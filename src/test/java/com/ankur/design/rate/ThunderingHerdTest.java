package com.ankur.design.rate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests the "Thundering Herd" problem on cache expiry
 *
 * Scenario: 100 threads all detect cache expiry at the SAME time
 * Expected: Only 1 thread should call the API (others wait and get the fresh cached value)
 */
class ThunderingHerdTest {

    private RateApi rateApi;
    private RateServiceImpl rateService;

    @BeforeEach
    void setUp() {
        rateApi = Mockito.mock(RateApi.class);
        rateService = new RateServiceImpl(rateApi);
    }

    @Test
    void testOnlyOneThreadCallsApiOnExpiry() throws InterruptedException {
        // Given: Mock API to return different rates on each call
        AtomicInteger callCount = new AtomicInteger(0);
        when(rateApi.fetchRateFromProvider("USD", "EUR"))
                .thenAnswer(invocation -> {
                    int count = callCount.incrementAndGet();
                    System.out.println("  🔴 API CALLED - Call #" + count);
                    Thread.sleep(100); // Simulate slow API
                    return 1.0 + (count * 0.1); // 1.1, 1.2, 1.3, etc.
                });

        int numThreads = 100;

        // STEP 1: Prime the cache with initial value
        System.out.println("STEP 1: Initial cache population");
        double initialRate = rateService.getRate("USD", "EUR");
        assertEquals(1.1, initialRate, 0.001);
        System.out.println("  ✓ Cache populated with rate: " + initialRate);
        System.out.println("  API call count: " + callCount.get());
        System.out.println();

        // STEP 2: Wait for cache to expire (61 seconds)
        System.out.println("STEP 2: Waiting 61 seconds for cache to expire...");
        Thread.sleep(61_000);
        System.out.println("  ✓ Cache is now EXPIRED");
        System.out.println();

        // STEP 3: All 100 threads detect expiry SIMULTANEOUSLY
        System.out.println("STEP 3: 100 threads all detect expiry at the SAME TIME");
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Use CyclicBarrier to ensure all threads start AT THE EXACT SAME TIME
        CyclicBarrier startGate = new CyclicBarrier(numThreads);
        CountDownLatch finishLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Wait at the barrier until all 100 threads are ready
                    startGate.await();

                    // NOW all 100 threads execute this at the SAME TIME
                    System.out.println("Thread-" + threadId + " detected expiry, calling getRate()");
                    double rate = rateService.getRate("USD", "EUR");
                    successCount.incrementAndGet();

                    // All threads should get the SAME fresh rate
                    assertEquals(1.2, rate, 0.001,
                        "Thread-" + threadId + " should get fresh rate after expiry");

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        System.out.println();

        // STEP 4: Verify results
        System.out.println("STEP 4: Verification");
        System.out.println("  Threads that called getRate(): " + successCount.get());
        System.out.println("  Total API calls: " + callCount.get());
        System.out.println();

        // Assert: API should be called exactly TWICE total
        // - Once for initial population (step 1)
        // - Once for refresh after expiry (step 3) - despite 100 threads!
        verify(rateApi, times(2)).fetchRateFromProvider("USD", "EUR");
        assertEquals(2, callCount.get(),
            "API should be called exactly twice: initial + one refresh (not 101!)");

        System.out.println("✅ SUCCESS: Thundering herd prevented!");
        System.out.println("   100 threads detected expiry simultaneously");
        System.out.println("   Only 1 thread called the API");
        System.out.println("   Other 99 threads got the fresh cached value");
        System.out.println();
        System.out.println("How? compute() provides:");
        System.out.println("  1. LOCK-PER-KEY: Only 1 thread can execute compute lambda at a time");
        System.out.println("  2. DOUBLE-CHECK: Inside lambda, thread checks if another thread already refreshed");
    }
}