package com.tayotayo.backend.orgs;

public record OrganizationResponse(
        long id,
        String name,
        String orgType,
        String phone,
        String address,
        Double latitude,
        Double longitude,
        String timezone,
        String status) {
}
