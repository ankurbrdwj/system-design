package com.ankur.design.lld.kayak.room.reservation.service;

import com.ankur.design.lld.kayak.room.reservation.model.BookingRequest;
import com.ankur.design.lld.kayak.room.reservation.model.Room;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Simulates DB access for room data.
 *
 * Section 2 fix: bookRoom() synchronizes on the individual Room object so the
 * check-then-act (isAvailable → decrement) is atomic per room type.
 * Different room types can still be booked concurrently — no global lock.
 *
 * A BlockingQueue of pending requests is used in Main to bound concurrency;
 * this service just needs to be safe when called from multiple threads.
 */
public class RoomDatabaseAccessService {

    // 3 + 4 + 3 = 10 rooms total as per the challenge
    private final Map<String, Room> rooms = new HashMap<>();

    public void initializeRooms() {
        rooms.put("SINGLE", new Room("SINGLE", 3));
        rooms.put("DOUBLE", new Room("DOUBLE", 4));
        rooms.put("SUITE",  new Room("SUITE",  3));
    }

    public Room getRoom(String roomType) {
        return rooms.get(roomType);
    }

    public Collection<Room> getAllRooms() {
        return rooms.values();
    }

    /**
     * Thread-safe booking using per-room synchronization.
     * Synchronizing on `room` (not `this`) means SINGLE, DOUBLE, SUITE
     * requests don't block each other unnecessarily.
     */
    public boolean bookRoom(BookingRequest request) {
        Room room = rooms.get(request.getRoomType());
        if (room == null) {
            System.out.println("FAILED  | " + request.getGuestName() + " -> " + request.getRoomType() + " (unknown type)");
            return false;
        }
        synchronized (room) {
            if (!room.isAvailable()) {
                System.out.println("FAILED  | " + request.getGuestName() + " -> " + request.getRoomType() + " (unavailable)");
                return false;
            }
            room.decrementAvailability();
            System.out.println("BOOKED  | " + request.getGuestName() + " -> " + request.getRoomType()
                    + " (remaining: " + room.getAvailableCount() + ")");
            return true;
        }
    }
}