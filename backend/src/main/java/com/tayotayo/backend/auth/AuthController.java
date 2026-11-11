package com.tayotayo.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Local account API. debugCode is returned only because this project has no mail provider yet. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long ACCESS_SECONDS = 3600;
    private final JdbcTemplate jdbc;
    private final AccessTokenStore accessTokens;

    public AuthController(JdbcTemplate jdbc, AccessTokenStore accessTokens) { this.jdbc = jdbc; this.accessTokens = accessTokens; }

    @PostMapping("/email-verifications")
    public Map<String, Object> requestVerification(@RequestBody Map<String, Object> body) {
        String email = email(body.get("email"));
        String purpose = text(body.get("purpose"), "purpose").toUpperCase();
        if (!List.of("SIGN_UP", "EMAIL_CHANGE", "PASSWORD_RESET").contains(purpose)) bad("purpose 값이 올바르지 않습니다.");
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        jdbc.update("INSERT INTO email_verifications (email, purpose, code_hash, expires_at) VALUES (?, ?, ?, ?)",
                email, purpose, sha256(code), Timestamp.from(Instant.now().plus(10, ChronoUnit.MINUTES)));
        return Map.of("status", "SENT", "expiresIn", 600, "debugCode", code);
    }

    @PostMapping("/email-verifications/confirm")
    @Transactional
    public Map<String, Object> confirm(@RequestBody Map<String, Object> body) {
        String email = email(body.get("email"));
        String purpose = text(body.get("purpose"), "purpose").toUpperCase();
        var rows = jdbc.queryForList("SELECT id FROM email_verifications WHERE email=? AND purpose=? AND code_hash=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP(3) ORDER BY id DESC LIMIT 1",
                email, purpose, sha256(text(body.get("code"), "code")));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증 코드가 틀렸거나 만료되었습니다.");
        // Keep sign-up codes available for the following /signup request; signup consumes them atomically.
        if (!"SIGN_UP".equals(purpose)) {
            jdbc.update("UPDATE email_verifications SET consumed_at=CURRENT_TIMESTAMP(3) WHERE id=?", rows.get(0).get("id"));
        }
        return Map.of("verified", true, "email", email, "purpose", purpose);
    }

    @PostMapping("/signup")
    @Transactional
    public Map<String, Object> signup(@RequestBody Map<String, Object> body) {
        String email = email(body.get("email")), name = text(body.get("name"), "name"), password = text(body.get("password"), "password");
        if (name.length() > 50 || password.length() < 8 || password.length() > 100) bad("이름은 50자 이하, 비밀번호는 8~100자여야 합니다.");
        var verified = jdbc.queryForList("SELECT id FROM email_verifications WHERE email=? AND purpose='SIGN_UP' AND code_hash=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP(3) ORDER BY id DESC LIMIT 1",
                email, sha256(text(body.get("verificationCode"), "verificationCode")));
        if (verified.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 이메일 인증이 필요합니다.");
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE email=?)", Boolean.class, email)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        jdbc.update("INSERT INTO users (email,password_hash,name,email_verified_at,status) VALUES (?,?,?,CURRENT_TIMESTAMP(3),'ACTIVE')", email, hashPassword(password), name);
        jdbc.update("UPDATE email_verifications SET consumed_at=CURRENT_TIMESTAMP(3) WHERE id=?", verified.get(0).get("id"));
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        return Map.of("id", id, "email", email, "name", name, "status", "ACTIVE");
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String email = email(body.get("email")), password = text(body.get("password"), "password");
        String client = text(body.get("clientType"), "clientType").toUpperCase();
        if (!List.of("GUARDIAN_APP", "ADMIN_WEB", "TEACHER_APP").contains(client)) bad("clientType 값이 올바르지 않습니다.");
        var rows = jdbc.queryForList("SELECT id,email,password_hash,name,status FROM users WHERE email=?", email);
        if (rows.isEmpty() || !"ACTIVE".equals(rows.get(0).get("status")) || !passwordMatches(password, String.valueOf(rows.get(0).get("password_hash"))))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        Map<String,Object> user = rows.get(0);
        String access = accessTokens.issue(((Number) user.get("id")).longValue()), refresh = token();
        jdbc.update("INSERT INTO auth_sessions (user_id,client_type,refresh_token_hash,device_label,expires_at) VALUES (?,?,?,?,?)",
                user.get("id"), client, sha256(refresh), body.get("deviceLabel"), Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS)));
        jdbc.update("UPDATE users SET last_login_at=CURRENT_TIMESTAMP(3) WHERE id=?", user.get("id"));
        return Map.of("accessToken", access, "refreshToken", refresh, "expiresIn", ACCESS_SECONDS,
                "user", Map.of("id", user.get("id"), "email", email, "name", user.get("name")));
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(@RequestBody Map<String, Object> body) {
        String refresh = text(body.get("refreshToken"), "refreshToken");
        var rows = jdbc.queryForList("SELECT id,user_id FROM auth_sessions WHERE refresh_token_hash=? AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP(3)", sha256(refresh));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh token입니다.");
        String replacement = token();
        jdbc.update("UPDATE auth_sessions SET refresh_token_hash=?,last_used_at=CURRENT_TIMESTAMP(3) WHERE id=?", sha256(replacement), rows.get(0).get("id"));
        return Map.of("accessToken", accessTokens.issue(((Number) rows.get(0).get("user_id")).longValue()), "refreshToken", replacement, "expiresIn", ACCESS_SECONDS, "user", Map.of("id", rows.get(0).get("user_id")));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestBody(required=false) Map<String, Object> body) {
        if (body != null && body.get("refreshToken") instanceof String value && !value.isBlank())
            jdbc.update("UPDATE auth_sessions SET revoked_at=CURRENT_TIMESTAMP(3) WHERE refresh_token_hash=? AND revoked_at IS NULL", sha256(value));
        return Map.of("status", "LOGGED_OUT");
    }

    private static String email(Object value) {
        String e = text(value, "email").toLowerCase();
        if (e.length()>255 || !e.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) bad("올바른 이메일 주소를 입력하세요.");
        return e;
    }
    private static String text(Object value, String field) {
        if (!(value instanceof String s) || s.isBlank()) bad(field + " 값은 필수입니다.");
        return ((String)value).trim();
    }
    private static String token() { byte[] b=new byte[32]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String sha256(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static String hashPassword(String password) {
        try { byte[] salt=new byte[16]; RANDOM.nextBytes(salt); PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,120000,256);
            return "pbkdf2$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static boolean passwordMatches(String password,String stored) {
        try { String[] p=stored.split("\\$"); if(p.length!=3||!p[0].equals("pbkdf2")) return false;
            byte[] salt=Base64.getDecoder().decode(p[1]); PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,120000,256);
            byte[] actual=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(actual,Base64.getDecoder().decode(p[2])); }
        catch(Exception e) { return false; }
    }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
