package com.tayotayo.backend.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Short-lived development access tokens; persisted refresh sessions remain in MySQL. */
@Component
public class AccessTokenStore {
    private record Token(long userId, Instant expiresAt) {}
    private final ConcurrentHashMap<String, Token> tokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    public String issue(long userId) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(value, new Token(userId, Instant.now().plusSeconds(3600)));
        return value;
    }
    public long requireUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Bearer access token이 필요합니다.");
        Token token = tokens.get(authorization.substring(7).trim());
        if (token == null || token.expiresAt().isBefore(Instant.now())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"access token이 만료되었거나 유효하지 않습니다.");
        return token.userId();
    }
}
