package com.ankur.design.multithreading.correctness;

import com.ankur.design.multithreaded.correctness.MovieTheater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MovieTheaterCompletableFutureTest {
    private static final Logger log = LoggerFactory.getLogger(MovieTheaterCompletableFutureTest.class);
    private MovieTheater movieTheater;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        movieTheater = new MovieTheater();
        executor = Executors.newFixedThreadPool(20);
        log.info("=".repeat(80));
        log.info("Setting up new MovieTheater instance with CompletableFuture approach");
        log.info("=".repeat(80));
    }

    @Test
    void testConcurrentBookingSameSeatWithCompletableFuture() {
        log.info("\n*** TEST: Concurrent Booking Same Seat (CompletableFuture) ***\n");

        final String seatNumber = "A1";
        final int numberOfThreads = 10;

        log.info("Creating {} CompletableFuture tasks for seat: {}", numberOfThreads, seatNumber);

        // Create list of CompletableFutures
        List<CompletableFuture<BookingResult>> bookingFutures = IntStream.range(0, numberOfThreads)
                .mapToObj(threadId -> CompletableFuture.supplyAsync(() -> {
                    long startTime = System.nanoTime();
                    log.info("[Thread-{}] STARTED - Attempting to book seat: {}", threadId + 1, seatNumber);

                    boolean result = movieTheater.bookSeat(seatNumber);
                    long endTime = System.nanoTime();
                    long duration = (endTime - startTime) / 1_000; // microseconds

                    if (result) {
                        log.info("[Thread-{}] ✓ SUCCESS - Booked seat {} (took {} μs)",
                                threadId + 1, seatNumber, duration);
                    } else {
                        log.info("[Thread-{}] ✗ FAILED - Seat {} already booked (took {} μs)",
                                threadId + 1, seatNumber, duration);
                    }

                    return new BookingResult(threadId + 1, seatNumber, result, duration);
                }, executor))
                .collect(Collectors.toList());

        // Wait for all futures to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                bookingFutures.toArray(new CompletableFuture[0])
        );

        // Wait with timeout
        try {
            allFutures.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("Futures did not complete in time: " + e.getMessage());
        }

        // Collect results
        List<BookingResult> results = bookingFutures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // Analyze results
        long successCount = results.stream().filter(r -> r.success).count();
        long failureCount = results.stream().filter(r -> !r.success).count();
        double avgDuration = results.stream().mapToLong(r -> r.durationMicros).average().orElse(0);

        log.info("\n" + "=".repeat(80));
        log.info("RESULTS:");
        log.info("  Total Tasks: {}", numberOfThreads);
        log.info("  Successful Bookings: {}", successCount);
        log.info("  Failed Bookings: {}", failureCount);
        log.info("  Average Duration: {:.2f} μs", avgDuration);
        log.info("=".repeat(80) + "\n");

        assertEquals(1, successCount, "Only one thread should successfully book the seat");
        assertEquals(numberOfThreads - 1, failureCount, "Other threads should fail");
    }

    @Test
    void testConcurrentBookingDifferentSeatsWithResults() {
        log.info("\n*** TEST: Concurrent Booking Different Seats (with Results) ***\n");

        final List<String> seats = List.of("A1", "A2", "A3", "A4", "A5");
        log.info("Booking {} different seats concurrently", seats.size());

        // Create futures for each seat
        Map<String, CompletableFuture<BookingResult>> seatBookings = IntStream.range(0, seats.size())
                .boxed()
                .collect(Collectors.toMap(
                        seats::get,
                        i -> CompletableFuture.supplyAsync(() -> {
                            String seat = seats.get(i);
                            int threadId = i + 1;
                            long startTime = System.nanoTime();

                            log.info("[Thread-{}] STARTED - Attempting to book seat: {}", threadId, seat);

                            boolean result = movieTheater.bookSeat(seat);
                            long endTime = System.nanoTime();
                            long duration = (endTime - startTime) / 1_000;

                            log.info("[Thread-{}] {} - Seat {} (took {} μs)",
                                    threadId, result ? "✓ SUCCESS" : "✗ FAILED", seat, duration);

                            return new BookingResult(threadId, seat, result, duration);
                        }, executor)
                ));

        // Wait for all to complete
        CompletableFuture.allOf(seatBookings.values().toArray(new CompletableFuture[0])).join();

        // Get results per seat
        log.info("\n" + "=".repeat(80));
        log.info("BOOKING RESULTS BY SEAT:");
        seatBookings.forEach((seat, future) -> {
            BookingResult result = future.join();
            log.info("  Seat {}: {} (Thread-{}, {} μs)",
                    seat,
                    result.success ? "BOOKED ✓" : "FAILED ✗",
                    result.threadId,
                    result.durationMicros);
        });
        log.info("=".repeat(80) + "\n");

        // All should succeed
        long successCount = seatBookings.values().stream()
                .map(CompletableFuture::join)
                .filter(r -> r.success)
                .count();

        assertEquals(seats.size(), successCount, "All different seats should be booked successfully");
    }

    @Test
    void testCompletableFutureChaining() {
        log.info("\n*** TEST: CompletableFuture Chaining (Sequential Operations) ***\n");

        final String seat1 = "VIP-1";
        final String seat2 = "VIP-2";

        // Chain operations: Book seat1, then book seat2, then verify
        CompletableFuture<String> bookingChain = CompletableFuture
                .supplyAsync(() -> {
                    log.info("[Chain-Step-1] Booking seat: {}", seat1);
                    boolean result = movieTheater.bookSeat(seat1);
                    log.info("[Chain-Step-1] Result: {}", result ? "SUCCESS ✓" : "FAILED ✗");
                    return result ? seat1 : null;
                }, executor)
                .thenApplyAsync(firstSeat -> {
                    log.info("[Chain-Step-2] First seat: {}, now booking: {}", firstSeat, seat2);
                    boolean result = movieTheater.bookSeat(seat2);
                    log.info("[Chain-Step-2] Result: {}", result ? "SUCCESS ✓" : "FAILED ✗");
                    return result ? seat2 : null;
                }, executor)
                .thenApplyAsync(secondSeat -> {
                    log.info("[Chain-Step-3] Attempting to re-book: {}", seat1);
                    boolean result = movieTheater.bookSeat(seat1);
                    log.info("[Chain-Step-3] Re-booking should fail: {}", result ? "UNEXPECTED SUCCESS" : "FAILED AS EXPECTED ✓");
                    return secondSeat;
                }, executor)
                .exceptionally(ex -> {
                    log.error("[Chain-Error] Exception occurred: {}", ex.getMessage());
                    return null;
                });

        String finalSeat = bookingChain.join();

        log.info("\n" + "=".repeat(80));
        log.info("CHAINING RESULT: Final seat booked = {}", finalSeat);
        log.info("=".repeat(80) + "\n");

        assertNotNull(finalSeat, "Chained booking should complete successfully");
        assertEquals(seat2, finalSeat);
    }

    @Test
    void testCompletableFutureRaceCondition() {
        log.info("\n*** TEST: Race Condition with CompletableFuture.anyOf ***\n");

        final String hotSeat = "VIP-1";
        final int racers = 5;

        log.info("Starting {} threads racing for seat: {}", racers, hotSeat);

        // Create competing futures
        List<CompletableFuture<BookingResult>> racingFutures = IntStream.range(0, racers)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    int threadId = i + 1;
                    log.info("[Racer-{}] Ready to race for seat: {}", threadId, hotSeat);

                    // Small random delay to make race more interesting
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextInt(5, 20));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    long startTime = System.nanoTime();
                    boolean result = movieTheater.bookSeat(hotSeat);
                    long endTime = System.nanoTime();
                    long duration = (endTime - startTime) / 1_000;

                    if (result) {
                        log.info("[Racer-{}] 🏆 WON THE RACE! Booked {} (took {} μs)",
                                threadId, hotSeat, duration);
                    } else {
                        log.info("[Racer-{}] Lost the race for {} (took {} μs)",
                                threadId, hotSeat, duration);
                    }

                    return new BookingResult(threadId, hotSeat, result, duration);
                }, executor))
                .collect(Collectors.toList());

        // Wait for the first one to complete
        CompletableFuture<Object> firstToComplete = CompletableFuture.anyOf(
                racingFutures.toArray(new CompletableFuture[0])
        );

        BookingResult winner = (BookingResult) firstToComplete.join();
        log.info("\n🎉 First thread to complete: Thread-{}", winner.threadId);

        // Wait for all to complete
        CompletableFuture.allOf(racingFutures.toArray(new CompletableFuture[0])).join();

        List<BookingResult> allResults = racingFutures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        long successCount = allResults.stream().filter(r -> r.success).count();

        log.info("\n" + "=".repeat(80));
        log.info("RACE RESULTS:");
        log.info("  Total Racers: {}", racers);
        log.info("  Winners: {}", successCount);
        log.info("  First to Finish: Thread-{}", winner.threadId);
        log.info("  Thread Safety: {}", successCount == 1 ? "✓ PASSED" : "✗ FAILED");
        log.info("=".repeat(80) + "\n");

        assertEquals(1, successCount, "Only one racer should win");
    }

    @Test
    void testCompletableFutureWithExceptionHandling() {
        log.info("\n*** TEST: CompletableFuture with Exception Handling ***\n");

        AtomicInteger attemptCount = new AtomicInteger(0);

        CompletableFuture<String> bookingWithRetry = CompletableFuture
                .supplyAsync(() -> {
                    int attempt = attemptCount.incrementAndGet();
                    log.info("[Attempt-{}] Trying to book seat A1", attempt);

                    if (attempt < 2) {
                        log.info("[Attempt-{}] Simulating failure...", attempt);
                        throw new RuntimeException("Booking service temporarily unavailable");
                    }

                    boolean result = movieTheater.bookSeat("A1");
                    log.info("[Attempt-{}] Booking result: {}", attempt, result ? "SUCCESS" : "FAILED");
                    return result ? "A1" : null;
                }, executor)
                .exceptionally(ex -> {
                    log.warn("[Exception-Handler] Caught exception: {}, retrying...", ex.getMessage());
                    return null;
                })
                .thenCompose(result -> {
                    if (result == null) {
                        log.info("[Retry-Logic] Previous attempt failed, retrying...");
                        return CompletableFuture.supplyAsync(() -> {
                            int attempt = attemptCount.incrementAndGet();
                            log.info("[Attempt-{}] Retry booking seat A1", attempt);
                            boolean success = movieTheater.bookSeat("A1");
                            log.info("[Attempt-{}] Result: {}", attempt, success ? "SUCCESS ✓" : "FAILED ✗");
                            return success ? "A1" : null;
                        }, executor);
                    }
                    return CompletableFuture.completedFuture(result);
                })
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Final-Handler] Booking failed with exception: {}", ex.getMessage());
                    } else {
                        log.info("[Final-Handler] Booking completed: {}", result != null ? "SUCCESS" : "FAILED");
                    }
                });

        String bookedSeat = bookingWithRetry.join();

        log.info("\n" + "=".repeat(80));
        log.info("EXCEPTION HANDLING RESULTS:");
        log.info("  Total Attempts: {}", attemptCount.get());
        log.info("  Final Result: {}", bookedSeat != null ? "Seat " + bookedSeat + " booked" : "Failed");
        log.info("=".repeat(80) + "\n");

        assertNotNull(bookedSeat, "Should eventually succeed after retry");
    }

    @Test
    void testCombineMultipleBookings() {
        log.info("\n*** TEST: Combine Multiple Bookings ***\n");

        CompletableFuture<Boolean> booking1 = CompletableFuture.supplyAsync(() -> {
            log.info("[Booking-1] Attempting seat A1");
            boolean result = movieTheater.bookSeat("A1");
            log.info("[Booking-1] Result: {}", result ? "SUCCESS ✓" : "FAILED ✗");
            return result;
        }, executor);

        CompletableFuture<Boolean> booking2 = CompletableFuture.supplyAsync(() -> {
            log.info("[Booking-2] Attempting seat A2");
            boolean result = movieTheater.bookSeat("A2");
            log.info("[Booking-2] Result: {}", result ? "SUCCESS ✓" : "FAILED ✗");
            return result;
        }, executor);

        CompletableFuture<Boolean> booking3 = CompletableFuture.supplyAsync(() -> {
            log.info("[Booking-3] Attempting seat A3");
            boolean result = movieTheater.bookSeat("A3");
            log.info("[Booking-3] Result: {}", result ? "SUCCESS ✓" : "FAILED ✗");
            return result;
        }, executor);

        // Combine all results
        CompletableFuture<List<Boolean>> allBookings = CompletableFuture.allOf(booking1, booking2, booking3)
                .thenApply(v -> List.of(
                        booking1.join(),
                        booking2.join(),
                        booking3.join()
                ));

        List<Boolean> results = allBookings.join();
        long successCount = results.stream().filter(Boolean::booleanValue).count();

        log.info("\n" + "=".repeat(80));
        log.info("COMBINED BOOKING RESULTS:");
        log.info("  Total Bookings: {}", results.size());
        log.info("  Successful: {}", successCount);
        log.info("  Individual Results: {}", results);
        log.info("=".repeat(80) + "\n");

        assertEquals(3, successCount, "All three different seats should be booked");
    }

    // Helper class to store booking results
    static class BookingResult {
        final int threadId;
        final String seatNumber;
        final boolean success;
        final long durationMicros;

        BookingResult(int threadId, String seatNumber, boolean success, long durationMicros) {
            this.threadId = threadId;
            this.seatNumber = seatNumber;
            this.success = success;
            this.durationMicros = durationMicros;
        }

        @Override
        public String toString() {
            return String.format("BookingResult{thread=%d, seat=%s, success=%s, duration=%dμs}",
                    threadId, seatNumber, success, durationMicros);
        }
    }
}