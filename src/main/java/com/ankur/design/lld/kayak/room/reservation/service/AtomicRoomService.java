package com.ankur.design.lld.kayak.room.reservation.service;

import com.ankur.design.lld.kayak.room.reservation.model.BookingRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Strategy: AtomicInteger per room type with a CAS (compare-and-set) loop.
 *
 * Lock-free — no thread ever blocks waiting for another.
 * Instead of locking, each thread reads the current count, computes the new
 * value, and only writes it back if nothing else changed in the meantime.
 * If another thread changed it first, retry.
 *
 * Best throughput under high contention because there is no lock overhead,
 * but each thread may spin a few iterations under extreme contention.
 */
public class AtomicRoomService implements RoomBookingService {

    private final Map<String, AtomicInteger> availability = new HashMap<>();

    @Override
    public void initializeRooms() {
        availability.put("SINGLE", new AtomicInteger(3));
        availability.put("DOUBLE", new AtomicInteger(4));
        availability.put("SUITE",  new AtomicInteger(3));
    }

    @Override
    public boolean bookRoom(BookingRequest request) {
        AtomicInteger counter = availability.get(request.getRoomType());
        if (counter == null) return false;

        int current;
        do {
            current = counter.get();
            if (current <= 0) return false;
            // Only write (current - 1) if `current` hasn't changed since we read it.
            // If it has changed (another thread booked), the CAS fails and we retry.
        } while (!counter.compareAndSet(current, current - 1));

        return true;
    }

    @Override
    public Map<String, Integer> availability() {
        Map<String, Integer> snapshot = new HashMap<>();
        availability.forEach((type, counter) -> snapshot.put(type, counter.get()));
        return snapshot;
    }
}