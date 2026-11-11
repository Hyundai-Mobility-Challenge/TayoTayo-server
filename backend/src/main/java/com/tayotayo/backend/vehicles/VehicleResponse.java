package com.tayotayo.backend.vehicles;

public record VehicleResponse(
        long id,
        long orgId,
        String name,
        String plateNumber,
        String model,
        Integer seatCapacity,
        String status) {
}
