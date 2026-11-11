package com.tayotayo.backend.orgs;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.jdbc.core.JdbcTemplate;
import com.tayotayo.backend.auth.AccessTokenStore;

@RestController
@RequestMapping("/api/v1/orgs")
public class OrganizationController {
    private final OrganizationService organizationService;
    private final JdbcTemplate jdbcTemplate;
    private final AccessTokenStore accessTokens;

    public OrganizationController(OrganizationService organizationService, JdbcTemplate jdbcTemplate, AccessTokenStore accessTokens) {
        this.organizationService = organizationService;
        this.jdbcTemplate = jdbcTemplate;
        this.accessTokens = accessTokens;
    }

    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody OrganizationCreateRequest request) {
        long userId = accessTokens.requireUser(authorization);
        OrganizationResponse created = organizationService.create(request);
        jdbcTemplate.update("INSERT INTO org_staff (org_id,user_id,name,staff_role,web_role,status) VALUES (?,?,?,'ADMIN','OWNER','ACTIVE')",
                created.id(), userId, "기관 관리자");
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
