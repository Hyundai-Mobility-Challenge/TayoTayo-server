package com.tayotayo.backend.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final JdbcTemplate jdbc;
    private final AccessTokenStore tokens;
    public AccountController(JdbcTemplate jdbc, AccessTokenStore tokens) { this.jdbc=jdbc; this.tokens=tokens; }

    @GetMapping("/me")
    public Map<String,Object> me(@RequestHeader(name="Authorization",required=false) String authorization) {
        long userId=tokens.requireUser(authorization);
        List<Map<String,Object>> users=jdbc.queryForList("SELECT id,email,name,status,created_at AS createdAt FROM users WHERE id=? AND status='ACTIVE'",userId);
        if(users.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"계정을 찾을 수 없습니다.");
        return Map.of("user",users.get(0),"organizations",jdbc.queryForList("SELECT o.id,o.name,o.org_type AS orgType,s.staff_role AS staffRole,s.web_role AS webRole FROM org_staff s JOIN organizations o ON o.id=s.org_id WHERE s.user_id=? AND s.status='ACTIVE' AND s.deleted_at IS NULL AND o.deleted_at IS NULL",userId));
    }

    @PatchMapping("/me")
    public Map<String,Object> updateMe(@RequestHeader(name="Authorization",required=false) String authorization,@RequestBody Map<String,Object> body) {
        long userId=tokens.requireUser(authorization);
        if(body.containsKey("name")) {
            Object raw=body.get("name");
            if(!(raw instanceof String name)||name.isBlank()||name.trim().length()>50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"name은 1~50자여야 합니다.");
            jdbc.update("UPDATE users SET name=?,updated_at=CURRENT_TIMESTAMP(3) WHERE id=?",name.trim(),userId);
        }
        return me(authorization);
    }

    @PutMapping("/me/consents")
    @Transactional
    public Map<String,Object> saveConsents(@RequestHeader(name="Authorization",required=false) String authorization,@RequestBody Map<String,Object> body) {
        long userId=tokens.requireUser(authorization);
        Object raw=body.get("consents");
        if(!(raw instanceof List<?> list)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"consents 배열이 필요합니다.");
        List<Long> saved=new ArrayList<>();
        for(Object item:list) {
            if(!(item instanceof Map<?,?> c)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"consents 항목은 객체여야 합니다.");
            Object policy=c.get("policyDocumentId"), agreed=c.get("agreed");
            if(!(policy instanceof Number n)||!(agreed instanceof Boolean)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"policyDocumentId와 agreed가 필요합니다.");
            long policyId=n.longValue();
            Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM policy_documents WHERE id=? AND effective_at<=CURRENT_TIMESTAMP(3)",Integer.class,policyId);
            if(exists==null||exists==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"약관을 찾을 수 없습니다.");
            if(Boolean.TRUE.equals(agreed)) jdbc.update("INSERT INTO user_consents (user_id,policy_document_id) VALUES (?,?)",userId,policyId);
            else jdbc.update("UPDATE user_consents SET withdrawn_at=CURRENT_TIMESTAMP(3) WHERE user_id=? AND policy_document_id=? AND withdrawn_at IS NULL",userId,policyId);
            saved.add(policyId);
        }
        return Map.of("savedPolicyDocumentIds",saved);
    }
}
