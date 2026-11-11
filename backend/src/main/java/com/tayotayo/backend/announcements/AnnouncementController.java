package com.tayotayo.backend.announcements;

import com.tayotayo.backend.auth.AccessTokenStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1")
public class AnnouncementController {
    private final JdbcTemplate jdbc;private final AccessTokenStore tokens;
    public AnnouncementController(JdbcTemplate jdbc,AccessTokenStore tokens){this.jdbc=jdbc;this.tokens=tokens;}

    @GetMapping("/orgs/{orgId}/announcements")
    public Map<String,Object> orgList(@PathVariable("orgId")long org,@RequestHeader(name="Authorization",required=false)String auth,@RequestParam(name="limit",defaultValue="20")int limit,@RequestParam(name="cursor",required=false)String cursor){long user=tokens.requireUser(auth);staff(org,user);return page("SELECT id,org_id AS orgId,trip_id AS tripId,audience,vehicle_id AS vehicleId,category,title,body,created_at AS createdAt FROM org_announcements WHERE org_id=? AND id>? ORDER BY id DESC LIMIT ?",limit,cursor,org);}

    @PostMapping("/orgs/{orgId}/announcements") @Transactional
    public Map<String,Object> create(@PathVariable("orgId")long org,@RequestHeader(name="Authorization",required=false)String auth,@RequestBody Map<String,Object>b){long user=tokens.requireUser(auth),staffId=staff(org,user);String audience=text(b.get("audience"),"audience").toUpperCase(),category=text(b.get("category"),"category").toUpperCase(),title=text(b.get("title"),"title"),body=text(b.get("body"),"body");if(!List.of("ALL","VEHICLE","TRIP").contains(audience)||!List.of("DELAY","EMERGENCY","NOTICE").contains(category)||title.length()>100||body.length()>1000)bad("공지 입력 값이 올바르지 않습니다.");Object trip=b.get("tripId"),vehicle=b.get("vehicleId");jdbc.update("INSERT INTO org_announcements(org_id,trip_id,audience,vehicle_id,category,title,body,created_by_staff_id,sent_at) VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(3))",org,trip, audience,vehicle,category,title,body,staffId);Long id=jdbc.queryForObject("SELECT id FROM org_announcements WHERE org_id=? ORDER BY id DESC LIMIT 1",Long.class,org);
        jdbc.update("INSERT INTO notifications(user_id,announcement_id,notification_type,dedupe_key,title,body,status) SELECT DISTINCT cg.user_id,?, 'ANNOUNCEMENT', CONCAT('announcement:',?,':',cg.user_id), ?, ?, 'PENDING' FROM child_guardians cg JOIN children c ON c.id=cg.child_id WHERE c.org_id=? AND cg.status='ACTIVE' AND c.deleted_at IS NULL",id,id,title,body,org);
        return jdbc.queryForMap("SELECT id,org_id AS orgId,trip_id AS tripId,audience,vehicle_id AS vehicleId,category,title,body,created_at AS createdAt FROM org_announcements WHERE id=?",id);}

    @GetMapping("/guardian/announcements")
    public Map<String,Object> guardianAnnouncements(@RequestHeader(name="Authorization",required=false)String auth,@RequestParam(name="limit",defaultValue="20")int limit,@RequestParam(name="cursor",required=false)String cursor){long user=tokens.requireUser(auth);return page("SELECT DISTINCT a.id,a.org_id AS orgId,o.name AS orgName,a.category,a.title,a.body,a.created_at AS createdAt FROM org_announcements a JOIN organizations o ON o.id=a.org_id JOIN children c ON c.org_id=a.org_id JOIN child_guardians cg ON cg.child_id=c.id WHERE cg.user_id=? AND cg.status='ACTIVE' AND c.deleted_at IS NULL AND a.id>? ORDER BY a.id DESC LIMIT ?",limit,cursor,user);}

    @GetMapping("/guardian/notifications")
    public Map<String,Object> notifications(@RequestHeader(name="Authorization",required=false)String auth,@RequestParam(name="limit",defaultValue="20")int limit,@RequestParam(name="cursor",required=false)String cursor){long user=tokens.requireUser(auth);return page("SELECT id,child_id AS childId,trip_id AS tripId,announcement_id AS announcementId,notification_type AS notificationType,title,body,status,created_at AS createdAt,read_at AS readAt FROM notifications WHERE user_id=? AND id>? ORDER BY id DESC LIMIT ?",limit,cursor,user);}

    @PostMapping("/guardian/notifications/{notificationId}/read")
    public Map<String,Object> read(@PathVariable("notificationId")long id,@RequestHeader(name="Authorization",required=false)String auth){long user=tokens.requireUser(auth);int n=jdbc.update("UPDATE notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP(3)) WHERE id=? AND user_id=?",id,user);if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"알림을 찾을 수 없습니다.");return Map.of("id",id,"read",true);}

    private long staff(long org,long user){List<Map<String,Object>>r=jdbc.queryForList("SELECT id FROM org_staff WHERE org_id=? AND user_id=? AND status='ACTIVE' AND deleted_at IS NULL",org,user);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"기관 직원이 아닙니다.");return ((Number)r.get(0).get("id")).longValue();}
    private Map<String,Object> page(String sql,int limit,String cursor,Object...prefix){if(limit<1||limit>100)bad("limit은 1~100이어야 합니다.");long after=0;if(cursor!=null&&!cursor.isBlank())try{after=Long.parseLong(cursor);if(after<1)throw new NumberFormatException();}catch(Exception e){bad("cursor 값이 올바르지 않습니다.");}Object[]args=java.util.Arrays.copyOf(prefix,prefix.length+2);args[prefix.length]=after;args[prefix.length+1]=limit+1;List<Map<String,Object>>rows=jdbc.queryForList(sql,args);boolean more=rows.size()>limit;List<Map<String,Object>>items=rows.subList(0,Math.min(limit,rows.size()));Map<String,Object>out=new LinkedHashMap<>();out.put("items",items);out.put("nextCursor",more?String.valueOf(items.get(items.size()-1).get("id")):null);return out;}
    private static String text(Object o,String f){if(!(o instanceof String s)||s.isBlank())bad(f+" 값은 필수입니다.");return ((String)o).trim();}
    private static void bad(String s){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
}
