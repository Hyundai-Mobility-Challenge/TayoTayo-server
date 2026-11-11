package com.tayotayo.backend.vehicles;

import java.util.List;

public record VehiclePageResponse(List<VehicleResponse> items, String nextCursor) {
}
