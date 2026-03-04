package com.ankur.design.lld.kayak.room.reservation;

import com.ankur.design.lld.kayak.room.reservation.model.BookingRequest;
import com.ankur.design.lld.kayak.room.reservation.model.Room;
import com.ankur.design.lld.kayak.room.reservation.service.RoomDatabaseAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Section 2 – Workable Code
 *
 * Tests verify that RoomDatabaseAccessService:
 *  1. Initialises rooms correctly
 *  2. Books rooms and tracks availability
 *  3. Never over-books under concurrent load (the Section 2 race-condition fix)
 *  4. Handles unknown room types gracefully
 */
class RoomDatabaseAccessServiceTest {

    private RoomDatabaseAccessService service;

    @BeforeEach
    void setUp() {
        service = new RoomDatabaseAccessService();
        service.initializeRooms();
    }

    // -------------------------------------------------------------------------
    // 1. Initialization
    // -------------------------------------------------------------------------
    @Nested
    class Initialization {

        @Test
        void allThreeRoomTypesArePresent() {
            assertNotNull(service.getRoom("SINGLE"));
            assertNotNull(service.getRoom("DOUBLE"));
            assertNotNull(service.getRoom("SUITE"));
        }

        @Test
        void initialAvailabilityIsCorrect() {
            assertEquals(3, service.getRoom("SINGLE").getAvailableCount());
            assertEquals(4, service.getRoom("DOUBLE").getAvailableCount());
            assertEquals(3, service.getRoom("SUITE").getAvailableCount());
        }

        @Test
        void totalRoomsAcrossAllTypesSumToTen() {
            int total = service.getAllRooms().stream()
                    .mapToInt(Room::getAvailableCount)
                    .sum();
            assertEquals(10, total);
        }
    }

    // -------------------------------------------------------------------------
    // 2. Basic single-threaded booking behaviour
    // -------------------------------------------------------------------------
    @Nested
    class SingleThreadedBooking {

        @Test
        void bookingAvailableRoomReturnsTrue() {
            assertTrue(service.bookRoom(new BookingRequest("Alice", "SINGLE")));
        }

        @Test
        void bookingDecrementsAvailability() {
            service.bookRoom(new BookingRequest("Alice", "SINGLE"));
            assertEquals(2, service.getRoom("SINGLE").getAvailableCount());
        }

        @Test
        void bookingAllRoomsOfOneTypeExhaustsAvailability() {
            service.bookRoom(new BookingRequest("Alice",   "SINGLE"));
            service.bookRoom(new BookingRequest("Bob",     "SINGLE"));
            service.bookRoom(new BookingRequest("Charlie", "SINGLE"));
            assertEquals(0, service.getRoom("SINGLE").getAvailableCount());
        }

        @Test
        void bookingWhenUnavailableReturnsFalse() {
            service.bookRoom(new BookingRequest("Alice",   "SINGLE"));
            service.bookRoom(new BookingRequest("Bob",     "SINGLE"));
            service.bookRoom(new BookingRequest("Charlie", "SINGLE"));

            assertFalse(service.bookRoom(new BookingRequest("Dave", "SINGLE")));
        }

        @Test
        void availabilityNeverGoesBelowZero() {
            for (int i = 0; i < 10; i++) {
                service.bookRoom(new BookingRequest("Guest" + i, "SINGLE"));
            }
            assertTrue(service.getRoom("SINGLE").getAvailableCount() >= 0);
        }

        @Test
        void unknownRoomTypeReturnsFalse() {
            assertFalse(service.bookRoom(new BookingRequest("Alice", "PENTHOUSE")));
        }

        @Test
        void bookingOneTypeDoesNotAffectOtherTypes() {
            service.bookRoom(new BookingRequest("Alice", "SINGLE"));
            service.bookRoom(new BookingRequest("Bob",   "SINGLE"));
            service.bookRoom(new BookingRequest("Charlie","SINGLE"));

            // DOUBLE and SUITE untouched
            assertEquals(4, service.getRoom("DOUBLE").getAvailableCount());
            assertEquals(3, service.getRoom("SUITE").getAvailableCount());
        }
    }

    // -------------------------------------------------------------------------
    // 3. Concurrency — the core Section 2 fix
    // -------------------------------------------------------------------------
    @Nested
    class ConcurrentBooking {

        /**
         * Fires more threads than available rooms and asserts:
         *  - exactly `capacity` bookings succeed
         *  - availability never goes negative
         *
         * Repeated 5 times to increase the chance of catching a race condition
         * that only manifests under certain thread interleavings.
         */
        @RepeatedTest(5)
        void exactlyCapacityBookingsSucceedForSingleRoom() throws Exception {
            int capacity = service.getRoom("SINGLE").getAvailableCount(); // 3
            int totalRequests = capacity + 5;  // intentionally more than available

            AtomicInteger successCount = new AtomicInteger();
            CountDownLatch startGate = new CountDownLatch(1);   // all threads start simultaneously
            CountDownLatch doneLatch  = new CountDownLatch(totalRequests);

            ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
            for (int i = 0; i < totalRequests; i++) {
                final String guest = "Guest-" + i;
                executor.submit(() -> {
                    try {
                        startGate.await();   // wait for the signal
                        if (service.bookRoom(new BookingRequest(guest, "SINGLE"))) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startGate.countDown();  // release all threads at once
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Timed out waiting for bookings");
            executor.shutdown();

            assertEquals(capacity, successCount.get(),
                    "Exactly " + capacity + " bookings should succeed, no more");
            assertEquals(0, service.getRoom("SINGLE").getAvailableCount(),
                    "Availability should be exactly 0, not negative");
        }

        @RepeatedTest(5)
        void concurrentBookingsAcrossDifferentRoomTypesDoNotInterfere() throws Exception {
            int singleCap = service.getRoom("SINGLE").getAvailableCount(); // 3
            int doubleCap = service.getRoom("DOUBLE").getAvailableCount(); // 4
            int totalRequests = 20;

            AtomicInteger singleSuccess = new AtomicInteger();
            AtomicInteger doubleSuccess = new AtomicInteger();
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(totalRequests);

            ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
            for (int i = 0; i < totalRequests; i++) {
                String roomType = (i % 2 == 0) ? "SINGLE" : "DOUBLE";
                final String guest = "Guest-" + i;
                executor.submit(() -> {
                    try {
                        startGate.await();
                        boolean booked = service.bookRoom(new BookingRequest(guest, roomType));
                        if (booked) {
                            if ("SINGLE".equals(roomType)) singleSuccess.incrementAndGet();
                            else doubleSuccess.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startGate.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(singleCap, singleSuccess.get(), "SINGLE bookings must not exceed capacity");
            assertEquals(doubleCap, doubleSuccess.get(), "DOUBLE bookings must not exceed capacity");
            assertTrue(service.getRoom("SINGLE").getAvailableCount() >= 0);
            assertTrue(service.getRoom("DOUBLE").getAvailableCount() >= 0);
        }
    }
}