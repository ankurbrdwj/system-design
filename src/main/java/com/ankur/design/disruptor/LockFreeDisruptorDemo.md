# LockFreeDisruptorDemo — Explained

## What it is

A self-contained benchmark that builds the core of the **LMAX Disruptor** from scratch using nothing but `AtomicLong` and a plain `long[]` array, then races it against Java's built-in `ConcurrentLinkedQueue` to show the latency difference.

---

## The problem it solves

Every time two threads need to hand data to each other, the naive answer is a `BlockingQueue` — which uses a mutex and OS-level parking. That costs **thousands of nanoseconds**. In HFT, the entire end-to-end budget is **5–20 microseconds**. A single queue hand-off cannot consume that budget.

The Disruptor pattern solves this by:

1. **Never allocating on the hot path** — slots are pre-allocated once at startup.
2. **Never locking** — coordination is done with CPU atomic instructions (CAS/store-release).
3. **Spinning, not sleeping** — threads busy-wait, which burns CPU but achieves nanosecond latency.

---

## Class structure

```
LockFreeDisruptorDemo
│
├── SpscRingBuffer          (inner static class)  ← the Disruptor core
│     ├── data[]            long[1024]             pre-allocated ring
│     ├── producerSeq       AtomicLong             tracks what was published
│     ├── consumerSeq       AtomicLong             tracks what was consumed
│     ├── publish(value)    boolean                producer API
│     └── consume()         long                   consumer API
│
└── main()
      ├── Benchmark A: SpscRingBuffer  (2M events)
      ├── Benchmark B: ConcurrentLinkedQueue (2M events)
      └── printStats()   latency percentiles
```

---

## SpscRingBuffer — line by line

### The ring array

```java
private static final int SIZE = 1 << 10;  // 1024
private static final int MASK = SIZE - 1; // 1023  (0b0000001111111111)
private final long[] data = new long[SIZE];
```

- **Power-of-2 size** means an index wrap is `seq & MASK` (bitwise AND) instead of `seq % SIZE` (division). Division is ~20–40 CPU cycles; AND is 1 cycle.
- `data[]` is allocated **once** at construction and reused forever — zero GC pressure on the hot path.

---

### The two sequence numbers

```java
private final AtomicLong producerSeq = new AtomicLong(-1);
private final AtomicLong consumerSeq = new AtomicLong(-1);
```

Both start at `-1`, meaning "nothing published / consumed yet."

| Variable | Owned by | Meaning |
|---|---|---|
| `producerSeq` | Producer thread | Last sequence number written and published |
| `consumerSeq` | Consumer thread | Last sequence number read and consumed |

> **False sharing risk**: in a real production system these two `AtomicLong`s would be separated by 56 bytes of padding so they each sit on their own 64-byte CPU cache line. When two cores write to variables on the same cache line, every write on one core invalidates the cache line on the other — this is called **false sharing** and can multiply latency by 3–5×.

---

### `publish(long value)` — the producer

```java
boolean publish(long value) {
    long nextSeq = producerSeq.get() + 1;         // (1)

    if (nextSeq - SIZE > consumerSeq.get())        // (2)
        return false;

    data[(int)(nextSeq & MASK)] = value;           // (3)

    producerSeq.lazySet(nextSeq);                  // (4)
    return true;
}
```

**(1) Claim the next sequence** — the producer increments its own sequence locally. No CAS needed here because there is only **one** producer (SPSC = Single Producer Single Consumer).

**(2) Back-pressure check** — if `nextSeq - SIZE > consumerSeq` the ring is full: the consumer has not yet freed the slot the producer wants to write into. Rather than block inside `publish()`, it returns `false` and the caller spins:

```java
while (!ring.publish(now)) Thread.onSpinWait();
```

**(3) Write the data** — the value goes into the array at `nextSeq & MASK`. This plain array write is safe because only one producer exists.

**(4) `lazySet(nextSeq)`** — this is the key instruction.

| Method | Memory fence | Cost |
|---|---|---|
| `AtomicLong.set(v)` | Full `StoreLoad` fence | ~20–40 ns |
| `AtomicLong.lazySet(v)` | `store-release` only | ~1–3 ns |

`lazySet` guarantees that **the data write in step (3) is visible to the consumer before the sequence number update** (happens-before). It does **not** guarantee immediate visibility to other cores — but the consumer is spin-reading this value, so it will see it within nanoseconds anyway. The C++ equivalent is `std::atomic::store(v, std::memory_order_release)`.

---

### `consume()` — the consumer

```java
long consume() {
    long nextConsumed = consumerSeq.get() + 1;           // (1)

    while (producerSeq.get() < nextConsumed)             // (2)
        Thread.onSpinWait();

    long value = data[(int)(nextConsumed & MASK)];       // (3)

    consumerSeq.lazySet(nextConsumed);                   // (4)
    return value;
}
```

**(1)** Compute the next sequence to consume — local arithmetic, no contention.

**(2) Spin-wait** — keep checking `producerSeq` until the producer has published this slot. `Thread.onSpinWait()` emits the x86 `PAUSE` instruction, which tells the CPU it is in a spin loop, reducing power and avoiding memory-order violation penalties.

**(3)** Read the value from the pre-allocated array.

**(4)** Advance `consumerSeq` with `lazySet` — this tells the producer the slot is now free (back-pressure release).

---

## Memory ordering summary

```
Producer                         Consumer
────────                         ────────
data[slot] = value               spin: producerSeq.get() < next?
producerSeq.lazySet(seq)  ──▶   (sees seq)
                                 value = data[slot]   ← guaranteed visible
                                 consumerSeq.lazySet(seq)
producerSeq.get()  ◀──           (sees consumerSeq, frees slot)
```

The `lazySet` on the producer side creates a **store-release** → **load-acquire** pairing. This is the same memory model as C++ `memory_order_release` / `memory_order_acquire`.

---

## The benchmark — `main()`

### SPSC Ring Buffer

```java
Thread ringConsumer = Thread.ofPlatform().name("ring-consumer").start(() -> {
    for (int i = 0; i < EVENTS; i++) {
        long publishedNs = ring.consume();              // blocks until published
        ringLatencies[i] = System.nanoTime() - publishedNs; // one-way latency
    }
});

Thread.sleep(50); // warm up consumer thread

for (int i = 0; i < EVENTS; i++) {
    long now = System.nanoTime();
    while (!ring.publish(now)) Thread.onSpinWait(); // spin on back-pressure
}
```

The **timestamp is the value itself** — the producer embeds `System.nanoTime()` and the consumer subtracts it on receipt. This measures **one-way producer-to-consumer latency**.

### ConcurrentLinkedQueue

```java
Thread clqConsumer = Thread.ofPlatform().name("clq-consumer").start(() -> {
    for (int i = 0; i < EVENTS; ) {
        Long ts = clq.poll();
        if (ts != null) { clqLatencies[i++] = System.nanoTime() - ts; }
        else Thread.onSpinWait();
    }
});

for (int i = 0; i < EVENTS; i++) {
    clq.offer(System.nanoTime()); // allocates a new Node object per call
}
```

`ConcurrentLinkedQueue` is the Michael & Scott lock-free linked-list queue. It is lock-free (no mutex) but:
- Allocates a **new `Node` object per element** — GC pressure.
- Uses full CAS (`compareAndSet`) on both head and tail pointers — more expensive than `lazySet`.
- Is **MPMC** (Multiple Producer Multiple Consumer) so it carries overhead that SPSC does not need.

---

### `printStats()` — reading the output

```java
System.out.printf("  Throughput : %,.0f M events/sec%n", count * 1e9 / totalNs / 1e6);
System.out.printf("  Avg latency: %,d ns%n", sum / count);
System.out.printf("  p50        : %,d ns%n", lats[count / 2]);
System.out.printf("  p99        : %,d ns%n", lats[(int)(count * 0.99)]);
System.out.printf("  p99.9      : %,d ns%n", lats[(int)(count * 0.999)]);
System.out.printf("  Max        : %,d ns%n", lats[count - 1]);
```

The array is sorted before statistics so percentile lookup is a direct array index — O(1).

| Metric | What it tells you |
|---|---|
| Throughput | Events per second — capacity |
| p50 (median) | Typical case |
| p99 | Worst 1 in 100 — the SLA boundary |
| p99.9 | Worst 1 in 1000 — GC / scheduling outliers |
| Max | Single worst observation — usually a GC pause |

In HFT, **p99 and p99.9 matter more than average**. A strategy that is fast on average but spikes to 1 ms at p99.9 will miss fills during market moves — exactly when you need to be fastest.

---

## Expected results

| Queue type | Throughput | p50 latency | p99 latency |
|---|---|---|---|
| SPSC Ring Buffer | ~100–200 M/sec | ~30–100 ns | ~200–500 ns |
| ConcurrentLinkedQueue | ~20–50 M/sec | ~200–500 ns | ~1–5 µs |

*Numbers depend on JVM warm-up, CPU model, and GC behaviour.*

---

## Why SPSC beats CLQ

| Property | SpscRingBuffer | ConcurrentLinkedQueue |
|---|---|---|
| Allocation after startup | Zero | 1 `Node` per message |
| Memory fence cost | `store-release` (lazySet) | Full CAS (StoreLoad) |
| Producers / Consumers | 1 / 1 | N / N |
| GC pressure | None | High |
| Cache-line behaviour | Predictable stride | Pointer chasing |
| Lock | None | None (lock-free CAS) |

---

## How this maps to real LMAX Disruptor

| This demo | Real Disruptor |
|---|---|
| `producerSeq.lazySet()` | `Sequence.set()` (same lazySet underneath) |
| `data[seq & MASK]` | `RingBuffer.get(sequence)` |
| Spin in `consume()` | `WaitStrategy` (BusySpinWaitStrategy / YieldingWaitStrategy) |
| `consumerSeq.lazySet()` | `SequenceBarrier.getCursor()` |
| Back-pressure `nextSeq - SIZE > consumerSeq` | `MultiProducerSequencer.hasAvailableCapacity()` |

The real Disruptor adds: multiple consumers, event processors, `BatchEventProcessor`, `WorkerPool`, and pluggable wait strategies. The core mechanic — pre-allocated ring + `lazySet` sequence numbers — is identical to this demo.

---

## Key takeaways

1. **`lazySet()` is not unsafe** — it provides store-release ordering, which is exactly what a producer-consumer hand-off needs. Using full `set()` wastes a `StoreLoad` fence for no benefit.
2. **Power-of-2 ring size** enables the `& MASK` trick — never use modulo on a hot path.
3. **Pre-allocation** is the single biggest GC win — allocate once at startup, reuse forever.
4. **`Thread.onSpinWait()`** emits `PAUSE` — always use it in spin loops.
5. **SPSC > CLQ for HFT** because the single-producer constraint removes all CAS contention on the write path.