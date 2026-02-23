package com.ankur.design.multithreading.correctness;

import com.ankur.design.multithreaded.correctness.MovieTheater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MovieTheaterTest {
    private static final Logger log = LoggerFactory.getLogger(MovieTheaterTest.class);
    private MovieTheater movieTheater;

    @BeforeEach
    void setUp() {
        movieTheater = new MovieTheater();
        log.info("=".repeat(80));
        log.info("Setting up new MovieTheater instance");
        log.info("=".repeat(80));
    }

    @Test
    void testConcurrentBookingSameSeat() throws InterruptedException {
        log.info("\n*** TEST: Concurrent Booking Same Seat ***\n");

        final String seatNumber = "A1";
        final int numberOfThreads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        log.info("Launching {} threads to book seat: {}", numberOfThreads, seatNumber);

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i + 1;
            executor.submit(() -> {
                try {
                    log.info("[Thread-{}] Waiting at starting gate...", threadId);
                    startLatch.await(); // Wait for signal to start

                    long startTime = System.nanoTime();
                    log.info("[Thread-{}] STARTED - Attempting to book seat: {}", threadId, seatNumber);

                    boolean result = movieTheater.bookSeat(seatNumber);
                    long endTime = System.nanoTime();
                    long duration = (endTime - startTime) / 1_000; // microseconds

                    if (result) {
                        successCount.incrementAndGet();
                        log.info("[Thread-{}] ✓ SUCCESS - Booked seat {} (took {} μs)",
                                threadId, seatNumber, duration);
                    } else {
                        failureCount.incrementAndGet();
                        log.info("[Thread-{}] ✗ FAILED - Seat {} already booked (took {} μs)",
                                threadId, seatNumber, duration);
                    }

                } catch (Exception e) {
                    log.error("[Thread-{}] ERROR: {}", threadId, e.getMessage(), e);
                } finally {
                    doneLatch.countDown();
                    log.info("[Thread-{}] COMPLETED - Released", threadId);
                }
            });
        }

        Thread.sleep(100); // Let all threads reach the starting gate
        log.info("\n>>> RELEASING ALL THREADS <<<\n");
        startLatch.countDown(); // Release all threads at once

        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("\n" + "=".repeat(80));
        log.info("RESULTS:");
        log.info("  Total Threads: {}", numberOfThreads);
        log.info("  Successful Bookings: {}", successCount.get());
        log.info("  Failed Bookings: {}", failureCount.get());
        log.info("=".repeat(80) + "\n");

        assertTrue(finished, "All threads should complete within timeout");
        assertEquals(1, successCount.get(), "Only one thread should successfully book the seat");
        assertEquals(numberOfThreads - 1, failureCount.get(), "Other threads should fail");
    }

    @Test
    void testConcurrentBookingDifferentSeats() throws InterruptedException {
        log.info("\n*** TEST: Concurrent Booking Different Seats ***\n");

        final int numberOfThreads = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<String> seats = List.of("A1", "A2", "A3", "A4", "A5");

        log.info("Launching {} threads to book different seats", numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i + 1;
            final String seat = seats.get(i);

            executor.submit(() -> {
                try {
                    log.info("[Thread-{}] Waiting at starting gate for seat: {}", threadId, seat);
                    startLatch.await();

                    long startTime = System.nanoTime();
                    log.info("[Thread-{}] STARTED - Attempting to book seat: {}", threadId, seat);

                    boolean result = movieTheater.bookSeat(seat);
                    long endTime = System.nanoTime();
                    long duration = (endTime - startTime) / 1_000;

                    if (result) {
                        successCount.incrementAndGet();
                        log.info("[Thread-{}] ✓ SUCCESS - Booked seat {} (took {} μs)",
                                threadId, seat, duration);
                    } else {
                        log.info("[Thread-{}] ✗ FAILED - Seat {} already booked (took {} μs)",
                                threadId, seat, duration);
                    }

                } catch (Exception e) {
                    log.error("[Thread-{}] ERROR: {}", threadId, e.getMessage(), e);
                } finally {
                    doneLatch.countDown();
                    log.info("[Thread-{}] COMPLETED - Released", threadId);
                }
            });
        }

        Thread.sleep(100);
        log.info("\n>>> RELEASING ALL THREADS <<<\n");
        startLatch.countDown();

        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("\n" + "=".repeat(80));
        log.info("RESULTS:");
        log.info("  Total Threads: {}", numberOfThreads);
        log.info("  Successful Bookings: {}", successCount.get());
        log.info("=".repeat(80) + "\n");

        assertTrue(finished, "All threads should complete within timeout");
        assertEquals(numberOfThreads, successCount.get(), "All threads should successfully book different seats");
    }

    @Test
    void testConcurrentBookingMultipleSeatsWithContention() throws InterruptedException {
        log.info("\n*** TEST: Concurrent Booking Multiple Seats With Contention ***\n");

        final int numberOfThreads = 20;
        final List<String> popularSeats = List.of("A1", "A2", "B1", "B2", "C1");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        ConcurrentHashMap<String, List<Integer>> seatBookingAttempts = new ConcurrentHashMap<>();

        log.info("Launching {} threads to book {} popular seats", numberOfThreads, popularSeats.size());

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i + 1;
            final String seat = popularSeats.get(i % popularSeats.size()); // Multiple threads per seat

            seatBookingAttempts.computeIfAbsent(seat, k -> new CopyOnWriteArrayList<>()).add(threadId);

            executor.submit(() -> {
                try {
                    log.info("[Thread-{}] Waiting to book seat: {}", threadId, seat);
                    startLatch.await();

                    long startTime = System.nanoTime();
                    log.info("[Thread-{}] ATTEMPTING seat: {}", threadId, seat);

                    // Simulate some processing time
                    Thread.sleep(ThreadLocalRandom.current().nextInt(10, 50));

                    boolean result = movieTheater.bookSeat(seat);
                    long endTime = System.nanoTime();
                    long duration = (endTime - startTime) / 1_000_000; // milliseconds

                    if (result) {
                        successCount.incrementAndGet();
                        log.info("[Thread-{}] ✓✓✓ SUCCESS ✓✓✓ - Booked seat {} (took {} ms)",
                                threadId, seat, duration);
                    } else {
                        failureCount.incrementAndGet();
                        log.info("[Thread-{}] ✗✗✗ FAILED ✗✗✗ - Seat {} unavailable (took {} ms)",
                                threadId, seat, duration);
                    }

                } catch (Exception e) {
                    log.error("[Thread-{}] ERROR: {}", threadId, e.getMessage(), e);
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                    log.info("[Thread-{}] DONE", threadId);
                }
            });
        }

        Thread.sleep(200);
        log.info("\n>>> RELEASING ALL {} THREADS <<<\n", numberOfThreads);
        startLatch.countDown();

        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("\n" + "=".repeat(80));
        log.info("FINAL RESULTS:");
        log.info("  Total Threads: {}", numberOfThreads);
        log.info("  Successful Bookings: {}", successCount.get());
        log.info("  Failed Bookings: {}", failureCount.get());
        log.info("\nBooking Attempts Per Seat:");
        seatBookingAttempts.forEach((seat, threads) -> {
            log.info("  Seat {}: {} threads attempted (Thread IDs: {})",
                    seat, threads.size(), threads);
        });
        log.info("=".repeat(80) + "\n");

        assertTrue(finished, "All threads should complete within timeout");
        assertEquals(popularSeats.size(), successCount.get(),
                "Should book exactly as many seats as available");
        assertEquals(numberOfThreads - popularSeats.size(), failureCount.get(),
                "Remaining threads should fail");
    }

    @Test
    void testSequentialBooking() {
        log.info("\n*** TEST: Sequential Booking ***\n");

        log.info("[Main Thread] Booking seat A1");
        boolean first = movieTheater.bookSeat("A1");
        log.info("[Main Thread] Result: {}", first ? "SUCCESS" : "FAILED");

        log.info("[Main Thread] Attempting to book A1 again");
        boolean second = movieTheater.bookSeat("A1");
        log.info("[Main Thread] Result: {}", second ? "SUCCESS" : "FAILED");

        log.info("[Main Thread] Booking different seat A2");
        boolean third = movieTheater.bookSeat("A2");
        log.info("[Main Thread] Result: {}", third ? "SUCCESS" : "FAILED");

        log.info("\n" + "=".repeat(80));
        log.info("RESULTS:");
        log.info("  First booking (A1): {}", first);
        log.info("  Duplicate booking (A1): {}", second);
        log.info("  Different seat (A2): {}", third);
        log.info("=".repeat(80) + "\n");

        assertTrue(first, "First booking should succeed");
        assertFalse(second, "Duplicate booking should fail");
        assertTrue(third, "Different seat booking should succeed");
    }

    @Test
    void testHighContentionStressTest() throws InterruptedException {
        log.info("\n*** TEST: High Contention Stress Test ***\n");

        final int numberOfThreads = 50;
        final String hotSeat = "VIP-1";

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        log.info("Launching {} threads competing for single seat: {}", numberOfThreads, hotSeat);
        log.info("This simulates high contention scenario\n");

        long testStartTime = System.currentTimeMillis();

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i + 1;

            executor.submit(() -> {
                try {
                    startLatch.await();

                    long threadStart = System.nanoTime();
                    boolean result = movieTheater.bookSeat(hotSeat);
                    long threadEnd = System.nanoTime();
                    long duration = (threadEnd - threadStart) / 1_000;

                    if (result) {
                        successCount.incrementAndGet();
                        log.info("[Thread-{:02d}] 🎟️  WON THE SEAT! (took {} μs)", threadId, duration);
                    } else {
                        log.debug("[Thread-{:02d}] Lost (took {} μs)", threadId, duration);
                    }

                } catch (Exception e) {
                    log.error("[Thread-{}] ERROR: {}", threadId, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        Thread.sleep(100);
        log.info(">>> GO! <<<\n");
        startLatch.countDown();

        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        long testDuration = System.currentTimeMillis() - testStartTime;
        executor.shutdown();

        log.info("\n" + "=".repeat(80));
        log.info("STRESS TEST RESULTS:");
        log.info("  Threads Launched: {}", numberOfThreads);
        log.info("  Successful Bookings: {}", successCount.get());
        log.info("  Failed Attempts: {}", numberOfThreads - successCount.get());
        log.info("  Test Duration: {} ms", testDuration);
        log.info("  Thread Safety: {}", successCount.get() == 1 ? "✓ PASSED" : "✗ FAILED");
        log.info("=".repeat(80) + "\n");

        assertTrue(finished, "All threads should complete");
        assertEquals(1, successCount.get(), "Exactly one thread should win in high contention");
    }
}