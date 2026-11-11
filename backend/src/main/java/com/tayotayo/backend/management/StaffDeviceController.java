package com.tayotayo.backend.management;

import com.tayotayo.backend.auth.AccessTokenStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class StaffDeviceController {
    private final JdbcTemplate jdbc; private final AccessTokenStore tokens;
    public StaffDeviceController(JdbcTemplate jdbc,AccessTokenStore tokens){this.jdbc=jdbc;this.tokens=tokens;}

    @GetMapping("/orgs/{orgId}/staff")
    public Map<String,Object> staff(@PathVariable("orgId") long orgId,@RequestHeader(name="Authorization",required=false) String auth,
            @RequestParam(name="limit",defaultValue="20") int limit,@RequestParam(name="cursor",required=false) String cursor){
        tokens.requireUser(auth);long after=parse(cursor);if(limit<1||limit>100)bad("limit은 1~100이어야 합니다.");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,org_id AS orgId,user_id AS userId,name,staff_role AS staffRole,web_role AS webRole,status,created_at AS createdAt FROM org_staff WHERE org_id=? AND deleted_at IS NULL AND id>? ORDER BY id LIMIT ?",orgId,after,limit+1);
        boolean more=rows.size()>limit;List<Map<String,Object>> items=rows.subList(0,Math.min(limit,rows.size()));Map<String,Object> out=new LinkedHashMap<>();out.put("items",items);out.put("nextCursor",more?String.valueOf(items.get(items.size()-1).get("id")):null);return out;
    }

    @org.springframework.web.bind.annotation.PatchMapping("/orgs/{orgId}/staff/{staffId}")
    public Map<String,Object> updateStaff(@PathVariable("orgId") long orgId,@PathVariable("staffId") long staffId,
            @RequestHeader(name="Authorization",required=false) String auth,@RequestBody Map<String,Object> body){
        tokens.requireUser(auth); exists("SELECT COUNT(*) FROM org_staff WHERE org_id=? AND id=? AND deleted_at IS NULL",orgId,staffId);
        if(body.containsKey("staffRole")){String role=String.valueOf(body.get("staffRole")).toUpperCase();if(!List.of("ADMIN","TEACHER","DRIVER").contains(role))bad("staffRole 값이 올바르지 않습니다.");jdbc.update("UPDATE org_staff SET staff_role=? WHERE org_id=? AND id=?",role,orgId,staffId);}
        if(body.containsKey("webRole")){Object v=body.get("webRole");if(v!=null&&!List.of("OWNER","MANAGER","VIEWER").contains(String.valueOf(v).toUpperCase()))bad("webRole 값이 올바르지 않습니다.");jdbc.update("UPDATE org_staff SET web_role=? WHERE org_id=? AND id=?",v==null?null:String.valueOf(v).toUpperCase(),orgId,staffId);}
        if(body.containsKey("status")){String status=String.valueOf(body.get("status")).toUpperCase();if(!List.of("ACTIVE","INACTIVE").contains(status))bad("status 값이 올바르지 않습니다.");jdbc.update("UPDATE org_staff SET status=? WHERE org_id=? AND id=?",status,orgId,staffId);}
        return jdbc.queryForMap("SELECT id,org_id AS orgId,user_id AS userId,name,staff_role AS staffRole,web_role AS webRole,status FROM org_staff WHERE org_id=? AND id=?",orgId,staffId);
    }

    @PostMapping("/me/push-devices")
    public Map<String,Object> registerPush(@RequestHeader(name="Authorization",required=false) String auth,@RequestBody Map<String,Object> body){
        long user=tokens.requireUser(auth);String platform=text(body.get("platform"),"platform").toUpperCase();String fcm=text(body.get("fcmToken"),"fcmToken");String permission=body.get("osPermission")==null?"UNKNOWN":String.valueOf(body.get("osPermission")).toUpperCase();
        if(!List.of("ANDROID","IOS","WEB").contains(platform)||!List.of("GRANTED","DENIED","UNKNOWN").contains(permission)||fcm.length()>512)bad("기기 등록 값이 올바르지 않습니다.");
        List<Map<String,Object>> old=jdbc.queryForList("SELECT id FROM push_devices WHERE user_id=? AND fcm_token=?",user,fcm);
        if(old.isEmpty())jdbc.update("INSERT INTO push_devices(user_id,platform,fcm_token,app_version,os_permission) VALUES(?,?,?,?,?)",user,platform,fcm,body.get("appVersion"),permission);
        else jdbc.update("UPDATE push_devices SET platform=?,app_version=?,os_permission=?,revoked_at=NULL,last_refreshed_at=CURRENT_TIMESTAMP(3) WHERE id=?",platform,body.get("appVersion"),permission,old.get(0).get("id"));
        return jdbc.queryForMap("SELECT id,platform,app_version AS appVersion,os_permission AS osPermission,registered_at AS registeredAt FROM push_devices WHERE user_id=? AND fcm_token=?",user,fcm);
    }

    @DeleteMapping("/me/push-devices/{deviceId}")
    public Map<String,Object> revokePush(@RequestHeader(name="Authorization",required=false) String auth,@PathVariable("deviceId") long id){
        long user=tokens.requireUser(auth);int n=jdbc.update("UPDATE push_devices SET revoked_at=CURRENT_TIMESTAMP(3),revoke_reason='USER_REQUEST' WHERE id=? AND user_id=? AND revoked_at IS NULL",id,user);if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"활성 푸시 기기를 찾을 수 없습니다.");return Map.of("id",id,"status","REVOKED");
    }
    private void exists(String sql,Object...args){Integer n=jdbc.queryForObject(sql,Integer.class,args);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"대상을 찾을 수 없습니다.");}
    private static long parse(String c){if(c==null||c.isBlank())return 0;try{long n=Long.parseLong(c);if(n>0)return n;}catch(Exception ignored){}bad("cursor 값이 올바르지 않습니다.");return 0;}
    private static String text(Object v,String k){if(!(v instanceof String s)||s.isBlank())bad(k+"은 필수입니다.");return ((String)v).trim();}
    private static void bad(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
