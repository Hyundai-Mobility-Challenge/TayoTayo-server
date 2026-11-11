package com.tayotayo.backend.sharing;

import com.tayotayo.backend.auth.AccessTokenStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1")
public class ShareAndCallController {
    private static final SecureRandom RANDOM=new SecureRandom();private final JdbcTemplate jdbc;private final AccessTokenStore tokens;
    public ShareAndCallController(JdbcTemplate jdbc,AccessTokenStore tokens){this.jdbc=jdbc;this.tokens=tokens;}

    @PostMapping("/guardian/children/{childId}/trips/{tripId}/share-links")
    public Map<String,Object> createShare(@PathVariable("childId")long child,@PathVariable("tripId")long trip,@RequestHeader(name="Authorization",required=false)String auth,@RequestBody(required=false)Map<String,Object>b){long user=tokens.requireUser(auth);guardian(child,user);exists("SELECT COUNT(*) FROM trip_children WHERE trip_id=? AND child_id=?",trip,child);int ttl=b!=null&&b.get("expiresIn") instanceof Number n?Math.max(300,Math.min(86400,n.intValue())):3600;String token=randomToken();jdbc.update("INSERT INTO share_links(token_hash,child_id,trip_id,created_by_user_id,expires_at) VALUES(?,?,?,?,?)",sha(token),child,trip,user,Timestamp.from(Instant.now().plus(ttl,ChronoUnit.SECONDS)));Long id=jdbc.queryForObject("SELECT id FROM share_links WHERE token_hash=?",Long.class,sha(token));return Map.of("id",id,"token",token,"url","/api/v1/share/"+token,"expiresIn",ttl);}

    @GetMapping("/share/{token}")
    public Map<String,Object> getShare(@PathVariable("token")String token){List<Map<String,Object>>r=jdbc.queryForList("SELECT sl.id,sl.child_id AS childId,sl.trip_id AS tripId,tc.child_name AS childName,tc.status AS childStatus,t.direction,t.status AS tripStatus,t.service_date AS serviceDate,t.last_latitude AS latitude,t.last_longitude AS longitude,t.last_location_at AS locationUpdatedAt,ts.stop_name AS stopName,ts.eta_remaining_sec AS etaRemainingSec FROM share_links sl JOIN trip_children tc ON tc.trip_id=sl.trip_id AND tc.child_id=sl.child_id JOIN trips t ON t.id=sl.trip_id LEFT JOIN trip_stops ts ON ts.id=tc.trip_stop_id WHERE sl.token_hash=? AND sl.revoked_at IS NULL AND sl.expires_at>CURRENT_TIMESTAMP(3)",sha(token));if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"공유 링크가 만료되었거나 폐기되었습니다.");jdbc.update("UPDATE share_links SET access_count=access_count+1,last_accessed_at=CURRENT_TIMESTAMP(3) WHERE id=?",r.get(0).get("id"));return r.get(0);}

    @PostMapping("/guardian/share-links/{shareLinkId}/revoke")
    public Map<String,Object> revokeShare(@PathVariable("shareLinkId")long id,@RequestHeader(name="Authorization",required=false)String auth){long user=tokens.requireUser(auth);int n=jdbc.update("UPDATE share_links SET revoked_at=CURRENT_TIMESTAMP(3),revoke_reason='MANUAL' WHERE id=? AND created_by_user_id=? AND revoked_at IS NULL",id,user);if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"공유 링크를 찾을 수 없습니다.");return Map.of("id",id,"status","REVOKED");}

    @PostMapping("/trips/{tripId}/children/{tripChildId}/guardian-calls") @Transactional
    public Map<String,Object> openCall(@PathVariable("tripId")long trip,@PathVariable("tripChildId")long tripChild,@RequestHeader(name="X-Vehicle-Credential",required=false)String credential,@RequestBody Map<String,Object>b){deviceTrip(trip,credential);if(!"CHILD_NOT_AT_STOP".equals(b.get("callType")))bad("callType은 CHILD_NOT_AT_STOP이어야 합니다.");Map<String,Object>tc=one("SELECT child_id FROM trip_children WHERE trip_id=? AND id=?",trip,tripChild);List<Map<String,Object>>open=jdbc.queryForList("SELECT id,status FROM guardian_call_requests WHERE trip_id=? AND trip_child_id=? AND status='OPEN' AND expires_at>CURRENT_TIMESTAMP(3) ORDER BY id DESC LIMIT 1",trip,tripChild);if(!open.isEmpty())return Map.of("id",open.get(0).get("id"),"status","OPEN");jdbc.update("INSERT INTO guardian_call_requests(trip_id,trip_child_id,status,expires_at) VALUES(?,?,'OPEN',DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 10 MINUTE))",trip,tripChild);Long id=jdbc.queryForObject("SELECT id FROM guardian_call_requests WHERE trip_id=? AND trip_child_id=? ORDER BY id DESC LIMIT 1",Long.class,trip,tripChild);jdbc.update("INSERT INTO notifications(user_id,child_id,trip_id,notification_type,dedupe_key,title,body,status) SELECT cg.user_id,?,?,'GUARDIAN_NO_SHOW',CONCAT('guardian-call:',?,':',cg.user_id),'셔틀 탑승 확인','아이가 정류장에 보이지 않아요. 현재 상황을 알려주세요.','PENDING' FROM child_guardians cg WHERE cg.child_id=? AND cg.status='ACTIVE'",tc.get("child_id"),trip,id,tc.get("child_id"));return Map.of("id",id,"tripChildId",tripChild,"status","OPEN","expiresIn",600);}

    @PostMapping("/guardian/calls/{callId}/response")
    public Map<String,Object> respond(@PathVariable("callId")long id,@RequestHeader(name="Authorization",required=false)String auth,@RequestBody Map<String,Object>b){long user=tokens.requireUser(auth);String response=text(b.get("response"),"response").toUpperCase();if(!List.of("ON_THE_WAY","NOT_RIDING").contains(response))bad("response 값이 올바르지 않습니다.");Map<String,Object>call=one("SELECT g.trip_child_id AS tripChildId,tc.child_id AS childId FROM guardian_call_requests g JOIN trip_children tc ON tc.id=g.trip_child_id WHERE g.id=? AND g.status IN ('OPEN','RESPONDED') AND g.expires_at>CURRENT_TIMESTAMP(3)",id);guardian(((Number)call.get("childId")).longValue(),user);jdbc.update("UPDATE guardian_call_requests SET response=?,status='RESPONDED',responded_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE id=?",response,id);return Map.of("id",id,"response",response,"status","RESPONDED");}

    private long deviceTrip(long trip,String credential){if(credential==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"차량 credential이 필요합니다.");List<Map<String,Object>>r=jdbc.queryForList("SELECT id FROM vehicle_devices WHERE credential_hash=SHA2(?,256) AND revoked_at IS NULL",credential);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"차량 credential이 유효하지 않습니다.");long d=((Number)r.get(0).get("id")).longValue();exists("SELECT COUNT(*) FROM trips WHERE id=? AND vehicle_device_id=?",trip,d);return d;}
    private void guardian(long child,long user){exists("SELECT COUNT(*) FROM child_guardians WHERE child_id=? AND user_id=? AND status='ACTIVE'",child,user);}
    private void exists(String q,Object...a){Integer n=jdbc.queryForObject(q,Integer.class,a);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"대상을 찾을 수 없습니다.");}
    private Map<String,Object> one(String q,Object...a){List<Map<String,Object>>r=jdbc.queryForList(q,a);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"대상을 찾을 수 없습니다.");return r.get(0);}
    private static String text(Object o,String k){if(!(o instanceof String s)||s.isBlank())bad(k+" 값은 필수입니다.");return ((String)o).trim();}
    private static String randomToken(){byte[]b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static String sha(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void bad(String s){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
}
