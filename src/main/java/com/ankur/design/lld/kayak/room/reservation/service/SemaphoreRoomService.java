package com.ankur.design.lld.kayak.room.reservation.service;

import com.ankur.design.lld.kayak.room.reservation.model.BookingRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Strategy: one Semaphore per room type, initialised with room capacity as permits.
 *
 * Most semantically natural mapping:
 *   "3 available SINGLE rooms" == Semaphore(3 permits)
 *   booking a room             == acquiring a permit
 *   cancellation (future)      == releasing a permit
 *
 * tryAcquire() is non-blocking — returns immediately if no permit is available
 * rather than queuing the thread.
 *
 * fairness=true gives FIFO ordering so no guest starves.
 */
public class SemaphoreRoomService implements RoomBookingService {

    private final Map<String, Semaphore> semaphores = new HashMap<>();

    @Override
    public void initializeRooms() {
        boolean fair = true;
        semaphores.put("SINGLE", new Semaphore(3, fair));
        semaphores.put("DOUBLE", new Semaphore(4, fair));
        semaphores.put("SUITE",  new Semaphore(3, fair));
    }

    @Override
    public boolean bookRoom(BookingRequest request) {
        Semaphore semaphore = semaphores.get(request.getRoomType());
        if (semaphore == null) return false;

        // tryAcquire() — non-blocking: instant success or instant failure
        return semaphore.tryAcquire();
    }

    /** Cancel a booking — release the permit back to the pool. */
    public void cancelBooking(String roomType) {
        Semaphore semaphore = semaphores.get(roomType);
        if (semaphore != null) semaphore.release();
    }

    @Override
    public Map<String, Integer> availability() {
        Map<String, Integer> snapshot = new HashMap<>();
        semaphores.forEach((type, sem) -> snapshot.put(type, sem.availablePermits()));
        return snapshot;
    }
}