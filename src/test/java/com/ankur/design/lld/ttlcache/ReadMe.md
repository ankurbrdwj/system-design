# LLD Interview: TTL Cache (40 min)

---

## Problem Statement

Design a thread-safe in-memory cache where each entry expires after a given TTL (time-to-live).

---

## API to implement

```java
public class TtlCache<K, V> {
    void   put(K key, V value, long ttlSeconds);  // store with expiry
    V      get(K key);                            // null if expired or absent
    void   remove(K key);                         // explicit eviction
    int    size();                                // count of non-expired entries
}
```

---

## Constraints

- Thread-safe — multiple threads calling `put`/`get`/`remove` concurrently
- Expired entries must not be returned by `get`
- No external libraries (no Guava Cache, Caffeine etc.)
- No Spring, no frameworks

---

## Interview Stages (40 min)

### Stage 1 — Data model (5 min)
What do you store per entry?  
*Hint: value alone is not enough — you need to know when it dies.*

### Stage 2 — Single-threaded impl (10 min)
Implement all four methods assuming single-threaded use.  
`get` must return `null` for expired entries without throwing.

### Stage 3 — Thread safety (10 min)
Two threads call `put` and `get` on the same key simultaneously.  
What breaks? Fix it. What lock granularity do you choose and why?

### Stage 4 — Expiry strategy discussion (10 min)
Your current `get` only evicts lazily (on access).  
Interviewer asks: *"The cache fills up with expired entries nobody is reading — what do you do?"*  
Design a background cleanup thread. What are the risks?

### Stage 5 — Edge cases (5 min)
- `put` with `ttlSeconds <= 0` — what should happen?
- `get` on a key that is expiring RIGHT NOW (race between check and return)
- `size()` — does it count expired-but-not-yet-evicted entries?

---

## What the interviewer is looking for

| Stage | Looking for |
|---|---|
| Data model | `ConcurrentHashMap<K, Entry<V>>` where `Entry` holds value + `expiryAt` (epoch ms) |
| Single-threaded | Correct `System.currentTimeMillis()` comparison in `get` |
| Thread safety | Per-entry lock OR `ConcurrentHashMap.compute()` for atomic read-modify |
| Background cleanup | `ScheduledExecutorService`, shutdown hook, not holding lock during full scan |
| Edge cases | `ttl <= 0` throws or treats as immediate expiry; `size()` filters expired entries |

---

## Common mistakes

- Storing absolute TTL seconds instead of `expiryAt = System.currentTimeMillis() + ttlSeconds * 1000`
- Global lock on the whole map instead of per-entry (kills concurrency)
- Background thread holds the map lock while scanning (blocks all `get`/`put`)
- `size()` returns raw map size including expired entries
- Not shutting down the background thread — leaked thread in tests

---

## Skeleton

```java
public class TtlCache<K, V> {

    private static class Entry<V> {
        final V value;
        final long expiryAt;  // System.currentTimeMillis() + ttl

        Entry(V value, long ttlSeconds) {
            this.value    = value;
            this.expiryAt = System.currentTimeMillis() + ttlSeconds * 1000;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryAt;
        }
    }

    private final ConcurrentHashMap<K, Entry<V>> map = new ConcurrentHashMap<>();

    public void   put(K key, V value, long ttlSeconds) { /* TODO */ }
    public V      get(K key)                           { /* TODO */ }
    public void   remove(K key)                        { /* TODO */ }
    public int    size()                               { /* TODO */ }
}
```

---

## Follow-up questions (if time allows)

- How would you add `getOrCompute(key, loader, ttl)` — load-on-miss atomically?
- What if two threads call `get` for the same missing key simultaneously — how do you avoid a cache stampede?
- How would you make TTL per-access (sliding expiry) vs per-write (fixed expiry)?