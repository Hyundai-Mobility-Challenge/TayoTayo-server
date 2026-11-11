package com.tayotayo.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Enforces OpenAPI security schemes and organization/guardian resource boundaries. */
@Component
public class ApiAuthorizationInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final AccessTokenStore tokens; private final JdbcTemplate jdbc;
    public ApiAuthorizationInterceptor(AccessTokenStore tokens,JdbcTemplate jdbc){this.tokens=tokens;this.jdbc=jdbc;}
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(this).addPathPatterns("/api/v1/**");}
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
        String path=request.getRequestURI().substring("/api/v1".length());String method=request.getMethod();
        if(path.startsWith("/auth/")||path.equals("/policies")||path.equals("/vehicle/pair")||path.startsWith("/share/"))return true;
        if(path.equals("/orgs")&&"POST".equals(method))return true;
        if(path.equals("/vehicle/session/revoke")||path.startsWith("/vehicle/trips/")||path.equals("/vehicle/trips")||isTripDeviceOperation(path)){
            requireDevice(request);return true;
        }
        long user=tokens.requireUser(request.getHeader("Authorization"));
        if(path.matches("/trips/\\d+(/cancel|/route-revisions)?") && !isTripDeviceOperation(path)){
            long tripId=parseId(path.split("/")[2]);
            List<Long> orgs=jdbc.query("SELECT org_id FROM trips WHERE id=?",(rs,n)->rs.getLong(1),tripId);
            if(orgs.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"운행을 찾을 수 없습니다.");
            long org=orgs.get(0);
            boolean isMember=!jdbc.query("SELECT id FROM org_staff WHERE org_id=? AND user_id=? AND status='ACTIVE' AND deleted_at IS NULL",(rs,n)->rs.getLong(1),org,user).isEmpty();
            if(path.matches("/trips/\\d+(/cancel|/route-revisions)?") && !path.equals("/trips/"+tripId)){
                if(!isMember)deny("기관 직원만 운행을 변경할 수 있습니다.");
                List<Long> managers=jdbc.query("SELECT id FROM org_staff WHERE org_id=? AND user_id=? AND status='ACTIVE' AND deleted_at IS NULL AND (staff_role='ADMIN' OR web_role IN ('OWNER','MANAGER'))",(rs,n)->rs.getLong(1),org,user);
                if(managers.isEmpty())deny("기관 관리자 권한이 필요합니다.");
            }else if(!isMember){
                Integer guardian=jdbc.queryForObject("SELECT COUNT(*) FROM trip_children tc JOIN child_guardians cg ON cg.child_id=tc.child_id WHERE tc.trip_id=? AND cg.user_id=? AND cg.status='ACTIVE'",Integer.class,tripId,user);
                if(guardian==null||guardian==0)deny("해당 운행에 접근할 권한이 없습니다.");
            }
        }
        if(path.startsWith("/orgs/")){
            String[] parts=path.split("/");if(parts.length>2){long org=parseId(parts[2]);List<Integer> member=jdbc.query("SELECT 1 FROM org_staff WHERE org_id=? AND user_id=? AND status='ACTIVE' AND deleted_at IS NULL",(rs,n)->rs.getInt(1),org,user);if(member.isEmpty())deny("해당 기관의 직원만 접근할 수 있습니다.");if(isOrgWrite(method)){List<Integer> manager=jdbc.query("SELECT 1 FROM org_staff WHERE org_id=? AND user_id=? AND status='ACTIVE' AND deleted_at IS NULL AND (staff_role='ADMIN' OR web_role IN ('OWNER','MANAGER'))",(rs,n)->rs.getInt(1),org,user);if(manager.isEmpty())deny("기관 관리자 권한이 필요합니다.");}}
        }
        if(path.startsWith("/guardian/children/")){
            String[] parts=path.split("/");if(parts.length>3){long child=parseId(parts[3]);Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM child_guardians WHERE child_id=? AND user_id=? AND status='ACTIVE'",Integer.class,child,user);if(n==null||n==0)deny("해당 아이와 연결된 보호자만 접근할 수 있습니다.");}}
        return true;
    }
    private boolean isTripDeviceOperation(String p){return p.matches("/trips/\\d+/(prepare|checklist|start|stops/\\d+/(arrive|complete|skip)|return|interior-check|complete|events|events/batch)")||p.matches("/trips/\\d+/children/\\d+/guardian-calls");}
    private boolean isOrgWrite(String method){return List.of("POST","PUT","PATCH","DELETE").contains(method);}
    private void requireDevice(HttpServletRequest req){String credential=req.getHeader("X-Vehicle-Credential");if(credential==null||credential.isBlank())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"X-Vehicle-Credential 헤더가 필요합니다.");Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM vehicle_devices WHERE credential_hash=SHA2(?,256) AND revoked_at IS NULL",Integer.class,credential);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"차량 credential이 유효하지 않습니다.");}
    private long parseId(String x){try{return Long.parseLong(x);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"경로 ID가 올바르지 않습니다.");}}
    private void deny(String msg){throw new ResponseStatusException(HttpStatus.FORBIDDEN,msg);}
}
