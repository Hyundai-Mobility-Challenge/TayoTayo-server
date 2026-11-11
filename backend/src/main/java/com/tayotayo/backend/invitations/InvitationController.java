package com.tayotayo.backend.invitations;

import com.tayotayo.backend.auth.AccessTokenStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class InvitationController {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final JdbcTemplate jdbc; private final AccessTokenStore tokens;
    public InvitationController(JdbcTemplate jdbc,AccessTokenStore tokens){this.jdbc=jdbc;this.tokens=tokens;}

    @PostMapping("/orgs/{orgId}/staff-invitations")
    public Map<String,Object> inviteStaff(@PathVariable("orgId") long orgId,@RequestHeader(name="Authorization",required=false) String auth,@RequestBody Map<String,Object> body){
        tokens.requireUser(auth);String email=email(body.get("email"));String role=val(body.get("staffRole"),"staffRole").toUpperCase();String web=body.get("webRole")==null?null:String.valueOf(body.get("webRole")).toUpperCase();
        if(!List.of("ADMIN","TEACHER","DRIVER").contains(role)||web!=null&&!List.of("OWNER","MANAGER","VIEWER").contains(web))bad("직원 역할 값이 올바르지 않습니다.");
        ensureOrg(orgId);String code=code();jdbc.update("INSERT INTO staff_invitations(org_id,invitee_email,staff_role,web_role,code_hash,expires_at) VALUES(?,?,?,?,?,?)",orgId,email,role,web,sha(code),java.sql.Timestamp.from(Instant.now().plus(7,ChronoUnit.DAYS)));
        return Map.of("status","PENDING","email",email,"staffRole",role,"invitationCode",code,"expiresIn",604800);
    }

    @PostMapping("/staff-invitations/accept") @Transactional
    public Map<String,Object> acceptStaff(@RequestHeader(name="Authorization",required=false) String auth,@RequestBody Map<String,Object> body){
        long user=tokens.requireUser(auth);String code=val(body.get("code"),"code");Map<String,Object> invite=one("SELECT id,org_id,invitee_email,staff_role,web_role FROM staff_invitations WHERE code_hash=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP(3)",sha(code));
        Map<String,Object> account=one("SELECT email,name FROM users WHERE id=? AND status='ACTIVE'",user);
        if(!String.valueOf(account.get("email")).equalsIgnoreCase(String.valueOf(invite.get("invitee_email"))))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"초대받은 이메일 계정으로 로그인해야 합니다.");
        List<Map<String,Object>> existing=jdbc.queryForList("SELECT id FROM org_staff WHERE org_id=? AND user_id=? AND deleted_at IS NULL",invite.get("org_id"),user);
        Long staffId;
        if(existing.isEmpty()){jdbc.update("INSERT INTO org_staff(org_id,user_id,name,staff_role,web_role,status) VALUES(?,?,?,?,?,'ACTIVE')",invite.get("org_id"),user,account.get("name"),invite.get("staff_role"),invite.get("web_role"));staffId=jdbc.queryForObject("SELECT id FROM org_staff WHERE org_id=? AND user_id=? ORDER BY id DESC LIMIT 1",Long.class,invite.get("org_id"),user);}else staffId=((Number)existing.get(0).get("id")).longValue();
        jdbc.update("UPDATE staff_invitations SET status='ACCEPTED',accepted_at=CURRENT_TIMESTAMP(3),accepted_by_user_id=?,staff_id=? WHERE id=?",user,staffId,invite.get("id"));
        return Map.of("staffId",staffId,"orgId",invite.get("org_id"),"status","ACTIVE");
    }

    @PostMapping("/orgs/{orgId}/children/{childId}/guardian-invitations")
    public Map<String,Object> inviteGuardian(@PathVariable("orgId") long orgId,@PathVariable("childId") long childId,@RequestHeader(name="Authorization",required=false) String auth,@RequestBody Map<String,Object> body){
        tokens.requireUser(auth);exists("SELECT COUNT(*) FROM children WHERE org_id=? AND id=? AND deleted_at IS NULL",orgId,childId);String email=email(body.get("email"));String rel=body.get("relationship")==null?"OTHER":String.valueOf(body.get("relationship")).toUpperCase();
        if(!List.of("MOTHER","FATHER","GRANDPARENT","SIBLING","OTHER").contains(rel))bad("relationship 값이 올바르지 않습니다.");String code=code();
        jdbc.update("INSERT INTO guardian_invitations(org_id,child_id,invitee_email,relationship,code_hash,expires_at) VALUES(?,?,?,?,?,?)",orgId,childId,email,rel,sha(code),java.sql.Timestamp.from(Instant.now().plus(7,ChronoUnit.DAYS)));
        return Map.of("status","PENDING","email",email,"invitationCode",code,"expiresIn",604800);
    }

    @PostMapping("/guardian-invitations/accept") @Transactional
    public Map<String,Object> acceptGuardian(@RequestHeader(name="Authorization",required=false) String auth,@RequestBody Map<String,Object> body){
        long user=tokens.requireUser(auth);Map<String,Object> invite=one("SELECT id,org_id,child_id,invitee_email,relationship FROM guardian_invitations WHERE code_hash=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP(3)",sha(val(body.get("code"),"code")));
        String email=jdbc.queryForObject("SELECT email FROM users WHERE id=? AND status='ACTIVE'",String.class,user);if(!email.equalsIgnoreCase(String.valueOf(invite.get("invitee_email"))))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"초대받은 이메일 계정으로 로그인해야 합니다.");
        jdbc.update("INSERT INTO child_guardians(child_id,user_id,relationship,invitation_id,status) VALUES(?,?,?,?,'ACTIVE')",invite.get("child_id"),user,invite.get("relationship"),invite.get("id"));
        jdbc.update("UPDATE guardian_invitations SET status='ACCEPTED',accepted_at=CURRENT_TIMESTAMP(3),accepted_by_user_id=? WHERE id=?",user,invite.get("id"));
        return Map.of("childId",invite.get("child_id"),"userId",user,"relationship",invite.get("relationship"),"status","ACTIVE");
    }

    private void ensureOrg(long id){exists("SELECT COUNT(*) FROM organizations WHERE id=? AND deleted_at IS NULL",id);}
    private void exists(String q,Object...a){Integer n=jdbc.queryForObject(q,Integer.class,a);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"대상을 찾을 수 없습니다.");}
    private Map<String,Object> one(String q,Object...a){List<Map<String,Object>>r=jdbc.queryForList(q,a);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"초대를 찾을 수 없거나 만료되었습니다.");return r.get(0);}
    private static String email(Object o){String s=val(o,"email").toLowerCase();if(!s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))bad("이메일이 올바르지 않습니다.");return s;}
    private static String val(Object o,String k){if(!(o instanceof String s)||s.isBlank())bad(k+"은 필수입니다.");return ((String)o).trim();}
    private static String code(){byte[] b=new byte[24];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static String sha(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void bad(String s){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
}
