package com.ankur.design.lld.kayak.room.reservation.service;

import com.ankur.design.lld.kayak.room.reservation.model.BookingRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Strategy: one BlockingQueue of "tokens" per room type.
 *
 * Each room slot is represented by a token (a String room ID) in the queue.
 * Booking = poll() a token out (non-blocking).
 * Cancellation = offer() the token back in.
 *
 * Benefits:
 *  - FIFO ordering — first guest to poll wins
 *  - Natural fit for a producer-consumer pipeline: Main pushes BookingRequests
 *    into one queue; workers poll from it, then poll a room token from here
 *  - Cancellation is trivially O(1) — just offer the token back
 *
 * Note: the queue capacity is fixed at init time, so no over-booking is possible
 * by construction — poll() returns null when the queue is empty.
 */
public class BlockingQueueRoomService implements RoomBookingService {

    // Each entry in the queue represents one available room slot
    private final Map<String, BlockingQueue<String>> slots = new HashMap<>();

    @Override
    public void initializeRooms() {
        slots.put("SINGLE", buildSlots("SINGLE", 3));
        slots.put("DOUBLE", buildSlots("DOUBLE", 4));
        slots.put("SUITE",  buildSlots("SUITE",  3));
    }

    private BlockingQueue<String> buildSlots(String type, int count) {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(count);
        for (int i = 1; i <= count; i++) {
            queue.offer(type + "-" + i);   // e.g. "SINGLE-1", "SINGLE-2", "SINGLE-3"
        }
        return queue;
    }

    @Override
    public boolean bookRoom(BookingRequest request) {
        BlockingQueue<String> queue = slots.get(request.getRoomType());
        if (queue == null) return false;

        // poll() is non-blocking: returns a token immediately, or null if empty
        String token = queue.poll();
        if (token != null) {
            System.out.println("BOOKED  | " + request.getGuestName() + " -> " + token);
        }
        return token != null;
    }

    /** Cancel a booking by returning the token to the pool. */
    public void cancelBooking(String roomToken) {
        String type = roomToken.split("-")[0];
        BlockingQueue<String> queue = slots.get(type);
        if (queue != null)
            queue.offer(roomToken);
    }

    @Override
    public Map<String, Integer> availability() {
        Map<String, Integer> snapshot = new HashMap<>();
        slots.forEach((type, queue) -> snapshot.put(type, queue.size()));
        return snapshot;
    }
}