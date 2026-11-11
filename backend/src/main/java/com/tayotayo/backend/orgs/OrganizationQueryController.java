package com.tayotayo.backend.orgs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.tayotayo.backend.auth.AccessTokenStore;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1")
public class OrganizationQueryController {
    private final JdbcTemplate jdbc;
    private final AccessTokenStore tokens;
    public OrganizationQueryController(JdbcTemplate jdbc, AccessTokenStore tokens) { this.jdbc = jdbc; this.tokens=tokens; }

    @GetMapping("/orgs")
    public Map<String,Object> organizations(@RequestHeader(name="Authorization",required=false) String auth,@RequestParam(name="limit",defaultValue="20") int limit,
            @RequestParam(name="cursor",required=false) String cursor) {
        long user=tokens.requireUser(auth);
        return page("SELECT o.id,o.name,o.org_type AS orgType,o.phone,o.address,o.latitude,o.longitude,o.timezone,o.status FROM organizations o JOIN org_staff s ON s.org_id=o.id WHERE s.user_id=? AND s.status='ACTIVE' AND s.deleted_at IS NULL AND o.deleted_at IS NULL AND o.id>? ORDER BY o.id LIMIT ?", limit, cursor,user);
    }

    @GetMapping("/orgs/{orgId}")
    public Map<String,Object> organization(@PathVariable("orgId") long orgId) {
        return one("SELECT id,name,org_type AS orgType,phone,address,latitude,longitude,timezone,status FROM organizations WHERE id=? AND deleted_at IS NULL", orgId);
    }

    @PatchMapping("/orgs/{orgId}")
    public Map<String,Object> updateOrganization(@PathVariable("orgId") long orgId,@RequestBody Map<String,Object> body) {
        organization(orgId);
        updateText(body,"name","name",100,orgId);
        updateText(body,"phone","phone",30,orgId);
        updateText(body,"address","address",255,orgId);
        updateText(body,"timezone","timezone",40,orgId);
        if(body.containsKey("latitude")) jdbc.update("UPDATE organizations SET latitude=? WHERE id=?", number(body.get("latitude"),-90,90,"latitude"),orgId);
        if(body.containsKey("longitude")) jdbc.update("UPDATE organizations SET longitude=? WHERE id=?", number(body.get("longitude"),-180,180,"longitude"),orgId);
        jdbc.update("UPDATE organizations SET updated_at=CURRENT_TIMESTAMP(3) WHERE id=?",orgId);
        return organization(orgId);
    }

    @GetMapping("/policies")
    public Map<String,Object> policies(@RequestParam(name="limit",defaultValue="20") int limit,
            @RequestParam(name="cursor",required=false) String cursor) {
        return page("SELECT id,policy_type AS policyType,version,title,content_url AS contentUrl,is_required AS isRequired,effective_at AS effectiveAt FROM policy_documents WHERE effective_at<=CURRENT_TIMESTAMP(3) AND id>? ORDER BY id LIMIT ?",limit,cursor);
    }

    private Map<String,Object> page(String sql,int limit,String cursor,Object... prefix) {
        if(limit<1||limit>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"limit은 1~100이어야 합니다.");
        long after=0;
        if(cursor!=null&&!cursor.isBlank()) try { after=Long.parseLong(cursor); if(after<1) throw new NumberFormatException(); }
        catch(NumberFormatException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"cursor는 양의 정수여야 합니다.");}
        Object[] args=java.util.Arrays.copyOf(prefix,prefix.length+2);args[prefix.length]=after;args[prefix.length+1]=limit+1;
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        boolean more=rows.size()>limit;
        List<Map<String,Object>> items=rows.subList(0,Math.min(limit,rows.size()));
        Map<String,Object> result=new LinkedHashMap<>(); result.put("items",items);
        result.put("nextCursor",more?String.valueOf(items.get(items.size()-1).get("id")):null); return result;
    }
    private Map<String,Object> one(String sql,Object... args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"기관을 찾을 수 없습니다.");
        return rows.get(0);
    }
    private void updateText(Map<String,Object> body,String key,String column,int max,long id) {
        if(!body.containsKey(key)) return;
        Object v=body.get(key);
        if(v!=null&&(!(v instanceof String)||((String)v).length()>max)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,key+" 값이 올바르지 않습니다.");
        jdbc.update("UPDATE organizations SET "+column+"=? WHERE id=?",v,id);
    }
    private Double number(Object raw,double min,double max,String key) {
        if(raw==null) return null;
        if(!(raw instanceof Number n)||n.doubleValue()<min||n.doubleValue()>max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,key+" 값 범위가 올바르지 않습니다.");
        return n.doubleValue();
    }
}
