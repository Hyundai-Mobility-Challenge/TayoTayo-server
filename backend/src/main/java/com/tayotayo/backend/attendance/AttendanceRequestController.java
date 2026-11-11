package com.tayotayo.backend.attendance;

import com.tayotayo.backend.auth.AccessTokenStore;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1")
public class AttendanceRequestController {
    private final JdbcTemplate jdbc; private final AccessTokenStore tokens;
    public AttendanceRequestController(JdbcTemplate jdbc,AccessTokenStore tokens){this.jdbc=jdbc;this.tokens=tokens;}

    @GetMapping("/guardian/children/{childId}/attendance-requests")
    public Map<String,Object> guardianList(@PathVariable("childId")long child,@RequestHeader(name="Authorization",required=false)String auth,@RequestParam(name="limit",defaultValue="20")int limit,@RequestParam(name="cursor",required=false)String cursor){
        long user=tokens.requireUser(auth);guardianOf(child,user);return page("SELECT id,child_id AS childId,service_date AS serviceDate,direction,request_type AS requestType,reason_code AS reasonCode,reason_text AS reasonText,status,review_note AS reviewNote,created_at AS createdAt FROM attendance_requests WHERE child_id=? AND requested_by_user_id=? AND id>? ORDER BY id DESC LIMIT ?",limit,cursor,child,user);
    }

    @PostMapping("/guardian/children/{childId}/attendance-requests") @Transactional
    public Map<String,Object> create(@PathVariable("childId")long child,@RequestHeader(name="Authorization",required=false)String auth,@RequestBody Map<String,Object>b){
        long user=tokens.requireUser(auth);Map<String,Object> childRow=one("SELECT org_id FROM children WHERE id=? AND deleted_at IS NULL",child);guardianOf(child,user);
        LocalDate date;try{date=LocalDate.parse(text(b.get("serviceDate"),"serviceDate"));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"serviceDate는 YYYY-MM-DD여야 합니다.");}
        String direction=text(b.get("direction"),"direction").toUpperCase(),type=text(b.get("requestType"),"requestType").toUpperCase();
        if(!List.of("DROP_OFF","PICK_UP").contains(direction)||!List.of("ABSENT","SELF_PICKUP","STOP_CHANGE","PICKUP_PERSON","NOT_RIDING").contains(type))bad("direction 또는 requestType 값이 올바르지 않습니다.");
        Object stop=optionalId(b.get("requestedStopId")),person=optionalId(b.get("pickupPersonId")),guardian=optionalId(b.get("pickupGuardianUserId"));
        if(stop!=null)exists("SELECT COUNT(*) FROM stops WHERE org_id=? AND id=? AND deleted_at IS NULL",childRow.get("org_id"),stop);
        if(person!=null)exists("SELECT COUNT(*) FROM authorized_pickup_persons WHERE child_id=? AND id=?",child,person);
        jdbc.update("INSERT INTO attendance_requests(org_id,child_id,requested_by_user_id,service_date,direction,request_type,reason_code,reason_text,requested_stop_id,pickup_person_id,pickup_guardian_user_id,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,'PENDING')",
                childRow.get("org_id"),child,user,java.sql.Date.valueOf(date),direction,type,b.get("reasonCode"),b.get("reasonText"),stop,person,guardian);
        return one("SELECT id,child_id AS childId,service_date AS serviceDate,direction,request_type AS requestType,status,created_at AS createdAt FROM attendance_requests WHERE id=LAST_INSERT_ID()");
    }

    @GetMapping("/orgs/{orgId}/attendance-requests")
    public Map<String,Object> orgList(@PathVariable("orgId")long org,@RequestHeader(name="Authorization",required=false)String auth,@RequestParam(name="limit",defaultValue="20")int limit,@RequestParam(name="cursor",required=false)String cursor){
        long user=tokens.requireUser(auth);requireStaff(org,user);return page("SELECT ar.id,ar.child_id AS childId,c.name AS childName,ar.requested_by_user_id AS requestedByUserId,u.name AS guardianName,ar.service_date AS serviceDate,ar.direction,ar.request_type AS requestType,ar.reason_code AS reasonCode,ar.reason_text AS reasonText,ar.status,ar.created_at AS createdAt FROM attendance_requests ar JOIN children c ON c.id=ar.child_id JOIN users u ON u.id=ar.requested_by_user_id WHERE ar.org_id=? AND ar.id>? ORDER BY ar.id DESC LIMIT ?",limit,cursor,org);
    }

    @PostMapping("/orgs/{orgId}/attendance-requests/{requestId}/review") @Transactional
    public Map<String,Object> review(@PathVariable("orgId")long org,@PathVariable("requestId")long id,@RequestHeader(name="Authorization",required=false)String auth,@RequestBody Map<String,Object>b){
        long user=tokens.requireUser(auth);long staff=requireStaff(org,user);String decision=text(b.get("decision"),"decision").toUpperCase();if(!List.of("APPROVED","REJECTED").contains(decision))bad("decision은 APPROVED 또는 REJECTED여야 합니다.");
        int n=jdbc.update("UPDATE attendance_requests SET status=?,reviewed_by_staff_id=?,reviewed_at=CURRENT_TIMESTAMP(3),review_note=?,updated_at=CURRENT_TIMESTAMP(3) WHERE org_id=? AND id=? AND status='PENDING'",decision,staff,b.get("reviewNote"),org,id);
        if(n==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"대기 중인 요청이 없거나 이미 처리됐습니다.");return one("SELECT id,status,review_note AS reviewNote,reviewed_at AS reviewedAt FROM attendance_requests WHERE org_id=? AND id=?",org,id);
    }

    @PostMapping("/guardian/attendance-requests/{requestId}/cancel")
    public Map<String,Object> cancel(@PathVariable("requestId")long id,@RequestHeader(name="Authorization",required=false)String auth){
        long user=tokens.requireUser(auth);int n=jdbc.update("UPDATE attendance_requests SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND requested_by_user_id=? AND status='PENDING'",id,user);if(n==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"취소할 수 있는 대기 요청이 없습니다.");return Map.of("id",id,"status","CANCELLED");
    }

    private Map<String,Object> page(String sql,int limit,String cursor,Object...prefix){if(limit<1||limit>100)bad("limit은 1~100이어야 합니다.");long after=parse(cursor);Object[] args=java.util.Arrays.copyOf(prefix,prefix.length+2);args[prefix.length]=after;args[prefix.length+1]=limit+1;List<Map<String,Object>>rows=jdbc.queryForList(sql,args);boolean more=rows.size()>limit;List<Map<String,Object>>items=rows.subList(0,Math.min(limit,rows.size()));Map<String,Object>out=new LinkedHashMap<>();out.put("items",items);out.put("nextCursor",more?String.valueOf(items.get(items.size()-1).get("id")):null);return out;}
    private void guardianOf(long child,long user){exists("SELECT COUNT(*) FROM child_guardians WHERE child_id=? AND user_id=? AND status='ACTIVE'",child,user);}
    private long requireStaff(long org,long user){List<Map<String,Object>>r=jdbc.queryForList("SELECT id FROM org_staff WHERE org_id=? AND user_id=? AND status='ACTIVE' AND deleted_at IS NULL",org,user);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"기관 소속 직원이 아닙니다.");return ((Number)r.get(0).get("id")).longValue();}
    private void exists(String q,Object...a){Integer n=jdbc.queryForObject(q,Integer.class,a);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"대상을 찾을 수 없습니다.");}
    private Map<String,Object> one(String q,Object...a){List<Map<String,Object>>r=jdbc.queryForList(q,a);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"대상을 찾을 수 없습니다.");return r.get(0);}
    private static String text(Object o,String f){if(!(o instanceof String s)||s.isBlank())bad(f+" 값이 필요합니다.");return ((String)o).trim();}
    private static Object optionalId(Object o){if(o==null)return null;try{long n=Long.parseLong(String.valueOf(o));if(n>0)return n;}catch(Exception ignored){}bad("ID 값이 올바르지 않습니다.");return null;}
    private static long parse(String c){if(c==null||c.isBlank())return 0;try{long n=Long.parseLong(c);if(n>0)return n;}catch(Exception ignored){}bad("cursor 값이 올바르지 않습니다.");return 0;}
    private static void bad(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
