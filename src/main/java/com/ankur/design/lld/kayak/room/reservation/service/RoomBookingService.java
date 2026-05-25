package com.ankur.design.lld.kayak.room.reservation.service;

import com.ankur.design.lld.kayak.room.reservation.model.BookingRequest;

import java.util.Collection;
import java.util.Map;

/**
 * Common contract for all room booking strategies.
 * Each implementation uses a different concurrency mechanism.
 */
public interface RoomBookingService {

    /** Populate the room inventory (3 SINGLE, 4 DOUBLE, 3 SUITE). */
    void initializeRooms();

    /**
     * Attempt to book a room for the given request.
     * @return true if booked, false if room unavailable or unknown type
     */
    boolean bookRoom(BookingRequest request);

    /** Current availability per room type. */
    Map<String, Integer> availability();
}