package com.ankur.design.lld.kayak.room.reservation.model;

import lombok.Getter;

/**
 * Represents a hotel room type and its availability.
 * availableCount is intentionally not thread-safe here — Section 2 will surface the race condition.
 */
@Getter
public class Room {

    private final String roomType;
    private int availableCount;

    public Room(String roomType, int availableCount) {
        this.roomType = roomType;
        this.availableCount = availableCount;
    }

    public boolean isAvailable() {
        return availableCount > 0;
    }

    public void decrementAvailability() {
        availableCount--;
    }

    @Override
    public String toString() {
        return "Room{type='" + roomType + "', available=" + availableCount + "}";
    }
}