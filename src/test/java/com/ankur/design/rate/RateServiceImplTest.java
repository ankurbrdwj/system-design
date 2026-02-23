package com.ankur.design.rate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RateServiceImplTest {

    private RateApi rateApi;
    private RateServiceImpl rateService;

    @BeforeEach
    void setUp() {
        rateApi = Mockito.mock(RateApi.class);
        rateService = new RateServiceImpl(rateApi);
    }

    /**
     * TEST 1: Verify that out of 100 threads requesting the SAME key,
     * only ONE thread fetches from API (others get cached value)
     */
    @Test
    void testOnlyOneThreadFetchesForSameKey() throws InterruptedException {
        // Given: Mock API to return a fixed rate
        when(rateApi.fetchRateFromProvider("USD", "EUR"))
                .thenReturn(1.2);

        int numThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 100 threads request the SAME currency pair
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    double rate = rateService.getRate("USD", "EUR");
                    assertEquals(1.2, rate, 0.001);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: Verify API was called exactly ONCE (not 100 times!)
        verify(rateApi, times(1)).fetchRateFromProvider("USD", "EUR");

        // And all 100 threads got the value
        assertEquals(numThreads, successCount.get(),
                "All threads should successfully get the rate");

        System.out.println("✓ TEST 1 PASSED: 100 threads, only 1 API call!");
    }

    /**
     * TEST 2: Verify that after TTL expires (60 seconds),
     * a new value is fetched from API
     */
    @Test
    void testCacheExpiresAfterSixtySeconds() throws InterruptedException {
        // Given: Mock API to return different rates over time
        when(rateApi.fetchRateFromProvider("USD", "GBP"))
                .thenReturn(0.75)  // First call
                .thenReturn(0.80); // Second call after expiry

        // When: First call at T=0
        double rate1 = rateService.getRate("USD", "GBP");
        assertEquals(0.75, rate1, 0.001, "First call should return 0.75");

        // Then: Verify API was called once
        verify(rateApi, times(1)).fetchRateFromProvider("USD", "GBP");

        // When: Call again immediately (within TTL)
        double rate2 = rateService.getRate("USD", "GBP");
        assertEquals(0.75, rate2, 0.001, "Should return cached value");

        // Then: API should still be called only once (cached)
        verify(rateApi, times(1)).fetchRateFromProvider("USD", "GBP");

        System.out.println("  Waiting 61 seconds for cache to expire...");

        // When: Wait for cache to expire (61 seconds)
        Thread.sleep(61_000);

        // And: Call again after expiry
        double rate3 = rateService.getRate("USD", "GBP");
        assertEquals(0.80, rate3, 0.001, "Should fetch new value after TTL");

        // Then: API should be called again (2nd time)
        verify(rateApi, times(2)).fetchRateFromProvider("USD", "GBP");

        System.out.println("✓ TEST 2 PASSED: Cache expired after 60 seconds, new value fetched!");
    }
}