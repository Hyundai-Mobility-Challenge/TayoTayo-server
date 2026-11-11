package com.tayotayo.backend.orgs;

public record OrganizationCreateRequest(
        String name,
        String orgType,
        String phone,
        String address,
        Double latitude,
        Double longitude,
        String timezone) {
}
