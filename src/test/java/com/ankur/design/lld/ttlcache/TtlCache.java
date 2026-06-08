package com.ankur.design.lld.ttlcache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TtlCache<K, V> {

    private static class Entry<V> {
        final V value;
        final long expiryAt;   // System.currentTimeMillis() + ttl — absolute epoch ms

        Entry(V value, long ttlSeconds) {
            this.value    = value;
            this.expiryAt = System.currentTimeMillis() + ttlSeconds * 1000;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryAt;
        }
    }

    private final ConcurrentHashMap<K, Entry<V>> map     = new ConcurrentHashMap<>();
    private final ScheduledExecutorService        cleaner = Executors.newSingleThreadScheduledExecutor();

    public TtlCache() {
        // background sweep every second — removes entries nobody is reading
        cleaner.scheduleAtFixedRate(this::evictExpired, 1, 1, TimeUnit.SECONDS);
    }

    // ── put ──────────────────────────────────────────────────────────
    public void put(K key, V value, long ttlSeconds) {
        if (ttlSeconds <= 0) throw new IllegalArgumentException("TTL must be > 0");
        map.put(key, new Entry<>(value, ttlSeconds));
    }

    // ── get ──────────────────────────────────────────────────────────
    // lazy eviction: expired entry found on read is removed immediately
    public V get(K key) {
        Entry<V> entry = map.get(key);
        if (entry == null)        return null;
        if (entry.isExpired()) {
            map.remove(key, entry);   // atomic: only removes if still the same entry
            return null;
        }
        return entry.value;
    }

    // ── remove ───────────────────────────────────────────────────────
    public void remove(K key) {
        map.remove(key);
    }

    // ── size ─────────────────────────────────────────────────────────
    // counts only live (non-expired) entries
    public int size() {
        return (int) map.values().stream().filter(e -> !e.isExpired()).count();
    }

    // ── background cleanup ───────────────────────────────────────────
    private void evictExpired() {
        map.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    // call in @AfterEach / shutdown hook to avoid leaked threads in tests
    public void shutdown() {
        cleaner.shutdown();
    }
}