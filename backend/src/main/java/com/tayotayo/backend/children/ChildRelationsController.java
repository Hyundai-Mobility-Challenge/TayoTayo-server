package com.tayotayo.backend.children;

import com.tayotayo.backend.auth.AccessTokenStore;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1")
public class ChildRelationsController {
    private final JdbcTemplate jdbc; private final AccessTokenStore tokens;
    public ChildRelationsController(JdbcTemplate jdbc,AccessTokenStore tokens){this.jdbc=jdbc;this.tokens=tokens;}

    @GetMapping("/orgs/{orgId}/children/{childId}/route-assignments")
    public Map<String,Object> history(@PathVariable("orgId")long org,@PathVariable("childId")long child,@RequestHeader(name="Authorization",required=false)String auth,@RequestParam(name="limit",defaultValue="20")int limit,@RequestParam(name="cursor",required=false)String cursor){
        tokens.requireUser(auth);exists("SELECT COUNT(*) FROM children WHERE org_id=? AND id=? AND deleted_at IS NULL",org,child);long after=cursor(cursor);if(limit<1||limit>100)bad("limit은 1~100이어야 합니다.");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT a.id,a.child_id AS childId,a.route_id AS routeId,r.name AS routeName,r.direction,a.vehicle_id AS vehicleId,v.name AS vehicleName,a.stop_id AS stopId,s.name AS stopName,a.weekdays,a.effective_from AS effectiveFrom,a.effective_to AS effectiveTo FROM child_route_assignments a JOIN routes r ON r.id=a.route_id JOIN vehicles v ON v.id=a.vehicle_id JOIN stops s ON s.id=a.stop_id WHERE a.org_id=? AND a.child_id=? AND a.id>? ORDER BY a.id DESC LIMIT ?",org,child,after,limit+1);
        return page(rows,limit);
    }

    @PutMapping("/orgs/{orgId}/children/{childId}/route-assignments") @Transactional
    public Map<String,Object> assign(@PathVariable("orgId")long org,@PathVariable("childId")long child,@RequestHeader(name="Authorization",required=false)String auth,@RequestBody Map<String,Object> b){
        tokens.requireUser(auth);exists("SELECT COUNT(*) FROM children WHERE org_id=? AND id=? AND deleted_at IS NULL",org,child);
        long route=id(b.get("routeId"),"routeId"),vehicle=id(b.get("vehicleId"),"vehicleId"),stop=id(b.get("stopId"),"stopId");
        exists("SELECT COUNT(*) FROM routes WHERE org_id=? AND id=? AND vehicle_id=? AND deleted_at IS NULL",org,route,vehicle);exists("SELECT COUNT(*) FROM stops WHERE org_id=? AND id=? AND deleted_at IS NULL",org,stop);
        int weekdays=b.get("weekdays") instanceof Number n?n.intValue():31;if(weekdays<1||weekdays>127)bad("weekdays는 1~127이어야 합니다.");
        LocalDate from=date(b.get("effectiveFrom"),LocalDate.now()),to=date(b.get("effectiveTo"),null);if(to!=null&&to.isBefore(from))bad("effectiveTo는 effectiveFrom보다 빠를 수 없습니다.");
        jdbc.update("UPDATE child_route_assignments SET effective_to=DATE_SUB(?,INTERVAL 1 DAY),updated_at=CURRENT_TIMESTAMP(3) WHERE org_id=? AND child_id=? AND effective_to IS NULL AND effective_from<?",java.sql.Date.valueOf(from),org,child,java.sql.Date.valueOf(from));
        jdbc.update("INSERT INTO child_route_assignments(org_id,child_id,route_id,vehicle_id,stop_id,weekdays,effective_from,effective_to) VALUES(?,?,?,?,?,?,?,?)",org,child,route,vehicle,stop,weekdays,java.sql.Date.valueOf(from),to==null?null:java.sql.Date.valueOf(to));
        return Map.of("childId",child,"routeId",route,"vehicleId",vehicle,"stopId",stop,"weekdays",weekdays,"effectiveFrom",from.toString(),"effectiveTo",to==null?"":to.toString());
    }

    @DeleteMapping("/orgs/{orgId}/children/{childId}/guardians/{userId}")
    public Map<String,Object> unlink(@PathVariable("orgId")long org,@PathVariable("childId")long child,@PathVariable("userId")long user,@RequestHeader(name="Authorization",required=false)String auth){
        tokens.requireUser(auth);int n=jdbc.update("UPDATE child_guardians cg JOIN children c ON c.id=cg.child_id SET cg.status='REVOKED',cg.revoked_at=CURRENT_TIMESTAMP(3) WHERE c.org_id=? AND cg.child_id=? AND cg.user_id=? AND cg.status='ACTIVE'",org,child,user);
        if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"연결된 보호자를 찾을 수 없습니다.");return Map.of("childId",child,"userId",user,"status","REVOKED");
    }

    @GetMapping("/me/children")
    public Map<String,Object> myChildren(@RequestHeader(name="Authorization",required=false)String auth,@RequestParam(name="limit",defaultValue="20")int limit,@RequestParam(name="cursor",required=false)String cursor){
        long user=tokens.requireUser(auth);if(limit<1||limit>100)bad("limit은 1~100이어야 합니다.");long after=cursor(cursor);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT c.id,c.org_id AS orgId,o.name AS orgName,c.name,c.class_name AS className,cg.relationship,cg.is_primary AS isPrimary FROM children c JOIN organizations o ON o.id=c.org_id JOIN child_guardians cg ON cg.id=(SELECT MAX(cg2.id) FROM child_guardians cg2 WHERE cg2.child_id=c.id AND cg2.user_id=? AND cg2.status='ACTIVE') WHERE c.deleted_at IS NULL AND c.id>? AND cg.id IS NOT NULL ORDER BY c.id LIMIT ?",user,after,limit+1);return page(rows,limit);
    }

    private Map<String,Object> page(List<Map<String,Object>> rows,int size){boolean more=rows.size()>size;List<Map<String,Object>> items=rows.subList(0,Math.min(size,rows.size()));Map<String,Object> out=new LinkedHashMap<>();out.put("items",items);out.put("nextCursor",more?String.valueOf(items.get(items.size()-1).get("id")):null);return out;}
    private void exists(String q,Object...args){Integer n=jdbc.queryForObject(q,Integer.class,args);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"대상을 찾을 수 없습니다.");}
    private static long id(Object v,String key){try{long n=Long.parseLong(String.valueOf(v));if(n>0)return n;}catch(Exception ignored){}bad(key+"는 양의 정수여야 합니다.");return 0;}
    private static long cursor(String v){return v==null||v.isBlank()?0:id(v,"cursor");}
    private static LocalDate date(Object v,LocalDate fallback){if(v==null)return fallback;try{return LocalDate.parse(String.valueOf(v));}catch(Exception e){bad("날짜는 YYYY-MM-DD 형식이어야 합니다.");return null;}}
    private static void bad(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
