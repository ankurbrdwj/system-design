package com.ankur.design.lld.ttlcache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class TestTTLCache {

    TtlCache<String, String> cache;

    @BeforeEach
    void setUp() {
        cache = new TtlCache<>();
    }

    @AfterEach
    void tearDown() {
        cache.shutdown();
    }

    // ── functional ───────────────────────────────────────────────────

    @Test
    void put_thenGet_returnsValue() {
        cache.put("key", "value", 10);
        assertEquals("value", cache.get("key"));
    }

    @Test
    void get_unknownKey_returnsNull() {
        assertNull(cache.get("missing"));
    }

    @Test
    void get_afterExpiry_returnsNull() throws InterruptedException {
        cache.put("otp", "1234", 1);
        Thread.sleep(1100);
        assertNull(cache.get("otp"));
    }

    @Test
    void get_beforeExpiry_returnsValue() throws InterruptedException {
        cache.put("session", "user", 2);
        Thread.sleep(500);
        assertEquals("user", cache.get("session"));
    }

    @Test
    void remove_thenGet_returnsNull() {
        cache.put("key", "value", 10);
        cache.remove("key");
        assertNull(cache.get("key"));
    }

    @Test
    void size_excludesExpiredEntries() throws InterruptedException {
        cache.put("alive", "yes",  10);
        cache.put("dead",  "no",    1);
        Thread.sleep(1100);
        assertEquals(1, cache.size());
    }

    @Test
    void put_withZeroTtl_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> cache.put("k", "v", 0));
    }

    @Test
    void put_sameKey_overwritesPreviousValue() {
        cache.put("key", "first",  10);
        cache.put("key", "second", 10);
        assertEquals("second", cache.get("key"));
    }

    // ── concurrency ──────────────────────────────────────────────────

    @Test
    void concurrentPuts_allEntriesVisible() throws InterruptedException {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int id = i;
            pool.submit(() -> {
                cache.put("key-" + id, "val-" + id, 10);
                done.countDown();
            });
        }

        done.await();
        assertEquals(10, cache.size());
        pool.shutdown();
    }
}