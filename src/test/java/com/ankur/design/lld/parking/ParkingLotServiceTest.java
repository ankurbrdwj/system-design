package com.ankur.design.lld.parking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ParkingLotServiceTest {

    ParkingService lot;   // depends on interface, not concrete class

    @BeforeEach
    void setUp() {
        // depend on the interface — swap impl without changing tests
        lot = new ParkingLotService(3, new HourlyFeeCalculator(10), new FirstAvailableSpotSelector());
    }

    // ── functional ───────────────────────────────────────────────────

    @Test
    void enter_returnsTicketWithSpotAssigned() throws Exception {
        Ticket t = lot.enter("KA-01");
        assertNotNull(t.ticketId);
        assertNotNull(t.spotId);
        assertEquals("KA-01", t.vehicleNumber);
        assertEquals(TicketState.ACTIVE, t.state);
    }

    @Test
    void enter_reducesAvailableSpots() throws Exception {
        lot.enter("KA-01");
        assertEquals(2, lot.availableSpots());
    }

    @Test
    void pay_transitionsTicketToPaid() throws Exception {
        Ticket t = lot.enter("KA-01");
        lot.pay(t.ticketId);
        assertEquals(TicketState.PAID, t.state);
    }

    @Test
    void pay_returnsNonNegativeFee() throws Exception {
        Ticket t = lot.enter("KA-01");
        var fee = lot.pay(t.ticketId);
        assertTrue(fee.signum() >= 0);
    }

    @Test
    void exit_releasesSpot() throws Exception {
        Ticket t = lot.enter("KA-01");
        lot.pay(t.ticketId);
        lot.exit(t.ticketId);
        assertEquals(3, lot.availableSpots());
    }

    @Test
    void exit_withoutPay_throws() throws Exception {
        Ticket t = lot.enter("KA-01");
        assertThrows(IllegalStateException.class, () -> lot.exit(t.ticketId));
    }

    @Test
    void pay_alreadyPaid_throws() throws Exception {
        Ticket t = lot.enter("KA-01");
        lot.pay(t.ticketId);
        assertThrows(IllegalStateException.class, () -> lot.pay(t.ticketId));
    }

    @Test
    void enter_sameVehicle_twice_throws() throws Exception {
        lot.enter("KA-01");
        assertThrows(IllegalStateException.class, () -> lot.enter("KA-01"));
    }

    @Test
    void fullLot_afterExit_acceptsNewCar() throws Exception {
        Ticket a = lot.enter("KA-01");
        Ticket b = lot.enter("KA-02");
        Ticket c = lot.enter("KA-03");
        lot.pay(a.ticketId);
        lot.exit(a.ticketId);
        Ticket d = lot.enter("KA-04");   // should succeed — spot freed
        assertNotNull(d.spotId);
    }

    // ── concurrency ──────────────────────────────────────────────────

    @Test
    void concurrentEntry_noTwoCarsSameSpot() throws Exception {
        ParkingService bigLot = new ParkingLotService(10, new HourlyFeeCalculator(10), new FirstAvailableSpotSelector());
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        Set<String> assignedSpots = ConcurrentHashMap.newKeySet();
        List<String> duplicates = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String plate = "CAR-" + i;
            pool.submit(() -> {
                try {
                    Ticket t = bigLot.enter(plate);
                    if (!assignedSpots.add(t.spotId)) {
                        duplicates.add(t.spotId);   // same spot assigned twice
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        done.await();
        pool.shutdown();
        assertTrue(duplicates.isEmpty(), "Duplicate spots assigned: " + duplicates);
    }
}