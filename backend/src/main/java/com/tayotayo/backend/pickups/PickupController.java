package com.tayotayo.backend.pickups;

import com.tayotayo.backend.auth.AccessTokenStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1")
public class PickupController {
    private final JdbcTemplate jdbc;private final AccessTokenStore tokens;
    public PickupController(JdbcTemplate jdbc,AccessTokenStore tokens){this.jdbc=jdbc;this.tokens=tokens;}

    @GetMapping("/guardian/children/{childId}/authorized-pickup-persons")
    public Map<String,Object> persons(@PathVariable("childId")long child,@RequestHeader(name="Authorization",required=false)String auth){long user=tokens.requireUser(auth);guardian(child,user);return Map.of("items",jdbc.queryForList("SELECT id,child_id AS childId,name,relationship,status,valid_from AS validFrom,valid_to AS validTo FROM authorized_pickup_persons WHERE child_id=? AND registered_by_user_id=? ORDER BY id DESC",child,user),"nextCursor","");}

    @PostMapping("/guardian/children/{childId}/authorized-pickup-persons")
    public Map<String,Object> addPerson(@PathVariable("childId")long child,@RequestHeader(name="Authorization",required=false)String auth,@RequestBody Map<String,Object>b){long user=tokens.requireUser(auth);Map<String,Object> c=guardian(child,user);String name=text(b.get("name"),"name"),relation=text(b.get("relationship"),"relationship");if(name.length()>50||relation.length()>30)bad("이름 또는 관계가 너무 깁니다.");Object from=dateOrNull(b.get("validFrom")),to=dateOrNull(b.get("validTo"));jdbc.update("INSERT INTO authorized_pickup_persons(org_id,child_id,name,relationship,registered_by_user_id,status,valid_from,valid_to) VALUES(?,?,?,?,?,'PENDING',?,?)",c.get("org_id"),child,name,relation,user,from,to);return jdbc.queryForMap("SELECT id,child_id AS childId,name,relationship,status,valid_from AS validFrom,valid_to AS validTo FROM authorized_pickup_persons WHERE id=LAST_INSERT_ID()");}

    @PostMapping("/orgs/{orgId}/authorized-pickup-persons/{personId}/review")
    public Map<String,Object> review(@PathVariable("orgId")long org,@PathVariable("personId")long id,@RequestHeader(name="Authorization",required=false)String auth,@RequestBody Map<String,Object>b){long user=tokens.requireUser(auth);Long staff=staff(org,user);String decision=text(b.get("decision"),"decision").toUpperCase();if(!List.of("APPROVED","REJECTED").contains(decision))bad("decision은 APPROVED 또는 REJECTED여야 합니다.");int n=jdbc.update("UPDATE authorized_pickup_persons SET status=?,reviewed_by_staff_id=?,reviewed_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE org_id=? AND id=? AND status='PENDING'",decision,staff,org,id);if(n==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"심사 대기 중인 인원을 찾지 못했습니다.");return jdbc.queryForMap("SELECT id,child_id AS childId,name,relationship,status,reviewed_at AS reviewedAt FROM authorized_pickup_persons WHERE org_id=? AND id=?",org,id);}

    @GetMapping("/guardian/children/{childId}/daily-pickups")
    public Map<String,Object> daily(@PathVariable("childId")long child,@RequestHeader(name="Authorization",required=false)String auth,@RequestParam("serviceDate")String date,@RequestParam("direction")String dir){long user=tokens.requireUser(auth);guardian(child,user);LocalDate d=parseDate(date);String direction=direction(dir);List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,child_id AS childId,service_date AS serviceDate,direction,pickup_person_id AS pickupPersonId,pickup_guardian_user_id AS pickupGuardianUserId,person_name_snapshot AS personName,relationship_snapshot AS relationship FROM daily_pickups WHERE child_id=? AND service_date=? AND direction=?",child,java.sql.Date.valueOf(d),direction);return rows.isEmpty()?Map.of("childId",child,"serviceDate",d.toString(),"direction",direction,"personType","GUARDIAN","pickupGuardianUserId",user):rows.get(0);}

    @PutMapping("/guardian/children/{childId}/daily-pickups") @Transactional
    public Map<String,Object> setDaily(@PathVariable("childId")long child,@RequestHeader(name="Authorization",required=false)String auth,@RequestBody Map<String,Object>b){long user=tokens.requireUser(auth);Map<String,Object>c=guardian(child,user);LocalDate date=parseDate(text(b.get("serviceDate"),"serviceDate"));String dir=direction(text(b.get("direction"),"direction"));String type=text(b.get("personType"),"personType").toUpperCase();Long personId=null,guardianId=null;String name,relation;
        if(type.equals("GUARDIAN")){guardianId=b.get("pickupGuardianUserId") instanceof Number n?n.longValue():user;Map<String,Object>g=guardian(child,guardianId);name=String.valueOf(g.get("name"));relation="GUARDIAN";}
        else if(type.equals("AUTHORIZED_PERSON")){personId=positive(b.get("pickupPersonId"));List<Map<String,Object>>p=jdbc.queryForList("SELECT name,relationship FROM authorized_pickup_persons WHERE id=? AND child_id=? AND registered_by_user_id=? AND status='APPROVED' AND (valid_from IS NULL OR valid_from<=?) AND (valid_to IS NULL OR valid_to>=?)",personId,child,user,java.sql.Date.valueOf(date),java.sql.Date.valueOf(date));if(p.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"승인되고 유효한 대리 픽업 인원만 지정할 수 있습니다.");name=String.valueOf(p.get(0).get("name"));relation=String.valueOf(p.get(0).get("relationship"));}
        else{bad("personType 값이 올바르지 않습니다.");return Map.of();}
        jdbc.update("INSERT INTO daily_pickups(org_id,child_id,service_date,direction,pickup_person_id,pickup_guardian_user_id,person_name_snapshot,relationship_snapshot,updated_by_user_id) VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE pickup_person_id=VALUES(pickup_person_id),pickup_guardian_user_id=VALUES(pickup_guardian_user_id),person_name_snapshot=VALUES(person_name_snapshot),relationship_snapshot=VALUES(relationship_snapshot),updated_by_user_id=VALUES(updated_by_user_id),updated_at=CURRENT_TIMESTAMP(3)",c.get("org_id"),child,java.sql.Date.valueOf(date),dir,personId,guardianId,name,relation,user);
        return jdbc.queryForMap("SELECT child_id AS childId,service_date AS serviceDate,direction,pickup_person_id AS pickupPersonId,pickup_guardian_user_id AS pickupGuardianUserId,person_name_snapshot AS personName,relationship_snapshot AS relationship FROM daily_pickups WHERE child_id=? AND service_date=? AND direction=?",child,java.sql.Date.valueOf(date),dir);}

    private Map<String,Object> guardian(long child,long user){List<Map<String,Object>>r=jdbc.queryForList("SELECT c.org_id,u.name FROM children c JOIN child_guardians cg ON cg.child_id=c.id JOIN users u ON u.id=cg.user_id WHERE c.id=? AND u.id=? AND cg.status='ACTIVE' AND c.deleted_at IS NULL",child,user);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"연결된 아이의 보호자만 이용할 수 있습니다.");return r.get(0);}
    private Long staff(long org,long user){List<Map<String,Object>>r=jdbc.queryForList("SELECT id FROM org_staff WHERE org_id=? AND user_id=? AND status='ACTIVE' AND deleted_at IS NULL",org,user);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"기관 직원이 아닙니다.");return ((Number)r.get(0).get("id")).longValue();}
    private static String text(Object v,String k){if(!(v instanceof String s)||s.isBlank())bad(k+" 값이 필요합니다.");return ((String)v).trim();}
    private static String direction(String s){String d=s.toUpperCase();if(!List.of("DROP_OFF","PICK_UP").contains(d))bad("direction은 DROP_OFF 또는 PICK_UP이어야 합니다.");return d;}
    private static LocalDate parseDate(String s){try{return LocalDate.parse(s);}catch(Exception e){bad("날짜는 YYYY-MM-DD 형식이어야 합니다.");return null;}}
    private static Object dateOrNull(Object v){return v==null?null:java.sql.Date.valueOf(parseDate(String.valueOf(v)));}
    private static Long positive(Object v){try{long x=Long.parseLong(String.valueOf(v));if(x>0)return x;}catch(Exception ignored){}bad("pickupPersonId 값이 필요합니다.");return null;}
    private static void bad(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
