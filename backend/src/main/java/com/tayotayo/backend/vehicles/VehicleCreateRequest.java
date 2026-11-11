package com.tayotayo.backend.vehicles;

public record VehicleCreateRequest(
        String name,
        String plateNumber,
        String model,
        Integer seatCapacity) {
}
