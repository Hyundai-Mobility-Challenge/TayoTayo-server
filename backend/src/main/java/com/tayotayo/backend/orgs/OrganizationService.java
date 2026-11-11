package com.tayotayo.backend.orgs;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizationService {
    private static final Set<String> ORG_TYPES = Set.of("DAYCARE", "KINDERGARTEN", "ACADEMY", "SCHOOL", "OTHER");

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public OrganizationResponse create(OrganizationCreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw badRequest("기관 이름은 필수입니다.");
        }
        if (request.name().trim().length() > 100) {
            throw badRequest("기관 이름은 100자 이하여야 합니다.");
        }
        checkMaxLength(request.phone(), 30, "기관 전화번호");
        checkMaxLength(request.address(), 255, "기관 주소");

        String orgType = request.orgType() == null || request.orgType().isBlank()
                ? "OTHER" : request.orgType().trim().toUpperCase();
        if (!ORG_TYPES.contains(orgType)) {
            throw badRequest("orgType은 DAYCARE, KINDERGARTEN, ACADEMY, SCHOOL, OTHER 중 하나여야 합니다.");
        }
        String timezone = request.timezone() == null || request.timezone().isBlank()
                ? "Asia/Seoul" : request.timezone().trim();
        if (timezone.length() > 40) {
            throw badRequest("timezone은 40자 이하여야 합니다.");
        }

        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw badRequest("latitude와 longitude는 둘 다 입력하거나 둘 다 비워야 합니다.");
        }
        if (request.latitude() != null && (request.latitude() < -90 || request.latitude() > 90)) {
            throw badRequest("latitude는 -90에서 90 사이여야 합니다.");
        }
        if (request.longitude() != null && (request.longitude() < -180 || request.longitude() > 180)) {
            throw badRequest("longitude는 -180에서 180 사이여야 합니다.");
        }

        return organizationRepository.create(request, orgType, timezone);
    }

    private void checkMaxLength(String value, int max, String fieldName) {
        if (value != null && value.length() > max) {
            throw badRequest(fieldName + "은 " + max + "자 이하여야 합니다.");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
