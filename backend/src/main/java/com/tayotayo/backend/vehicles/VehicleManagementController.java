package com.tayotayo.backend.vehicles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import com.tayotayo.backend.auth.AccessTokenStore;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class VehicleManagementController {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final JdbcTemplate jdbc;
    private final AccessTokenStore tokens;
    public VehicleManagementController(JdbcTemplate jdbc,AccessTokenStore tokens){this.jdbc=jdbc;this.tokens=tokens;}

    @org.springframework.web.bind.annotation.GetMapping("/api/v1/orgs/{orgId}/vehicles/{vehicleId}")
    public Map<String,Object> get(@PathVariable("orgId") long orgId,@PathVariable("vehicleId") long vehicleId){return vehicle(orgId,vehicleId);}

    @PatchMapping("/api/v1/orgs/{orgId}/vehicles/{vehicleId}")
    public Map<String,Object> update(@PathVariable("orgId") long orgId,@PathVariable("vehicleId") long vehicleId,@RequestBody Map<String,Object> body){
        vehicle(orgId,vehicleId);
        updateText(body,"name","name",50,orgId,vehicleId); updateText(body,"plateNumber","plate_number",20,orgId,vehicleId); updateText(body,"model","model",50,orgId,vehicleId);
        if(body.containsKey("seatCapacity")){Object x=body.get("seatCapacity"); if(x!=null&&(!(x instanceof Number n)||n.intValue()<1||n.intValue()>32767)) bad("seatCapacity 값이 올바르지 않습니다."); jdbc.update("UPDATE vehicles SET seat_capacity=? WHERE org_id=? AND id=?",x,orgId,vehicleId);}
        if(body.containsKey("status")){String s=String.valueOf(body.get("status")).toUpperCase();if(!List.of("ACTIVE","INACTIVE").contains(s))bad("status 값이 올바르지 않습니다.");jdbc.update("UPDATE vehicles SET status=? WHERE org_id=? AND id=?",s,orgId,vehicleId);}
        jdbc.update("UPDATE vehicles SET updated_at=CURRENT_TIMESTAMP(3) WHERE org_id=? AND id=?",orgId,vehicleId);return vehicle(orgId,vehicleId);
    }

    @PostMapping("/api/v1/orgs/{orgId}/vehicles/{vehicleId}/pairing-codes")
    public Map<String,Object> issueCode(@PathVariable("orgId") long orgId,@PathVariable("vehicleId") long vehicleId,
            @RequestHeader(name="Authorization",required=false) String authorization,@RequestBody(required=false) Map<String,Object> body){
        // Require a valid account token; role-based organization permission is added with staff APIs.
        tokens.requireUser(authorization);
        vehicle(orgId,vehicleId); String code=String.format("%06d",RANDOM.nextInt(1_000_000));
        int ttl=body!=null&&body.get("expiresIn") instanceof Number n?Math.max(60,Math.min(1800,n.intValue())):600;
        jdbc.update("INSERT INTO vehicle_pairing_codes (org_id,vehicle_id,code_hash,expires_at) VALUES (?,?,?,?)",orgId,vehicleId,sha256(code),Timestamp.from(Instant.now().plus(ttl,ChronoUnit.SECONDS)));
        return Map.of("code",code,"expiresIn",ttl);
    }

    @PostMapping("/api/v1/vehicle/pair")
    @Transactional
    public Map<String,Object> pair(@RequestBody Map<String,Object> body){
        String code=text(body.get("code"),"code"), installId=text(body.get("installId"),"installId");
        var codes=jdbc.queryForList("SELECT id,org_id,vehicle_id FROM vehicle_pairing_codes WHERE code_hash=? AND used_at IS NULL AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP(3) ORDER BY id DESC LIMIT 1 FOR UPDATE",sha256(code));
        if(codes.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"페어링 코드가 없거나 만료되었습니다.");
        Map<String,Object> row=codes.get(0); long orgId=((Number)row.get("org_id")).longValue(),vehicleId=((Number)row.get("vehicle_id")).longValue();
        String credential=token(); KeyHolder kh=new GeneratedKeyHolder();
        jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO vehicle_devices (org_id,vehicle_id,install_id,credential_hash,app_version,pleos_sdk_version) VALUES (?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,orgId);ps.setLong(2,vehicleId);ps.setString(3,installId);ps.setString(4,sha256(credential));ps.setString(5,body.get("appVersion") instanceof String s?s:null);ps.setString(6,body.get("pleosSdkVersion") instanceof String s?s:null);return ps;},kh);
        jdbc.update("UPDATE vehicle_pairing_codes SET used_at=CURRENT_TIMESTAMP(3) WHERE id=?",row.get("id"));
        return Map.of("vehicleDeviceId",kh.getKey().longValue(),"orgId",orgId,"vehicleId",vehicleId,"vehicleCredential",credential);
    }

    @PostMapping("/api/v1/vehicle/session/revoke")
    public Map<String,Object> revoke(@RequestHeader(name="X-Vehicle-Credential",required=false) String credential){
        if(credential==null||credential.isBlank())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"차량 credential이 필요합니다.");
        int n=jdbc.update("UPDATE vehicle_devices SET revoked_at=CURRENT_TIMESTAMP(3) WHERE credential_hash=? AND revoked_at IS NULL",sha256(credential));
        if(n==0)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"차량 credential이 유효하지 않습니다.");return Map.of("status","REVOKED");
    }
    private Map<String,Object> vehicle(long org,long id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,org_id AS orgId,name,plate_number AS plateNumber,model,seat_capacity AS seatCapacity,status FROM vehicles WHERE org_id=? AND id=? AND deleted_at IS NULL",org,id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"차량을 찾을 수 없습니다.");return rows.get(0);}
    private void updateText(Map<String,Object>b,String k,String col,int max,long org,long id){if(!b.containsKey(k))return;Object v=b.get(k);if(v!=null&&(!(v instanceof String s)||s.length()>max))bad(k+" 값이 올바르지 않습니다.");jdbc.update("UPDATE vehicles SET "+col+"=? WHERE org_id=? AND id=?",v,org,id);}
    private static String text(Object v,String k){if(!(v instanceof String s)||s.isBlank())bad(k+" 값이 필요합니다.");return ((String)v).trim();}
    private static String token(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void bad(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
