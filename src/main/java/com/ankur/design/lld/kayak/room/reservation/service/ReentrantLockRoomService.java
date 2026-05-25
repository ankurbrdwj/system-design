package com.ankur.design.lld.kayak.room.reservation.service;

import com.ankur.design.lld.kayak.room.reservation.model.BookingRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Strategy: one ReentrantLock per room type.
 *
 * Benefits over synchronized:
 *  - tryLock(timeout) avoids blocking forever
 *  - fairness=true guarantees FIFO ordering (no starvation)
 *  - lockInterruptibly() allows cancellation
 */
public class ReentrantLockRoomService implements RoomBookingService {

    private final Map<String, Integer> rooms  = new HashMap<>();
    private final Map<String, ReentrantLock> locks = new HashMap<>();

    // fairness=true: longest-waiting thread gets the lock next
    private static final boolean FAIR = true;

    @Override
    public void initializeRooms() {
        for (String type : new String[]{"SINGLE", "DOUBLE", "SUITE"}) {
            int capacity = type.equals("DOUBLE") ? 4 : 3;
            rooms.put(type, capacity);
            locks.put(type, new ReentrantLock(FAIR));
        }
    }

    @Override
    public boolean bookRoom(BookingRequest request) {
        String type = request.getRoomType();
        ReentrantLock lock = locks.get(type);
        if (lock == null) return false;

        lock.lock();                          // blocks until acquired
        try {
            int available = rooms.get(type);
            if (available <= 0) return false;
            rooms.put(type, available - 1);
            return true;
        } finally {
            lock.unlock();                    // always releases, even on exception
        }
    }

    /**
     * tryLock variant — gives up after waiting, instead of blocking forever.
     * Useful when you want to reject rather than queue a booking under heavy load.
     */
    public boolean tryBookRoom(BookingRequest request, long timeoutMs) throws InterruptedException {
        String type = request.getRoomType();
        ReentrantLock lock = locks.get(type);
        if (lock == null) return false;

        if (!lock.tryLock(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            return false; // gave up waiting
        }
        try {
            int available = rooms.get(type);
            if (available <= 0) return false;
            rooms.put(type, available - 1);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<String, Integer> availability() {
        return Map.copyOf(rooms);
    }
}