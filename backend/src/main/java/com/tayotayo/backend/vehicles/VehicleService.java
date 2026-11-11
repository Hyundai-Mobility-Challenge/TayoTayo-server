package com.tayotayo.backend.vehicles;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VehicleService {
    private static final int MAX_PAGE_SIZE = 100;

    private final VehicleRepository vehicleRepository;
    private final com.tayotayo.backend.orgs.OrganizationRepository organizationRepository;

    public VehicleService(VehicleRepository vehicleRepository,
            com.tayotayo.backend.orgs.OrganizationRepository organizationRepository) {
        this.vehicleRepository = vehicleRepository;
        this.organizationRepository = organizationRepository;
    }

    public VehicleResponse createVehicle(long orgId, VehicleCreateRequest request) {
        if (orgId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orgId must be a positive number.");
        }
        if (!organizationRepository.existsActive(orgId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "활성 기관을 찾을 수 없습니다.");
        }
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "차량 이름은 필수입니다.");
        }
        if (request.name().trim().length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "차량 이름은 50자 이하여야 합니다.");
        }
        if (request.plateNumber() != null && request.plateNumber().length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "차량 번호는 20자 이하여야 합니다.");
        }
        if (request.model() != null && request.model().length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "차량 모델은 50자 이하여야 합니다.");
        }
        if (request.seatCapacity() != null && (request.seatCapacity() < 1 || request.seatCapacity() > 32767)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seatCapacity는 1에서 32767 사이여야 합니다.");
        }
        return vehicleRepository.create(orgId, request);
    }

    public VehiclePageResponse getVehicles(long orgId, int limit, String cursor) {
        if (orgId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orgId must be a positive number.");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 100.");
        }

        Long afterId = parseCursor(cursor);
        List<VehicleResponse> fetched = vehicleRepository.findPage(orgId, afterId, limit + 1);
        boolean hasNext = fetched.size() > limit;
        List<VehicleResponse> items = new ArrayList<>(fetched.subList(0, Math.min(limit, fetched.size())));
        String nextCursor = hasNext ? Long.toString(items.get(items.size() - 1).id()) : null;

        return new VehiclePageResponse(items, nextCursor);
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(cursor);
            if (value <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cursor must be a positive vehicle ID.");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cursor must be a numeric vehicle ID.");
        }
    }
}
