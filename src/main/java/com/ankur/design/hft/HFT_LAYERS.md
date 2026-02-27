# HFT Java Techniques — Layered Reference

Every low-latency technique in this codebase, organised by the layer of the
stack it operates on. Each layer has its own latency budget and failure modes.
Understanding which layer a technique targets is how you reason about where to
invest optimisation effort.

```
┌─────────────────────────────────────────────────────┐
│  7. HARDWARE          nanoseconds — physics limit    │
├─────────────────────────────────────────────────────┤
│  6. NETWORK           microseconds — wire + stack    │
├─────────────────────────────────────────────────────┤
│  5. OPERATING SYSTEM  microseconds — kernel/sched    │
├─────────────────────────────────────────────────────┤
│  4. JVM               microseconds — GC + JIT        │
├─────────────────────────────────────────────────────┤
│  3. CONCURRENCY       microseconds — locks + CAS     │
├─────────────────────────────────────────────────────┤
│  2. DATA STRUCTURES   nanoseconds — cache locality   │
├─────────────────────────────────────────────────────┤
│  1. PROGRAMMING       nanoseconds — allocation, copy │
└─────────────────────────────────────────────────────┘
  + DATABASES    milliseconds — I/O bound
```

---

## Layer 1 — Programming Patterns

> Goal: zero allocation, zero copy, zero boxing on the hot path.
> Budget: single-digit nanoseconds per operation.

### 1.1 Hot-Path Allocation Avoidance
_Source: `hft/hotpath/HotPathAllocationDemo.java`, `coinbaseexchange.md`_

**Flyweight Pattern** — one mutable instance reset and reused per event.
```java
// BAD: allocates 1 object per event × 300k events/sec = 300k objects/sec → GC
MarketTick t = new MarketTick(); t.reset(...);

// GOOD: reset and reuse the same object — zero allocation
FLYWEIGHT_TICK.reset(sym, seq, px, qty, side);
return FLYWEIGHT_TICK;
```

**ThreadLocal Reuse** — per-thread pre-allocated instance; no lock, no contention.
```java
static final ThreadLocal<MarketTick> TICK = ThreadLocal.withInitial(MarketTick::new);
TICK.get().reset(...);   // allocates once per thread, never again
```

**Pre-Allocated Output Buffer** — caller supplies buffer; callee never returns `new`.
```java
// BAD: new Fill[] on every match — hot path!
Fill[] matchOrder(Order o) { return new Fill[n]; }

// GOOD: caller owns the buffer
void matchOrder(Order o, FillBuffer out) { out.reset(); out.addFill(...); }
```

**Primitive Fixed-Point Arithmetic** — replace `Double` with `long` scaled integers.
```java
long priceRaw = 150_500_000L;   // 150.50 × 1_000_000
// No Double object, no boxing, no GC — fits in a CPU register
```

### 1.2 Symbol-as-Long Encoding
_Source: `hft/hotpath/HotPathAllocationDemo.java`, `coinbaseexchange.md`_

Pack 8-char ASCII ticker into a single `long` — no String, no char[], no heap.
```java
static long symbolToLong(String s) {     // called ONCE at startup
    long r = 0;
    for (int i = 0; i < Math.min(s.length(), 8); i++)
        r = (r << 8) | (s.charAt(i) & 0xFF);
    return r;
}
static final long SYM_AAPL   = symbolToLong("AAPL");   // 0x000000004141504C
static final long SYM_BTCUSD = symbolToLong("BTCUSD"); // used as Map key, == comparison
```

Benefits: `==` comparison (single CPU instruction), fits in a register, stored
in `long[]` with no boxing.

### 1.3 Avoiding Hidden Allocations
_Source: `hft/profiling/AllocationProfilingDemo.java`_

| Guilty pattern | What it allocates | Fix |
|---|---|---|
| `String.split(",")` | `Pattern` + `String[]` + N `String` objects | `indexOf()` + `substring()` |
| `new ArrayList<Integer>()` | `Integer` wrapper per element | `int[]` primitive array |
| `toString()` in hot loop | `StringBuilder` + `String` | guard with `if (log.isDebug())` |
| `Arrays.copyOf()` on read | full copy every call | return read-only view / volatile ref |
| `"AAPL".equals(sym)` | no alloc but pointer chase | `symLong == SYM_AAPL` primitive compare |

### 1.4 Simple Binary Encoding (SBE)
_Source: `coinbaseexchange.md`_

Zero-allocation message serialisation — writes directly into a pre-allocated
`DirectByteBuffer` at fixed offsets (C struct semantics in Java).

```
JSON/Protobuf:  allocates String + byte[] + Map → GC → latency spike
SBE:            buffer.putLong(0, orderId)       → zero allocation, ~20ns encode
```

### 1.5 Deterministic State Machine
_Source: `hft/architecture/DeterministicExchangeSM.java`_

Every exchange action modelled as an immutable `Event`. The state machine is a
pure function: `f(state, event) → (newState, fills)`. Same event log → identical
output on any node or replay run. Enables replication and replay-based debugging.

```
PLACE_ORDER → CANCEL_ORDER → MARKET_CLOSE   ← immutable sealed events
     ↓               ↓              ↓
  OrderBook     OrderBook      ExchangeSM     ← deterministic transitions
  (match, rest)  (remove)      (close market)
```

### 1.6 CQRS — Separate Write and Read Models
_Source: `hft/architecture/` discussions_

```
COMMAND side: TreeMap<Long, Deque<Order>>       ← mutable, single writer
QUERY  side:  volatile Map<Long, Long> snapshot ← immutable, many readers

void publishSnapshot() {
    bidSnapshot = Map.copyOf(bids snapshot);  // atomic reference swap
}
Map<Long, Long> queryDepth() { return bidSnapshot; }  // zero locking
```

---

## Layer 2 — Data Structures

> Goal: O(1) or O(log N) operations with maximum cache locality.
> Budget: tens of nanoseconds per operation.

### 2.1 Red-Black Tree → `TreeMap`
_Source: `hft/orderbook/CppStlToJavaCollectionsDemo.java`, `hft/interview/Block4_AlgorithmsQuickDrill.java`_

Backs `TreeMap` and `TreeSet`. Self-balancing BST guaranteeing O(log N) worst-case
for insert, delete, and lookup. Used for order book price levels because:
- `firstKey()` = best ask (lowest price) in O(1)
- `lastKey()` = best bid (highest price) in O(1)
- Range scan (depth-of-book) in O(k) where k = levels returned

```java
TreeMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();           // ascending
TreeMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(reverseOrder()); // descending
asks.firstKey()  // best ask — O(1)
bids.firstKey()  // best bid — O(1)
asks.headMap(px) // all asks <= px — range query O(log N + k)
```

### 2.2 Skip List → `ConcurrentSkipListMap`
_Source: `hft/orderbook/LowLatencyOrderBook.java`_

Lock-free sorted map; thread-safe alternative to `TreeMap` without a global lock.
Multiple linked lists at increasing "skip" heights allow O(log N) operations with
no single bottleneck. Used when the order book must be read by many threads
simultaneously without blocking the writer.

```java
ConcurrentSkipListMap<Long, Long> bids =
    new ConcurrentSkipListMap<>(Comparator.reverseOrder());
bids.put(priceRaw, qty);       // O(log N) lock-free
bids.firstKey();               // best bid O(1)
```

### 2.3 Ring Buffer (Circular Array)
_Source: `disruptor/SpscRingBuffer.java`, `disruptor/RingBuffer.java`_

Pre-allocated fixed-size array. No `new`, no GC, zero pointer chasing.
Power-of-2 size enables bitwise-mask wrap instead of modulo (one CPU instruction).

```
slots: [0][1][2][3][4][5][6][7]   capacity=8, MASK=7
         ↑ consumer reads here
                  ↑ producer writes here
index = sequence & MASK   (not sequence % capacity — MASK is one AND, % is a divide)
```

Two-phase commit (LMAX Disruptor pattern):
```
1. claim   → producer claims a slot (AtomicLong CAS on sequence)
2. write   → producer writes data into slot
3. publish → producer commits sequence (lazySet — store-release, no StoreLoad fence)
4. consume → consumer spins until published sequence ≥ expected
```

### 2.4 Hash Map → `HashMap` / `ConcurrentHashMap`
_Source: `hft/orderbook/`, `rate/`, `multithreaded/`_

`HashMap`: array of buckets. Hash collision: chaining (linked list → red-black
tree at 8 entries, Java 8+). Load factor 0.75 → resize when 75% full (doubles
array, rehashes all — **pre-size if cardinality is known**).

`ConcurrentHashMap` internals:
- Reads: lock-free (volatile array slots)
- Writes: CAS on empty bins; `synchronized` on first node of occupied bin
- No global lock: concurrent writers on different bins never block each other
- `computeIfAbsent()`: atomic — safe for lock-per-key pattern

### 2.5 Binary Heap → `PriorityQueue`
_Source: `hft/interview/Block4_AlgorithmsQuickDrill.java`_

O(log N) insert and poll; O(1) peek at min/max. Used for best-bid/ask tracking
when cancel/update is infrequent (cancel is O(N) without an index — use `TreeMap`
instead when cancels are common).

### 2.6 LRU Cache → `LinkedHashMap` (access-order)
_Source: `hft/interview/Block1_CoreJavaConcurrency.java`, `lowlatency/InMemoryComputingDemo.java`_

```java
new LinkedHashMap<K,V>(capacity, 0.75f, true)  // accessOrder=true
// get() and put() both move entry to tail (most-recently-used)
// Override removeEldestEntry() → auto-evict head (LRU) when over capacity
```

Real use: instrument reference-data cache, market data snapshot cache.

### 2.7 ArrayDeque vs LinkedList
_Source: `hft/interview/Block4_AlgorithmsQuickDrill.java`_

Always prefer `ArrayDeque` over `LinkedList` for stack/queue:
- `LinkedList`: each node is a heap object → GC pressure, pointer chasing
- `ArrayDeque`: circular array → cache-friendly, zero node allocation, O(1) amortised both ends

### 2.8 VWAP Sliding Window
_Source: `hft/interview/Block4_AlgorithmsQuickDrill.java`_

```java
// Deque of {priceRaw, volume, timestampMs}
// Maintain running pxVol and vol sums — O(1) VWAP after each trade
void addTrade(double price, long vol) {
    window.addLast(new long[]{priceRaw, vol, now});
    runningPxVol += priceRaw * vol;   // running sum, not re-scan
    evictStale();                     // remove head entries outside window
}
double vwap() { return runningPxVol / runningVol / 1_000_000.0; }
```

---

## Layer 3 — Concurrency & Collections

> Goal: eliminate lock contention; coordinate threads with minimum overhead.
> Budget: tens to hundreds of nanoseconds.

### 3.1 Java Memory Model — Happens-Before
_Source: `hft/interview/Block1_CoreJavaConcurrency.java`_

The JMM guarantees: a **write to a volatile variable V** happens-before every
**subsequent read of V**. This is the formal basis for all lock-free patterns.

```
volatile:     guarantees VISIBILITY (flush to main memory on write,
                                     fetch from main memory on read)
              does NOT guarantee atomicity for compound ops (i++ = read+inc+write)
              correct for: single-writer flag, published reference

synchronized: guarantees BOTH visibility AND atomicity

AtomicLong:   volatile long + CAS (LOCK CMPXCHG CPU instruction)
              correct for: multi-writer counters, sequence generators
```

### 3.2 Lock-Free Atomics
_Source: `hft/orderbook/LockFreeDisruptorDemo.java`, `disruptor/`_

| Operation | Java | CPU instruction |
|---|---|---|
| Read | `AtomicLong.get()` | `MOV` with acquire fence |
| Write | `AtomicLong.set()` | `MOV` with release fence |
| Lazy write | `AtomicLong.lazySet()` | `MOV` (store-release, no StoreLoad fence) |
| Increment | `AtomicLong.incrementAndGet()` | `LOCK XADD` |
| CAS | `AtomicLong.compareAndSet()` | `LOCK CMPXCHG` |

`lazySet()` is the key LMAX Disruptor optimisation: avoids the expensive
`MFENCE`/`SFENCE` that a full `volatile` write requires. Safe when one thread
writes and another reads (SPSC).

### 3.3 LMAX Disruptor
_Source: `disruptor/SpscRingBuffer.java`, `disruptor/RingBuffer.java`, `disruptor/Readme.md`_

Ring buffer that replaces `ArrayBlockingQueue` as inter-thread message bus:

| Feature | `ArrayBlockingQueue` | Disruptor |
|---|---|---|
| Locking | `ReentrantLock` on every op | Lock-free (CAS + lazySet) |
| Allocation | `new` on every `put` | Pre-allocated fixed array |
| Multiple consumers | Compete for same item | Each consumer has own cursor |
| Throughput | ~5M msg/sec | ~100M msg/sec |

### 3.4 False Sharing Fix — Inheritance Padding
_Source: `hft/interview/Block2_PerformanceOptimisation.java`_

Two threads writing adjacent `volatile long` fields on the same 64-byte cache
line cause constant cache-line invalidation (MESI protocol). Fix with inherited
56-byte padding (plain fields can be optimised away by JIT; inherited fields cannot):

```java
abstract class Pad1          { long p1,p2,p3,p4,p5,p6,p7; }     // 56 bytes
abstract class CounterA extends Pad1 { volatile long cA = 0; }   // own cache line
abstract class Pad2   extends CounterA { long p8,p9,p10,p11,p12,p13,p14; }
class Counters extends Pad2  { volatile long cB = 0; }            // own cache line
```

### 3.5 ExecutorService Patterns
_Source: `hft/interview/Block1_CoreJavaConcurrency.java`_

**Critical trap**: `Executors.newFixedThreadPool(n)` uses an **unbounded
`LinkedBlockingQueue`** — `submit()` never blocks, causing OOM under sustained load.

```java
// PRODUCTION-SAFE bounded pool with back-pressure:
new ThreadPoolExecutor(n, n, 0L, SECONDS,
    new ArrayBlockingQueue<>(1000),          // bounded queue
    new ThreadPoolExecutor.CallerRunsPolicy() // back-pressure: caller's thread runs task
);
```

`ForkJoinPool` (work-stealing): idle threads steal tasks from busy threads' queues.
Best for recursive decomposition (parallel stream, CompletableFuture default pool).

### 3.6 CompletableFuture — Multi-Venue Fan-Out
_Source: `hft/interview/Block1_CoreJavaConcurrency.java`_

```java
// Fan-out: 3 simultaneous price requests, pick best — parallel, not sequential
CompletableFuture<Double> lse    = supplyAsync(() -> fetchPrice("LSE"),    pool);
CompletableFuture<Double> nyse   = supplyAsync(() -> fetchPrice("NYSE"),   pool);
CompletableFuture<Double> nasdaq = supplyAsync(() -> fetchPrice("NASDAQ"), pool);

CompletableFuture.allOf(lse, nyse, nasdaq)
    .thenApply(v -> Math.min(lse.join(), Math.min(nyse.join(), nasdaq.join())));
```

`thenApply` — transform on same thread (no new thread).
`thenApplyAsync` — transform on pool thread.
`thenCompose` — chain a Future returning another Future (flatMap).
`anyOf` — return first completed (fastest venue wins).

### 3.7 ReadWriteLock — Market Data Cache
_Source: `hft/interview/Block1_CoreJavaConcurrency.java`_

Many strategy threads read; one feed thread writes. `ReadWriteLock` allows
concurrent readers with exclusive writer:
```java
ReadWriteLock rw = new ReentrantReadWriteLock();
// Writer (feed thread): exclusive
rw.writeLock().lock(); try { prices.put(sym, px); } finally { rw.writeLock().unlock(); }
// Reader (strategy threads): concurrent — no blocking between readers
rw.readLock().lock();  try { return prices.get(sym); } finally { rw.readLock().unlock(); }
```

### 3.8 Lock-Per-Key Pattern
_Source: `rate/`, `hft/hotpath/HotPathAllocationDemo.java`_

One lock per symbol rather than one global lock. Threads for different symbols
never block each other:
```java
ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
Object lock = locks.computeIfAbsent(symbol, k -> new Object()); // atomic
synchronized (lock) { positions.merge(symbol, delta, Long::sum); }
```

### 3.9 CountDownLatch vs CyclicBarrier
_Source: `hft/interview/Block1_CoreJavaConcurrency.java`_

| | `CountDownLatch` | `CyclicBarrier` |
|---|---|---|
| Resettable | No — one-shot | Yes — reusable |
| Use case | Wait for N services to start | Sync N threads between phases |
| Trading use | Wait for all venue connections | End-of-round sync in market simulation |

### 3.10 Semaphore — Exchange Throttle
_Source: `hft/interview/Block1_CoreJavaConcurrency.java`_

Limits concurrent exchange connections or in-flight orders to comply with
exchange throttle rules. `acquire()` blocks when all permits taken; `release()`
returns a permit.

---

## Layer 4 — JVM

> Goal: eliminate GC pauses; ensure JIT has compiled hot paths before trading.
> Budget: sub-millisecond GC; zero-pause during market hours.

### 4.1 GC Selection
_Source: `hft/interview/Block2_PerformanceOptimisation.java`, `coinbaseexchange.md`_

| GC | Pause | Java version | Use case |
|---|---|---|---|
| G1GC | 5–20ms | 9+ (default) | General trading apps |
| ZGC | <1ms | 15+ production | HFT — sub-ms SLA |
| Shenandoah | <1ms | 12+ | HFT (RedHat-backed) |
| Azul Zing | 0ms | Commercial | Coinbase, ultra-HFT |

ZGC and Shenandoah perform collection **concurrently** — the JVM never fully
stops while GC runs. Azul Zing (ReadyNow!) additionally saves the JIT profile
from a previous run and replays it at startup, eliminating warm-up spikes.

### 4.2 Critical JVM Flags
_Source: `hft/interview/Block2_PerformanceOptimisation.java`, `hft/profiling/FlameGraphPatternDemo.java`_

```bash
# Heap — prevent resize pauses (always equal min/max)
-Xms4g -Xmx4g

# GC
-XX:+UseZGC                        # sub-millisecond GC
-XX:+DisableExplicitGC             # prevent System.gc() calls from libraries

# Startup — eliminate page-fault noise during trading
-XX:+AlwaysPreTouch                # fault all heap pages at JVM start

# JIT — preserve stack frames for profiling
-XX:+PreserveFramePointer          # keeps rbp register intact
-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints

# Safe points
-XX:+UseCountedLoopSafepoints      # add safe points in tight loops (fixes profiling bias)

# Logging
-Xlog:gc*:file=gc.log              # structured GC log (replaces -verbose:gc)
```

### 4.3 JIT Warm-Up
_Source: `hft/orderbook/GCMitigationDemo.java`, `hft/profiling/`_

HotSpot compiles a method after **~2,000 invocations (C1)** and then
**~10,000 invocations (C2 — optimising compiler)**. Before warm-up, trading runs
at interpreted speed with unpredictable latency spikes.

```java
// Run all hot paths before market open
void warmUp(int iterations) {
    for (int i = 0; i < iterations; i++) {
        processOrder(pool.acquire().reset(...));  // forces JIT to compile processOrder
    }
    System.gc();   // collect warm-up garbage before production begins
}
```

### 4.4 C1 vs C2 Compiler
_Source: interview discussions_

```
C1 (client): fast compile, basic optimisations → decent native code quickly
C2 (server): slow compile, aggressive optimisations → near-C++ speed
  - method inlining across call boundaries
  - loop unrolling (removes loop overhead for small N)
  - escape analysis → eliminates allocations whose scope doesn't exceed method
  - auto-vectorisation (SIMD) for simple numeric loops
  - on-stack replacement (OSR): replaces interpreted loop mid-execution
```

### 4.5 Safe-Point Bias in Profiling
_Source: `hft/profiling/SafePointBiasDemo.java`_

Traditional profilers (`Thread.getAllStackTraces()`) only capture stack traces
at JVM safe points. C2-compiled tight loops have **no safe points** → the
profiler never sees time spent in them → false zero hotspot reading.

Fix: **async-profiler** uses `SIGPROF` (POSIX signal) to interrupt the thread
at any instruction — no safe-point dependency.

### 4.6 Escape Analysis (Why Naive Benchmarks Lie)
_Source: `hft/hotpath/HotPathAllocationDemo.java`_

C2 can prove that an object never escapes its allocating method and eliminate
the allocation entirely (stack-allocate or scalar-replace). This is why naive
`new Object()` microbenchmarks often show no GC — at scale with many live
objects, escape analysis fails and allocations do hit the heap.

### 4.7 Off-Heap Memory
_Source: `hft/orderbook/GCMitigationDemo.java`, `hft/interview/Block2_PerformanceOptimisation.java`_

`ByteBuffer.allocateDirect()` allocates outside the JVM heap in native memory.
GC never scans, moves, or pauses for it. Access via fixed-offset reads/writes
(C struct semantics).

```java
ByteBuffer buf = ByteBuffer.allocateDirect(capacity * RECORD_SIZE);
buf.putLong(base,    orderId);    // offset 0
buf.putDouble(base+8, price);    // offset 8
buf.putLong(base+16, quantity);  // offset 16
```

Production libraries: **Chronicle Map** (off-heap hash map), **Chronicle Queue**
(off-heap ring buffer), **Agrona** (off-heap collections by Real Logic/Aeron team).

---

## Layer 5 — Operating System

> Goal: eliminate scheduler jitter, system call overhead, and kernel stack costs.
> Budget: single-digit to low-microsecond kernel interaction.

### 5.1 CPU Pinning (Thread Affinity)
_Source: `coinbaseexchange.md`, `hft/architecture/aeron.md`_

Pin the hot thread to a dedicated CPU core so the OS scheduler never
context-switches it away:

```bash
taskset -c 3 java ...           # Linux: pin JVM to core 3
numactl --cpunodebind=0 java ... # pin to NUMA node 0
```

In code: JNA/JNI call to `pthread_setaffinity_np()`. Aeron's media driver
thread is pinned this way.

Complement: isolate the core from user-space scheduling:
```bash
# /etc/default/grub: GRUB_CMDLINE_LINUX="isolcpus=2,3 nohz_full=2,3 rcu_nocbs=2,3"
```

### 5.2 NUMA Awareness
_Source: `lowlatency/NumaNodeOptimizationDemo.java`_

On multi-socket servers, memory access across QPI bus costs 2–3× a local access
(~80ns local vs ~150ns remote). Partition data by NUMA node:

```
Symbol hash → NUMA node assignment
Thread for symbol → pinned to core on same NUMA node as data
Result: all hot reads are local → no cross-socket traffic
```

JVM flag: `-XX:+UseNUMA` enables NUMA-aware heap allocation (each thread
allocates from the NUMA-local memory bank).

### 5.3 Huge Pages
_Source: `hft/architecture/aeron.md`_

Default page size 4KB → large heap requires many TLB entries → TLB misses stall
the CPU waiting for page-table walks. Huge pages (2MB) reduce TLB pressure:

```bash
-XX:+UseTransparentHugePages          # Linux transparent huge pages
echo always > /sys/kernel/mm/transparent_hugepage/enabled
```

### 5.4 Busy-Spin vs Blocking I/O
_Source: `hft/orderbook/LockFreeDisruptorDemo.java`, `hft/architecture/aeron.md`_

```
Blocking I/O:  thread calls recv() → OS parks thread → NIC interrupt → OS wakes thread
               Wake-up latency: 10–100µs (OS scheduler granularity)

Busy-spin:     thread polls memory/NIC in tight loop — never sleeps
               Latency: 0µs scheduler delay (reacts at memory-read speed)
               Cost: 100% of one dedicated CPU core
```

`Thread.onSpinWait()` emits the x86 `PAUSE` instruction inside spin loops —
signals to the CPU that this is a spin-wait, reducing pipeline stalls and
power consumption without yielding the core.

### 5.5 Interrupt Affinity
_Source: `coinbaseexchange.md`_

NIC hardware interrupts (IRQs) are handled by a CPU core. If the same core also
runs your hot thread, interrupt handling pauses your thread:

```bash
cat /proc/interrupts | grep eth0               # find NIC IRQ number
echo 2 > /proc/irq/<N>/smp_affinity_list       # pin IRQ to core 2
# Keep core 3 for your hot thread — no NIC interrupts on core 3
```

### 5.6 AlwaysPreTouch
_Source: JVM flags section_

Without this flag, the OS uses lazy page allocation. The first access to each
4KB page triggers a **page fault** (OS allocates physical page) — each fault
costs ~1µs. During a busy trading session this appears as random latency spikes.

`-XX:+AlwaysPreTouch` faults all heap pages at JVM startup, paying the cost
once before market open.

---

## Layer 6 — Network

> Goal: eliminate kernel networking stack overhead; achieve wire-speed latency.
> Budget: hundreds of nanoseconds to low microseconds.

### 6.1 Kernel Bypass
_Source: `hft/architecture/aeron.md`, `coinbaseexchange.md`, `hft/architecture/architecture.md`_

Standard kernel networking path adds 5–50µs per message. Two bypass approaches:

**DPDK** (Data Plane Development Kit) — user-space NIC driver:
```
Application → polls NIC ring buffer directly (no syscall, no kernel)
NIC → DMA writes into application memory
Latency: ~500ns vs ~5µs for kernel path
Used by: Coinbase on AWS
```

**OpenOnload (Solarflare)** — kernel bypass for specific NICs:
```
Application → socket API unchanged
Solarflare NIC intercepts socket calls → bypasses kernel
Used by: data-centre HFT setups
```

**RDMA** (Remote Direct Memory Access) — NIC writes directly into remote server
RAM, zero CPU involvement on either side. Latency: ~1µs cross-machine.

### 6.2 Aeron — Reliable Low-Latency Transport
_Source: `hft/architecture/aeron.md`, `lowlatency/AeronSharedMemoryDemo.java`_

Aeron's latency budget (vs alternatives):

| Transport | p50 latency | p99 latency |
|---|---|---|
| Kafka | 1–5ms | 10–50ms |
| ZeroMQ | 50–200µs | 2ms |
| **Aeron IPC** | **<1µs** | **<5µs** |
| **Aeron UDP** | **1–3µs** | **30µs** |
| Disruptor | <100ns | <500ns |

Key techniques inside Aeron:
- **Lock-free ring buffer** backed by `MappedByteBuffer` (`/dev/shm`)
- **Reliable UDP** with selective NAK (no TCP head-of-line blocking)
- **Busy-spin polling** (no `recv()` blocking)
- **Zero-copy** — producer writes once; consumer reads from same memory pages
- **CPU pinning** of the media driver thread
- **SBE serialisation** — zero allocation encoding/decoding

When to use each:
```
Same JVM thread-to-thread:       LMAX Disruptor
Same machine process-to-process: Aeron IPC   (aeron:ipc)
Cross-machine LAN:               Aeron UDP   (aeron:udp?endpoint=...)
Durable audit / cross-DC:        Kafka
```

### 6.3 Aeron Cluster — Raft Consensus
_Source: `hft/architecture/aeron.md`, `coinbaseexchange.md`_

Aeron Cluster layers a **Raft consensus log** on top of Aeron transport, giving
a replicated deterministic state machine with sub-200ms leader failover:

```
Client → Leader (via Aeron UDP) → appends to replicated log
Leader waits for quorum (2-of-3) → commits → processes in state machine
Follower A applies same log → identical state
Follower B applies same log → identical state
Leader fails → Raft elects new leader in <200ms → trading resumes
```

### 6.4 Java NIO — Non-Blocking I/O
_Source: `hft/trading/JavaNioTcpDemo.java`_

```java
Selector sel = Selector.open();
ServerSocketChannel ssc = ServerSocketChannel.open();
ssc.configureBlocking(false);
ssc.register(sel, SelectionKey.OP_ACCEPT);   // epoll_ctl equivalent

while (true) {
    sel.select(5);                           // epoll_wait equivalent
    for (SelectionKey key : sel.selectedKeys()) {
        if (key.isAcceptable()) accept(key);
        if (key.isReadable())   read(key);
    }
}
```

Critical socket option for low latency:
```java
channel.setOption(StandardSocketOptions.TCP_NODELAY, true); // disable Nagle
channel.setOption(StandardSocketOptions.SO_RCVBUF, 1 << 20); // 1MB recv buffer
```

### 6.5 Reliable UDP vs TCP
_Source: `hft/architecture/aeron.md`_

TCP latency problems Aeron avoids:

| TCP behaviour | Latency penalty | Aeron approach |
|---|---|---|
| Nagle's algorithm | batches small packets | UDP — no Nagle |
| ACK delay (40ms) | receiver waits before ACK | NAK-only (no ACK) |
| Head-of-line blocking | one loss pauses all | selective retransmit only missing |
| Congestion control | throttles on estimated loss | application-controlled rate |

### 6.6 Multicast Market Data
_Source: `hft/architecture/architecture.md`_

Exchange market data is broadcast via **UDP multicast** — one transmission
reaches all subscribers simultaneously (no N unicast copies). Combined with
kernel-bypass NIC, this is how market data reaches the feed handler in
nanoseconds after leaving the exchange matching engine.

---

## Layer 7 — Hardware

> Goal: exploit CPU micro-architecture for maximum instruction throughput.
> Budget: single to tens of nanoseconds per CPU operation.

### 7.1 CPU Cache Hierarchy
_Source: `hft/profiling/CacheMissDemo.java`, `hft/orderbook/CacheMissDemo.java`_

```
L1 cache:  ~32KB,  4 cycles   (~1ns)   — per core
L2 cache:  ~256KB, 12 cycles  (~4ns)   — per core
L3 cache:  ~8MB,   40 cycles  (~15ns)  — shared across cores
Main RAM:  ∞,      200 cycles (~70ns)  — off-chip
```

Cache **line** = 64 bytes (x86, ARM). The CPU always fetches/evicts 64 bytes at
a time, even if you access 1 byte.

**Sequential access** → hardware prefetcher detects stride → pre-fetches ahead
→ almost all accesses hit L1/L2.

**Random access** → no predictable stride → prefetcher can't help → every access
is an L3 or RAM miss → 200-cycle stall.

**Implication for order book**: `long[]` contiguous array (cache-friendly) beats
`TreeMap` of node objects (pointer chasing, cache-unfriendly) for small N.

### 7.2 SIMD / Vector Units
_Source: `hft/vectors/JavaVectorAPIDemo.java`_

Modern CPUs have dedicated vector execution units:

| ISA extension | Width | Float lanes | Used for |
|---|---|---|---|
| SSE2 | 128-bit | 4 × float32 | Baseline on all x86-64 |
| AVX2 | 256-bit | 8 × float32 | Most modern servers |
| AVX-512 | 512-bit | 16 × float32 | Xeon, recent AMD |

Java Vector API (JEP 338, JDK 16+ incubating):
```java
VectorSpecies<Float> species = FloatVector.SPECIES_MAX;  // widest your CPU supports
FloatVector va = FloatVector.fromArray(species, a, i);
FloatVector vb = FloatVector.fromArray(species, b, i);
va.fma(vb, acc).intoArray(result, i);   // VFMADD: fused multiply-add, 1 instruction
```

**FMA (Fused Multiply-Add)**: `a*b + c` in ONE instruction with single rounding
(more precise than two instructions). Used for dot products, cosine similarity,
matrix multiply in risk calculations.

**Dark silicon**: CPU cannot run all vector units simultaneously at full frequency
due to power/thermal limits (Dennard scaling failure). `SPECIES_MAX` picks the
widest unit your thermal budget allows at runtime.

### 7.3 Branch Prediction
_Source: `hft/profiling/CacheMissDemo.java`_

Modern CPUs predict branch outcomes and speculatively execute ahead. A
misprediction costs ~15 cycles (pipeline flush + refetch). Sorted data →
predictable branches. Random data → ~50% misprediction → measurable slowdown.

```java
// BRANCH-FRIENDLY: sort data so the if() is always true or always false
Arrays.sort(data);
for (int v : data) if (v > threshold) sum += v;  // branch becomes predictable
```

### 7.4 Hardware Timestamp Counter (RDTSC)
_Source: `hft/profiling/InstrumentingVsSamplingDemo.java`_

`System.nanoTime()` calls `RDTSC` (Read Time-Stamp Counter) — a single CPU
instruction returning nanosecond-resolution clock with ~10ns overhead.
Lower overhead than `System.currentTimeMillis()` (which may call the OS).

### 7.5 Intel Optane / NVMe for the Journal
_Source: `coinbaseexchange.md`_

Coinbase uses **Intel Optane NVMe** drives for the Aeron Cluster replicated log.
Optane write latency: ~10µs (vs NAND SSD ~100µs, spinning disk ~5ms). This allows
the consensus log to be durable with acceptable latency overhead.

### 7.6 Network Switch Latency
_Source: `coinbaseexchange.md`_

| Switch type | Forwarding latency |
|---|---|
| Data-centre cut-through switch | 350ns |
| AWS store-and-forward | 5–50µs |
| AWS inter-AZ | ~1–2ms |

Coinbase colocates matching engine and customers in the **same rack / same AZ**
to eliminate cross-AZ latency. Cut-through switches forward the packet before
fully receiving it (start forwarding after reading the destination MAC address).

---

## Databases in HFT

> Databases are the cold path. The hot path never touches a database.
> Budget: milliseconds (acceptable only for non-latency-sensitive flows).

### Primary Rule: Keep the Database Off the Hot Path

```
HOT PATH  (order → match → fill):  in-memory state machine only
                                    TreeMap order book
                                    AtomicLong sequence
                                    → ~1µs latency

COLD PATH (audit → reporting → settlement):
                                    append to Kafka topic  → async
                                    Kafka consumer writes to DB
                                    → milliseconds, but hot path never waits
```

### In-Memory Databases

| DB | Technique | Use in trading |
|---|---|---|
| **Redis** | Hash map in RAM + optional persistence | Session state, reference data cache |
| **Hazelcast** | Distributed in-memory map | Shared position cache across OMS nodes |
| **Aerospike** | Hybrid RAM + SSD, microsecond reads | Real-time position/risk lookup |
| **TimeScaleDB** | PostgreSQL + time-series extension | Post-trade analytics, VWAP history |

### Chronicle Map / Chronicle Queue
Off-heap Java data structures (zero GC):
- **Chronicle Map**: off-heap `ConcurrentHashMap` — O(1) get/put with no GC pause
- **Chronicle Queue**: off-heap persistent ring buffer — used as the trade journal;
  consumers replay from any offset (like Kafka but without a broker process)

### Kafka — The Audit Log
_Source: `hft/interview/Block3_SystemDesignTrading.java`_

Kafka is **not** on the hot path. Used for:
- **Trade audit log**: every fill appended as an immutable Kafka record
- **Market data replay**: record raw feed → replay for backtesting
- **Settlement pipeline**: fills flow to risk, clearing, and settlement systems

Key configuration for ordered processing:
```
Partition by instrument symbol → all events for BTC/USD land on the same partition
→ single consumer sees them in order
→ matches how the matching engine processes them (one thread per symbol)
```

---

## Quick-Reference: Latency Numbers Every HFT Engineer Knows

```
L1 cache access:          ~1ns
L2 cache access:          ~4ns
L3 cache access:          ~15ns
Main memory (DRAM):       ~70ns
AtomicLong CAS:           ~10ns
Thread.onSpinWait():      ~1ns  (PAUSE instruction)
Context switch:           ~1–10µs
System call (syscall):    ~500ns–5µs
Java lock (uncontended):  ~20ns
Java lock (contended):    ~1–10µs
GC pause (ZGC):           <1ms
GC pause (G1GC):          5–20ms
Aeron IPC (same machine): ~1µs
Aeron UDP (10GbE LAN):    ~3µs
Kafka (local):            ~1ms
NVMe SSD write:           ~10µs
Data-centre switch:       ~350ns
AWS store-and-forward:    5–50µs
```

---

## Codebase Map — File → Layer

| File | Primary Layer |
|---|---|
| `hft/hotpath/HotPathAllocationDemo.java` | L1 Programming |
| `hft/architecture/DeterministicExchangeSM.java` | L1 Programming |
| `hft/orderbook/CppStlToJavaCollectionsDemo.java` | L2 Data Structures |
| `hft/orderbook/LowLatencyOrderBook.java` | L2 Data Structures |
| `hft/interview/Block4_AlgorithmsQuickDrill.java` | L2 Data Structures |
| `disruptor/SpscRingBuffer.java` | L2 + L3 |
| `disruptor/RingBuffer.java` | L2 + L3 |
| `hft/orderbook/LockFreeDisruptorDemo.java` | L3 Concurrency |
| `hft/interview/Block1_CoreJavaConcurrency.java` | L3 Concurrency |
| `multithreaded/` | L3 Concurrency |
| `hft/orderbook/GCMitigationDemo.java` | L4 JVM |
| `hft/interview/Block2_PerformanceOptimisation.java` | L4 JVM |
| `hft/profiling/` (all 9 files) | L4 JVM |
| `hft/optimizingjvm/bookSummary.md` | L4 JVM |
| `lowlatency/NumaNodeOptimizationDemo.java` | L5 OS |
| `lowlatency/HighAvailabilityDemo.java` | L5 + L6 |
| `hft/trading/JavaNioTcpDemo.java` | L6 Network |
| `hft/orderbook/AeronSharedMemoryDemo.java` | L6 Network |
| `hft/architecture/aeron.md` | L6 Network |
| `hft/vectors/JavaVectorAPIDemo.java` | L7 Hardware |
| `hft/profiling/CacheMissDemo.java` | L7 Hardware |
| `hft/architecture/coinbaseexchange.md` | All layers |
| `hft/interview/Block3_SystemDesignTrading.java` | All layers |

---

---

# Extended Reference: Extreme Low-Latency Hardware & Kernel Techniques

The sections below cover the frontier techniques used in co-location HFT, derivatives
exchanges, and market-making firms targeting tick-to-trade latencies under 1 microsecond.

---

## FPGA in HFT — Field-Programmable Gate Arrays

### What FPGAs Do

An FPGA is a chip whose digital logic circuits can be reprogrammed after manufacture.
Unlike a CPU (sequential instructions) or GPU (SIMT parallel), an FPGA implements
**custom hardware pipelines** — computations happen simultaneously in silicon, not
sequentially in software.

```
CPU flow (sequential):
  recv packet → decode → risk check → order decision → encode → send
  Each step waits for the previous.  Latency: 5–50µs (software stack overhead)

FPGA flow (pipelined):
  recv packet ─────────────────────────────────────────────────┐
  parse FIX/ITCH ──────────────────────────────────────────────┤  all happen
  risk limit check ────────────────────────────────────────────┤  simultaneously
  order decision logic ────────────────────────────────────────┤  in dedicated
  encode response ─────────────────────────────────────────────┤  silicon gates
  transmit ────────────────────────────────────────────────────┘
  Latency: 100–300ns tick-to-trade
```

### Hardware Vendors

| Vendor | FPGA Family | HFT Use |
|---|---|---|
| AMD/Xilinx | UltraScale+ (VU9P, VU13P) | Most common in HFT co-lo racks |
| Intel/Altera | Stratix 10, Agilex | Used by some prop desks |
| Microchip | PolarFire | Lower power, mid-range latency |

Xilinx UltraScale+ VU9P: 2.6 million logic cells, 6,840 DSP slices, integrated
100GbE MAC — used in CME co-location racks by Virtu Financial, Citadel Securities.

### Development Flow

```
Traditional RTL (VHDL/Verilog):
  Write hardware description language → simulate → synthesise → place-and-route → bitstream
  Development time: months. Required: hardware engineers.

High-Level Synthesis (HLS):
  Write C/C++ with pragmas → Vivado HLS / Vitis HLS compiles to RTL
  Development time: weeks. Required: C++ engineers.

Example HLS pragmas:
  #pragma HLS PIPELINE II=1     // initiation interval = 1 clock cycle
  #pragma HLS UNROLL factor=4   // unroll loop 4×
  #pragma HLS ARRAY_PARTITION   // partition array across BRAM banks

OpenCL / oneAPI:
  Higher-level abstraction; slower generated RTL but faster development.
```

### FPGA Tick-to-Trade Pipeline (Typical Architecture)

```
                    10/40/100 GbE
                          │
               ┌──────────▼──────────┐
               │   MAC / PCS Layer   │  (on-chip hardened block)
               └──────────┬──────────┘
                          │  raw Ethernet frames
               ┌──────────▼──────────┐
               │  UDP/IP Offload     │  IP checksum, UDP decode
               └──────────┬──────────┘
                          │  UDP payload
               ┌──────────▼──────────┐
               │  ITCH/OUCH/FIX      │  market data & order protocol decode
               │  Protocol Parser    │  pipelined: 1 symbol/clock cycle
               └──────────┬──────────┘
                          │  structured order fields
               ┌──────────▼──────────┐
               │  Order Book Update  │  price-level BRAM, FIFO queues
               └──────────┬──────────┘
                          │  signal + current BBO
               ┌──────────▼──────────┐
               │  Trading Strategy   │  configurable alpha logic
               │  (user logic)       │
               └──────────┬──────────┘
                          │  order parameters
               ┌──────────▼──────────┐
               │  Pre-Trade Risk     │  position/notional check in 1–2 cycles
               └──────────┬──────────┘
                          │  approved order
               ┌──────────▼──────────┐
               │  Order Encoder      │  OUCH/FIX encode
               └──────────┬──────────┘
                          │  UDP frame
               ┌──────────▼──────────┐
               │  MAC Tx             │  wire out
               └─────────────────────┘

Total pipeline depth: 200–350 clock cycles at 250MHz = 800ns–1.4µs
With 40GbE direct NIC-to-FPGA MAC bypass: ~100–300ns tick-to-trade
```

### Java Integration (PCIe DMA)

Java does not run on FPGAs. The typical architecture is:

```
FPGA (NIC half) ────PCIe DMA────► shared memory region (hugepages)
                                         │
                               Java application reads via
                               sun.misc.Unsafe or MappedByteBuffer
                               (zero-copy, no syscall)
```

Libraries: Xilinx XDMA driver + JNI wrapper, Solarflare OpenOnload Java bindings,
or custom JNA wrappers around the vendor SDK.

### Latency Comparison

| Approach | Tick-to-Trade | Notes |
|---|---|---|
| Java software stack | 10–50µs | Kernel, JVM, GC overhead |
| Java + kernel bypass (DPDK) | 2–5µs | Eliminates kernel, not JVM |
| Java + Aeron IPC + ZGC | 5–15µs | Best pure-Java |
| FPGA (HLS) | 300ns–1µs | Logic in silicon |
| FPGA (RTL, optimised) | 100–300ns | Gate-level optimisation |
| ASIC | 10–50ns | Custom chip, non-reprogrammable |

---

## DPDK — Data Plane Development Kit

### What DPDK Is

DPDK is an open-source set of libraries and drivers (originally by Intel, now part of
the Linux Foundation) that moves **network packet I/O from the kernel into user space**.

Normal Linux packet path:
```
NIC → kernel driver → sk_buff alloc → TCP/IP stack → socket buffer → recv() → app
      └── 2–5µs overhead from copies, context switches, interrupts
```

DPDK packet path:
```
NIC ──► user-space PMD (Poll Mode Driver) ──► app ring buffer ──► app
        └── no kernel, no interrupt, no copy — ~200–600ns
```

### Core Concepts

**Poll Mode Driver (PMD):** Instead of the NIC raising an interrupt when a packet
arrives (interrupt mode), the application **continuously polls** the NIC's Rx descriptor
ring in a tight loop — identical to Aeron's busy-spin subscriber pattern but at
the NIC hardware level.

```c
// DPDK PMD poll loop (C — DPDK is a C library)
while (running) {
    uint16_t nb_rx = rte_eth_rx_burst(port_id, queue_id, pkts_burst, MAX_BURST);
    for (int i = 0; i < nb_rx; i++) {
        process_packet(pkts_burst[i]);
        rte_pktmbuf_free(pkts_burst[i]);
    }
    // no sleep, no yield — burns 100% of one core (same as Aeron busy-spin)
}
```

**Hugepages:** DPDK requires 2MB or 1GB hugepages (vs default 4KB pages).
Fewer TLB entries needed → fewer TLB misses when walking the packet buffer pool.

```bash
# Reserve 1GB hugepages on Linux
echo 8 > /sys/kernel/mm/hugepages/hugepages-1048576kB/nr_hugepages
# Mount hugetlbfs
mount -t hugetlbfs hugetlbfs /dev/hugepages
```

**rte_mempool:** Pre-allocated pool of fixed-size packet buffers (mbufs), backed by
hugepages. Zero malloc/free on the data path — exact same concept as `ExecutionReportPool`
in Block2_PerformanceOptimisation.java but at the OS/driver level.

**rte_ring:** Lock-free SPSC/MPSC ring buffer in hugepage-backed memory. Same
algorithm as LMAX Disruptor — CAS on head/tail, power-of-2 size with bitmask wrap.

### DPDK + Java

DPDK is a C/C++ library. Java integration options:

| Method | Mechanism | Latency |
|---|---|---|
| JNI wrapper | Native method calls into DPDK | Adds ~50ns per call |
| Shared memory | DPDK writes to hugepage region; Java reads via MappedByteBuffer | ~100ns |
| Aeron + DPDK | Aeron MediaDriver uses DPDK for UDP transport | ~200–500ns |
| Chronicle Network | Pure-Java kernel bypass using Solarflare/DPDK | ~300ns |

The most common HFT architecture: **DPDK C process receives packets → writes to
shared memory (aeron:ipc or shared ring buffer) → Java trading logic reads**.

### DPDK Latency Profile

```
Interrupt-driven kernel UDP:    5–50µs   (context switch + TCP/IP stack)
DPDK user-space UDP:           200–600ns  (PMD poll, no kernel)
DPDK + 10GbE + co-location:    ~150–300ns (wire to application buffer)
```

---

## RDMA — Remote Direct Memory Access

### What RDMA Is

RDMA allows a host to **read or write memory on a remote machine directly**, bypassing
the CPU and OS on both ends. The NIC (HCA — Host Channel Adapter) performs the DMA
transfer autonomously.

```
Standard network message (TCP):
  App A writes → kernel → TCP stack → NIC → wire → NIC → kernel → TCP stack → App B reads
  CPU on both ends involved. 2 context switches. Multiple copies. Latency: 10–100µs

RDMA message (one-sided READ):
  App A's NIC reads directly from App B's pre-registered memory region
  App B's CPU is NOT involved AT ALL. No kernel. No context switch. 1 DMA.
  Latency: 1–2µs (InfiniBand) / 2–5µs (RoCEv2 over Ethernet)
```

### RDMA Transport Types

| Transport | Physical | Latency | Notes |
|---|---|---|---|
| InfiniBand (IB) | Dedicated IB fabric | 1–2µs | Fastest; separate network from Ethernet |
| RoCEv2 | Standard Ethernet (25/100GbE) | 2–5µs | Most popular in modern HFT data centres |
| iWARP | Standard Ethernet (TCP) | 3–10µs | Reliable but higher overhead |

### Key RDMA Concepts

**Memory Registration (ibv_reg_mr):** Before RDMA can access memory, the buffer must
be **pinned** (non-pageable) and registered with the HCA. The HCA gets a virtual-to-physical
translation table. The OS cannot page out this memory during trading.

```c
// Register a buffer for RDMA (C — libibverbs API)
struct ibv_mr *mr = ibv_reg_mr(pd, buffer, buffer_size,
                                IBV_ACCESS_LOCAL_WRITE |
                                IBV_ACCESS_REMOTE_READ |
                                IBV_ACCESS_REMOTE_WRITE);
// mr->lkey = local key (for local DMA)
// mr->rkey = remote key (must be shared with peer to allow remote access)
```

**Queue Pairs (QP):** RDMA communication uses Queue Pairs — a Send Queue (SQ) and
Receive Queue (RQ) per connection, plus a Completion Queue (CQ) for notifications.
This is the hardware equivalent of Aeron's ring buffer + completion log.

**One-sided vs Two-sided operations:**

| Operation | CPU involvement | Use case |
|---|---|---|
| SEND / RECV (two-sided) | Both CPUs involved | Order routing (remote CPU must post RECV) |
| READ (one-sided) | Only initiator CPU | Fetch remote order book snapshot |
| WRITE (one-sided) | Only initiator CPU | Push order to remote engine (zero CPU on receiver) |
| Atomic CAS (one-sided) | Only initiator CPU | Lock-free shared counter across machines |

```c
// RDMA WRITE: push an order directly into remote engine's buffer
struct ibv_sge sge = {
    .addr   = (uint64_t) &local_order,    // local buffer with order data
    .length = sizeof(Order),
    .lkey   = mr->lkey
};
struct ibv_send_wr wr = {
    .opcode     = IBV_WR_RDMA_WRITE,
    .wr.rdma.remote_addr = remote_buf_addr,  // remote engine's buffer address
    .wr.rdma.rkey        = remote_mr_rkey,   // remote key shared out-of-band
    .sg_list    = &sge,
    .num_sge    = 1,
    .send_flags = IBV_SEND_SIGNALED
};
ibv_post_send(qp, &wr, &bad_wr);
// Remote matching engine's CPU was never interrupted.
// Data appeared directly in its pre-registered buffer.
```

### RDMA in HFT Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Co-location Rack                     │
│                                                         │
│  Trading Server A          Trading Server B             │
│  ┌──────────────┐          ┌──────────────┐             │
│  │  Java OMS    │          │  Java Risk   │             │
│  │  (order gen) │          │  Engine      │             │
│  └──────┬───────┘          └──────┬───────┘             │
│         │ JNI / Unsafe            │ JNI / Unsafe        │
│  ┌──────▼───────┐          ┌──────▼───────┐             │
│  │  libibverbs  │──RDMA──►│  registered  │             │
│  │  (C layer)   │◄──RDMA──│  memory buf  │             │
│  └──────────────┘  RoCEv2 └──────────────┘             │
│         │                         │                     │
│  ┌──────▼───────┐          ┌──────▼───────┐             │
│  │  Mellanox    │          │  Mellanox    │             │
│  │  ConnectX-7  │──100GbE─►│  ConnectX-7  │             │
│  └──────────────┘          └──────────────┘             │
└─────────────────────────────────────────────────────────┘

Message latency OMS → Risk Engine: ~2µs (vs ~20µs over TCP)
```

---

## SmartNIC / DPU — Smart Network Interface Cards

### What SmartNICs Are

A SmartNIC (or DPU — Data Processing Unit) is a NIC with embedded compute —
ARM cores, FPGA fabric, or both — that can execute code **on the NIC itself**,
offloading work from the host CPU.

### Hardware Examples

| Device | Compute | Use in HFT |
|---|---|---|
| NVIDIA BlueField-3 | 16× ARM Cortex-A78 + ConnectX-7 NIC | Risk pre-check on NIC, RDMA gateway |
| AMD Pensando Elba | 24× ARM Cortex-A72 + FPGA fabric | Stateful firewall, order rate limiting |
| Intel IPU (Mt. Evans) | Intel Atom P5900 + 100GbE | SmartNIC for cloud, emerging HFT use |
| Xilinx Alveo U25N | FPGA + 25GbE NIC | Combined FPGA+NIC for tick processing |

### HFT Use Cases

```
Without SmartNIC:
  Market data packet → NIC → kernel → user space → risk check → order decision

With SmartNIC (BlueField-3):
  Market data packet → NIC DPU (ARM cores)
                         ├── risk pre-check on NIC (e.g., position limit)
                         ├── packet filtering (drop irrelevant symbols)
                         └── forward to host CPU only if valid
                       Host CPU → order decision (simpler, faster)
```

**Key benefit:** The host CPU never sees packets that fail risk pre-checks or are
for symbols it does not trade. CPU L1/L2 cache stays hot with relevant data only.
Measured benefit at Jump Trading and Tower Research: **15–30% reduction in host CPU
cache pressure** from NIC-side packet filtering.

---

## Solarflare / OpenOnload / ef_vi

### OpenOnload

OpenOnload (Solarflare, now part of AMD/Xilinx) is a **kernel bypass network stack**
that runs entirely in user space. It intercepts standard POSIX socket calls (`recv`,
`send`, `epoll`) via LD_PRELOAD without changing application code.

```
Without OpenOnload:
  app recv() → kernel socket layer → TCP/IP → driver → NIC
  Latency: 5–20µs

With OpenOnload:
  app recv() → OpenOnload user-space stack → directly polls NIC Rx ring
  Latency: 1–3µs
  Same socket API — zero code changes required in Java application.
```

Usage:
```bash
# Run Java app with OpenOnload kernel bypass
onload java -XX:+UseZGC -Xms4g -Xmx4g com.example.TradingApp
```

### ef_vi (Effortless Virtual Interface)

ef_vi is the **raw, zero-copy NIC access API** (below TCP/UDP) from Solarflare.
It bypasses the entire network stack — even OpenOnload's user-space TCP.

```c
// ef_vi raw packet send (C)
ef_vi_transmit_init(&vi, dma_addr, packet_len, EF_VI_TX_PUSH_ALWAYS);
ef_vi_transmit_push(&vi);
// Direct DMA to NIC Tx ring. No copies. No TCP/IP. No UDP.
// Latency from ef_vi transmit to wire: ~150–300ns
```

Used by firms that implement their own reliable protocol on top of raw Ethernet —
skipping even UDP encapsulation overhead.

### TCPDirect

TCPDirect is Solarflare's **zero-copy TCP** implementation in user space.
Unlike OpenOnload (which intercepts standard sockets), TCPDirect uses a dedicated API
with pre-allocated `zocket` objects.

```c
// TCPDirect — zero-copy receive
struct zft_msg msg;
zft_zc_recv(zock, &msg, 0);
// msg.iov[0].iov_base points directly into NIC DMA buffer
// No copy from kernel. No sk_buff. Direct NIC memory access.
zft_zc_recv_done(zock, &msg);   // release buffer back to NIC
```

Latency: **800ns–1.5µs** TCP (standard TCP is 10–50µs over loopback).

---

## PTP / IEEE 1588 — Precision Time Protocol and Hardware Timestamping

### Why Timestamps Matter in HFT

Every exchange trade report, market data event, and order acknowledgement carries a
nanosecond timestamp. HFT firms need **hardware-accurate timestamps** for:

1. **Latency measurement:** measure exact round-trip time of an order
2. **Market data sequencing:** detect out-of-order packets without sequence numbers
3. **VWAP / TWAP calculation:** correct time-weighted price with accurate timestamps
4. **Regulatory compliance:** MiFID II requires timestamps to 1µs accuracy

### PTP Hardware Timestamping

The NIC captures the **exact clock cycle** when a packet's first bit arrives or
departs — before any software processing. This eliminates software jitter from
interrupt handling, kernel scheduling, and JVM overhead.

```
Software timestamp (System.nanoTime()):
  packet arrives at NIC ──── interrupt ──── kernel ──── Java nanoTime()
                             └──────── 1–50µs jitter here ────────┘

Hardware timestamp (PTP on NIC):
  packet arrives at NIC ──── NIC captures timestamp in hardware ──── Java reads via JNI
                             └── sub-100ns accuracy ──────────────┘
```

### PTP Synchronisation Stack

```
GPS Disciplined Oscillator (GPSDO)
    │  10MHz reference + 1PPS pulse-per-second
    ▼
Grandmaster Clock (Arista 7170, Cisco Nexus)
    │  PTP over Ethernet (IEEE 1588v2)
    ▼
Boundary Clock (top-of-rack switch)
    │  distributes PTP to all servers on the rack
    ▼
Ordinary Clock on Server (Solarflare SFN8522, Mellanox ConnectX-6)
    │  hardware disciplines system clock to PTP master
    ▼
phc2sys / ptp4l (Linux PTP tools)
    │  syncs CLOCK_REALTIME to NIC hardware clock
    ▼
Application reads CLOCK_TAI (SI seconds, no leap-second jumps)
```

Accuracy achievable in co-location: **< 100ns** clock synchronisation across servers.
This is orders of magnitude better than NTP (1ms–100ms accuracy).

### Java Hardware Timestamping

```java
// Read hardware timestamp via JNI (example using Solarflare Java bindings)
long hwTimestampNs = solarflareNic.getLastRxTimestampNanos();

// Or via Linux SO_TIMESTAMPING socket option + recvmsg (JNI wrapper)
// Gives CLOCK_REALTIME hardware timestamp of the exact moment the
// packet hit the NIC wire — not when Java's recv() returned.

// For CLOCK_TAI (preferred in HFT — monotonic, no leap second):
// clock_gettime(CLOCK_TAI, &ts) via JNI
```

---

## SPDK — Storage Performance Development Kit

### What SPDK Is

SPDK (Storage Performance Development Kit, Intel open-source) moves **NVMe SSD I/O
from the kernel into user space**, the storage equivalent of what DPDK does for networking.

```
Kernel NVMe path:
  write() syscall → VFS → page cache → block layer → NVMe driver → SSD
  Latency: 20–100µs (context switch, queue depths, kernel scheduling)

SPDK NVMe path:
  spdk_nvme_ns_cmd_write() → user-space NVMe driver → SSD submission queue → SSD
  Latency: 2–5µs (Intel Optane P5800X) / 10–20µs (NAND NVMe)
  No syscall. No kernel. PMD polling of NVMe completion queue.
```

### HFT Storage Use Case: Event Sourcing Journal

HFT matching engines use **event sourcing** — every order event is written to a durable
journal before being processed. The journal enables:
- Deterministic replay after a crash
- Regulatory audit trail
- State machine consistency (see `DeterministicExchangeSM.java`)

**Journal write latency target:** < 5µs (must not add latency to the tick-to-trade path)

```
Without SPDK (kernel write):
  journal.write(event) → write() syscall → kernel → NVMe queue → Optane
  p99: 50–200µs → adds visible latency jitter to order pipeline

With SPDK + Intel Optane P5800X:
  spdk_nvme_ns_cmd_write() → user-space queue → Optane direct
  p50: 2µs, p99: 5µs → negligible impact on order pipeline
```

### Java + SPDK

SPDK is a C library. Java integration via JNI:

```java
// JNI wrapper pattern
public class SpdkJournal {
    private static native long spdkOpen(String devicePath);
    private static native void spdkWrite(long handle, long lba, ByteBuffer buf, int len);
    private static native void spdkPoll(long handle);  // poll completion queue

    // Hot path: journal an event with 2–5µs latency
    public void appendEvent(ByteBuffer eventBuf) {
        spdkWrite(handle, currentLba, eventBuf, eventBuf.limit());
        spdkPoll(handle);   // busy-poll for completion (no blocking syscall)
        currentLba++;
    }
}
// Chronicle Queue uses a similar approach via MappedByteBuffer to memory-mapped files.
```

---

## io_uring — Zero-Syscall Async I/O

### What io_uring Is

io_uring (introduced in Linux 5.1, 2019) is a **shared-memory ring buffer interface**
between user space and the Linux kernel for submitting and completing I/O operations.

```
Traditional async I/O (epoll):
  app posts work → epoll_wait() syscall → kernel → data ready → kernel wakes app
  2 syscalls minimum per I/O. Each syscall: 500ns–2µs.

io_uring:
  app writes to Submission Queue (SQ ring in shared memory)
  kernel polls SQ ring (SQPOLL mode) — no syscall needed to submit
  kernel writes completion to Completion Queue (CQ ring in shared memory)
  app reads CQ ring — no syscall needed to receive result
  Total syscalls: 0 per I/O in SQPOLL mode (one io_uring_setup at startup only)
```

### io_uring Architecture

```
User space              │   Kernel space
                         │
  ┌─────────────────┐    │    ┌──────────────────┐
  │ Submission Queue│◄───┼────│ io_uring_setup() │
  │ Ring (SQ Ring)  │    │    └──────────────────┘
  └────────┬────────┘    │
           │ app writes SQE (Submission Queue Entry)
           │             │    ┌──────────────────┐
           └─────────────┼───►│ Kernel SQPOLL    │
                         │    │ thread polls SQ  │
                         │    │ ring (no syscall)│
                         │    └────────┬─────────┘
                         │             │ performs I/O
                         │    ┌────────▼─────────┐
  ┌─────────────────┐    │    │ Completion Queue  │
  │ Completion Queue│◄───┼────│ Ring (CQ Ring)   │
  │ Ring (CQ Ring)  │    │    └──────────────────┘
  └─────────────────┘    │
  app reads CQE          │
  (no syscall)           │
```

### HFT Use: Zero-Syscall Journal Writes

```c
// io_uring SQPOLL setup (C)
struct io_uring ring;
struct io_uring_params params = {
    .flags = IORING_SETUP_SQPOLL,    // kernel polls SQ — no syscall for submit
    .sq_thread_idle = 2000           // kernel thread sleeps after 2s inactivity
};
io_uring_queue_init_params(QUEUE_DEPTH, &ring, &params);

// Hot path: submit journal write (ZERO syscalls)
struct io_uring_sqe *sqe = io_uring_get_sqe(&ring);
io_uring_prep_write(sqe, journal_fd, event_buf, event_len, file_offset);
io_uring_submit(&ring);  // actually a no-op in SQPOLL mode!

// Poll completion (ZERO syscalls)
struct io_uring_cqe *cqe;
io_uring_peek_cqe(&ring, &cqe);   // non-blocking peek — no syscall
```

**Linked SQEs:** io_uring supports **linked operations** — chain a write + fsync in a
single submission; the fsync executes atomically after the write completes. Zero extra
round-trips to the kernel.

### Java + io_uring

**Netty 5** (in development) and **Quarkus vert.x** support io_uring via JNI.
**Chronicle Queue** uses `MappedByteBuffer` for similar zero-syscall journal writes.

Pure Java approach: `FileChannel.map()` + `MappedByteBuffer.force()` is equivalent
for small writes (the kernel flushes the dirty page to NVMe without a syscall from
the application, though the flush is not deterministically timed).

---

## eBPF / XDP — Programmable Kernel Bypass at the NIC Driver

### What eBPF / XDP Is

**eBPF** (extended Berkeley Packet Filter) lets you load **custom bytecode programs**
into the Linux kernel that run in a safe, sandboxed VM without modifying kernel source.

**XDP** (eXpress Data Path) is an eBPF hook point at the **NIC driver level** —
before the kernel allocates a `sk_buff` (socket buffer) for the packet.

```
Normal Linux packet path:
  NIC → driver → sk_buff alloc (malloc) → netfilter/iptables → socket → app
  sk_buff alloc: ~200ns. iptables chain: ~500ns–5µs.

XDP path (eBPF program at NIC driver):
  NIC → XDP hook ──► XDP_DROP   (discard — e.g., block unwanted symbols)
                 ──► XDP_PASS   (continue to kernel stack)
                 ──► XDP_TX     (retransmit on same NIC — for response offload)
                 ──► XDP_REDIRECT → AF_XDP socket (zero-copy to user space)
  XDP hook runs before sk_buff alloc → saves 200–500ns per packet
```

### AF_XDP — Zero-Copy Socket for XDP

AF_XDP sockets allow **zero-copy packet delivery from XDP directly to user space**,
bypassing the entire kernel network stack.

```c
// AF_XDP setup (C)
int xsk_fd = socket(AF_XDP, SOCK_RAW, 0);

// UMEM: shared memory region between kernel XDP and user space
struct xdp_umem_reg umem_reg = {
    .addr = (uint64_t) umem_area,   // mmap'd hugepage region
    .len  = UMEM_SIZE,
    .chunk_size = CHUNK_SIZE,
};
setsockopt(xsk_fd, SOL_XDP, XDP_UMEM_REG, &umem_reg, sizeof(umem_reg));

// Rx ring: kernel XDP writes packet descriptors here
// Tx ring: user writes descriptors; kernel DMA's to NIC
// Fill ring: user supplies free UMEM chunks to kernel
// Completion ring: kernel returns sent chunks
```

### HFT Use Cases for eBPF/XDP

| Use Case | XDP Action | Benefit |
|---|---|---|
| Drop unwanted symbols | `XDP_DROP` before sk_buff | Saves CPU, cache pressure |
| Route equity to App A, futures to App B | `XDP_REDIRECT` to AF_XDP socket | Zero-copy demux |
| Hardware firewall (block non-exchange IPs) | `XDP_DROP` | Microsecond filtering |
| Custom UDP protocol decode | eBPF in XDP hook | Decode in kernel, pass structured data |
| Latency measurement / packet timestamping | eBPF + perf ring buffer | ns-accurate timestamps without full tcpdump |

### eBPF in Java Context

eBPF programs run in the kernel (C-like bytecode compiled with Clang/LLVM). Java
applications interact via:

1. **AF_XDP socket + JNI:** Java reads from AF_XDP ring buffer via Unsafe/JNI
2. **BPF perf ring buffer → Java agent:** eBPF timestamps written to perf buffer; Java reads via JNI
3. **tc eBPF programs:** loaded by `ip link set dev eth0 xdp obj prog.o` — transparent to Java

Tools: **bpftrace**, **bcc** (BPF Compiler Collection), **libbpf**.

---

## Kernel Bypass Comparison Table

| Technology | Latency | Language | Scope | Notes |
|---|---|---|---|---|
| Standard Linux sockets | 5–50µs | Any | Full network stack | Baseline; not for HFT hot path |
| OpenOnload (LD_PRELOAD) | 1–3µs | Any (transparent) | TCP/UDP user-space stack | Zero code change; works with Java |
| DPDK | 200–600ns | C/C++ primarily | Full user-space NIC driver | Requires dedicated core (busy poll) |
| ef_vi (Solarflare raw) | 150–300ns | C | Raw NIC Rx/Tx | Skips even UDP; custom protocol |
| XDP + AF_XDP | 200–500ns | C (eBPF) + any | NIC driver hook | Programmable; works with existing NIC |
| RDMA (RoCEv2) | 2–5µs | C (libibverbs) | Cross-machine | Best for machine-to-machine messaging |
| FPGA (HLS) | 300ns–1µs | C++ (HLS) + C (PCIe DMA) | Full pipeline in silicon | Reprogrammable hardware |
| FPGA (RTL) | 100–300ns | VHDL/Verilog | Full pipeline in silicon | Maximum performance, weeks to code |
| Aeron IPC | < 1µs | Java | Same machine, process-to-process | Best pure-Java option |
| Aeron UDP | 1–3µs | Java | LAN cross-machine | Best pure-Java cross-machine |

---

## HFT Architecture Decision Framework

```
┌─────────────────────────────────────────────────────────────────────────┐
│                   LATENCY TARGET DECISION TREE                          │
│                                                                         │
│  What is your tick-to-trade latency target?                            │
│                                                                         │
│  > 50µs (algo trading, not co-lo)                                      │
│    → Java + G1GC + Aeron UDP. No kernel bypass needed.                 │
│                                                                         │
│  10–50µs (co-lo, competitive market making)                            │
│    → Java + ZGC + Aeron IPC/UDP + OpenOnload kernel bypass             │
│    → Object pools, zero allocation hot path                            │
│                                                                         │
│  2–10µs (tier-1 HFT, prop trading desk)                               │
│    → C++ or Java + DPDK or Solarflare ef_vi                           │
│    → RDMA for cross-machine order routing                              │
│    → CPU isolation (isolcpus), NUMA-local allocation                   │
│    → SPDK + Optane for < 5µs event sourcing journal                   │
│                                                                         │
│  < 2µs (front-running prevention, pure speed competition)             │
│    → FPGA (HLS) for market data parse + order encode                  │
│    → RDMA one-sided WRITE to matching engine                          │
│    → Hardware PTP timestamping (< 100ns clock sync)                   │
│    → SmartNIC for risk pre-check offload                              │
│                                                                         │
│  < 300ns (arms race — top 5 HFT globally)                            │
│    → Custom RTL FPGA (Verilog/VHDL) full tick-to-trade pipeline       │
│    → ASIC consideration (non-reprogrammable, 10–50ns)                 │
│    → Co-location at exchange matching engine rack                      │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Updated Quick-Reference: Latency Numbers Every HFT Engineer Knows

```
─── CPU / Memory ──────────────────────────────────────────────────
L1 cache hit:                  ~1ns
L2 cache hit:                  ~4ns
L3 cache hit:                  ~15ns
DRAM (main memory):            ~70ns
NUMA remote memory:            ~150ns
CAS (AtomicLong):              ~10ns
Thread.onSpinWait() (PAUSE):   ~1ns
Context switch:                ~1–10µs
Syscall overhead:              ~500ns–5µs

─── JVM ────────────────────────────────────────────────────────────
Java lock (uncontended):       ~20ns
Java lock (contended):         ~1–10µs
GC pause (ZGC):                < 1ms
GC pause (G1GC):               5–20ms
JIT hot method compilation:    one-time cost; thereafter near-native

─── Messaging / IPC ────────────────────────────────────────────────
Aeron IPC (same machine):      < 1µs
Aeron UDP (10GbE LAN):         ~3µs
Disruptor (same JVM):          < 100ns
Kafka (local):                 ~1ms
ZeroMQ:                        ~50–200µs

─── Network / Kernel Bypass ────────────────────────────────────────
Standard Linux UDP recv():     5–50µs
OpenOnload kernel bypass:      1–3µs
DPDK PMD user-space:           200–600ns
Solarflare ef_vi raw NIC:      150–300ns
XDP + AF_XDP:                  200–500ns
RDMA (RoCEv2, cross-machine):  2–5µs
RDMA (InfiniBand):             1–2µs
Data-centre switch (cut-thru): ~300–500ns
AWS network hop:               5–50µs

─── Storage ────────────────────────────────────────────────────────
NVMe SSD (kernel path):        20–100µs
NVMe SSD (SPDK user-space):    5–20µs
Intel Optane P5800X (SPDK):    2–5µs
io_uring SQPOLL (vs epoll):    saves 1–3µs per I/O (no syscall)

─── Hardware Pipelines ─────────────────────────────────────────────
FPGA HLS tick-to-trade:        300ns–1µs
FPGA RTL tick-to-trade:        100–300ns
ASIC tick-to-trade:            10–50ns
PTP hardware clock sync:       < 100ns accuracy
Software NTP clock sync:       1ms–100ms accuracy
```

---

---

# Capital Markets: FX & Payments — Practical Low-Latency Patterns

The sections below cover patterns specific to **FX trading**, **wire processing**,
and **payment modernisation** (ISO 20022 / CBPR+) at 10M+ transactions/month scale.

---

## Distributed Caching for Low-Latency Capital Markets

### Why Caching is Critical

Every database round-trip in a transaction pipeline adds latency. For FX pricing
and payment reference data lookups, the pattern is always the same:

```
Without cache:
  incoming FX order → DB lookup (FX rate, counterparty limit, account status)
                        └── ~400µs–5ms per query (network + DB lock)
  At 10M transactions/month → 3.8 queries/sec average; burst during London open: 500+/sec
  p99 latency: 10–50ms

With distributed cache:
  incoming FX order → cache hit (FX rate: TTL 100ms, counterparty limit: TTL 5s)
                        └── ~1–5µs (local cache) / ~50–200µs (Redis cluster)
  DB hit only on cache miss or stale data
  p99 latency: 0.5–2ms
```

Real-world result: introducing distributed caching on an FX platform reduced DB
query load by **80%** and cut average transaction latency from 400ms → 150ms.

### Cache Technology Options

| Library | Type | Latency | Best For |
|---|---|---|---|
| **Caffeine** | In-process (JVM heap) | ~100ns | Per-instance rate/reference data |
| **Chronicle Map** | Off-heap (mmap) | ~500ns | Large maps without GC pressure |
| **Hazelcast** | Distributed (embedded or client-server) | ~200µs–1ms | Shared session state, cross-service |
| **Redis (Lettuce/Redisson)** | External cluster | ~100–500µs | Reference data, shared counters |
| **Infinispan** | Embedded or clustered | ~100µs–2ms | JBoss ecosystem, transactional cache |
| **Coherence (Oracle)** | Distributed grid | ~200µs–2ms | Large bank deployments |

**Rule:**
- **Hot path (tick data, FX rates):** Caffeine in-process (L1 cache), TTL 100ms–1s
- **Shared state (positions, limits):** Hazelcast or Redis (L2 distributed cache)
- **Reference data (static rates, account details):** Redis with write-through, TTL 5–60s

### Cache Write Strategies

```
WRITE-THROUGH (strong consistency):
  app writes → cache.put() + DB.write() in same transaction
  ┌────────────────────────────────────────────────────────┐
  │  App → Cache → DB (synchronous)                        │
  │        Cache is always consistent with DB              │
  │  Latency penalty: DB write latency on every put()      │
  │  Use: payment confirmations, position updates          │
  └────────────────────────────────────────────────────────┘

WRITE-BEHIND (eventual consistency):
  app writes → cache.put() → returns immediately
               background thread → DB.write() asynchronously
  ┌────────────────────────────────────────────────────────┐
  │  App → Cache (fast) → DB (async, batched)              │
  │  Cache and DB temporarily inconsistent                 │
  │  Risk: crash between write and DB flush = data loss    │
  │  Use: audit logs, non-critical FX rate snapshots       │
  └────────────────────────────────────────────────────────┘

CACHE-ASIDE (read-through on miss):
  app reads → cache miss → DB query → cache.put() → return
  ┌────────────────────────────────────────────────────────┐
  │  Read: App → Cache (hit: return) / (miss: DB → Cache)  │
  │  Write: App → DB directly, invalidate cache entry      │
  │  Use: FX reference rates, counterparty static data     │
  └────────────────────────────────────────────────────────┘
```

### Hot-Key Problem and Avoidance

A hot key occurs when thousands of requests per second all hit the **same cache entry**
— e.g., the EUR/USD spot rate during London market open.

```
Hot-key scenario:
  500 FX pricing threads all call cache.get("EURUSD") simultaneously
  → even in-memory cache lock contention → latency spikes

Mitigations:

1. Local L1 copy per thread (Caffeine with small TTL):
   Each thread has a 10ms local copy of the rate.
   At most 1 Redis lookup per thread per 10ms.
   Cost: 10ms stale risk — acceptable for FX spread pricing.

2. Striped cache (shard by thread ID):
   cache["EURUSD-0"], cache["EURUSD-1"], ... cache["EURUSD-N"]
   Each thread reads its own shard. No contention.

3. ReadWriteLock on the value:
   Single writer (rate updater); many concurrent readers.
   See Block1_CoreJavaConcurrency.java — ReadWriteLock pattern.

4. LongAdder / AtomicLong for counters (not cache):
   Never use synchronized Integer for rate-limit counters.
   LongAdder stripes the counter across CPU cores — zero contention.
```

### Java Pattern: Two-Level Cache (L1 Caffeine + L2 Redis)

```java
// Two-level FX rate cache — zero allocation on hot path
public class FxRateCache {
    // L1: in-process, nanosecond access, small TTL (stale risk: acceptable for spread)
    private final Cache<String, Long> l1 = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(100, TimeUnit.MILLISECONDS)  // 100ms stale max
        .build();

    // L2: Redis cluster, microsecond access, source of truth
    private final RedisCommands<String, Long> redis;

    // Rate stored as fixed-point long (e.g., 1.08345 → 108345L) — zero GC, no Double boxing
    public long getRate(String ccyPair) {
        Long rate = l1.getIfPresent(ccyPair);  // L1 hit: ~100ns, zero allocation
        if (rate != null) return rate;

        rate = redis.get(ccyPair);              // L2 hit: ~200µs
        if (rate != null) l1.put(ccyPair, rate);
        return rate != null ? rate : 0L;
    }

    public void updateRate(String ccyPair, long rate) {
        l1.invalidate(ccyPair);                 // evict stale L1 immediately
        redis.set(ccyPair, rate);               // write-through to L2
    }
}
// Note: rate as fixed-point long — avoids Double auto-boxing on every cache.get()
// This is the same zero-allocation principle as HotPathAllocationDemo.java
```

### Eviction and TTL Strategy for FX

| Data type | TTL | Eviction policy | Notes |
|---|---|---|---|
| Spot FX rates (EUR/USD) | 50–200ms | Write-through on tick | Stale rate = pricing error |
| FX forward points | 1–5s | Write-through on update | Less volatile |
| Counterparty credit limits | 5–30s | Write-through on breach | Risk exposure if stale |
| Account status (active/frozen) | 30–60s | Write-through on change | Compliance: never serve stale frozen status |
| Static reference data (CCY codes, BIC) | 1hr–24hr | LRU on capacity | Changes rarely |
| Audit log | No TTL | Write-behind, async flush | Non-critical for latency |

---

## JMH — Java Microbenchmark Harness

### Why JMH (Not `System.nanoTime()` Loops)

Measuring performance in Java is notoriously unreliable without proper tooling:

```
Naive benchmark pitfalls:
  1. JIT not warmed up → first 10,000 iterations run interpreted (10–100× slower)
  2. Dead-code elimination → JIT sees result unused → removes the code entirely
  3. OSR (On-Stack Replacement) → JIT compiles mid-loop; first half unoptimised
  4. GC during benchmark → random pauses inflate numbers
  5. CPU frequency scaling → OS may throttle core mid-run

JMH solves all of these:
  - Automatic warm-up iterations (configurable)
  - @Blackhole to prevent dead-code elimination
  - Fork mode: fresh JVM per benchmark set (no JIT state pollution)
  - GC control between iterations
  - Percentile output (p50, p90, p99) not just averages
```

### JMH Benchmark Structure

```java
// gradle: implementation 'org.openjdk.jmh:jmh-core:1.37'
//         annotationProcessor 'org.openjdk.jmh:jmh-generator-annprocess:1.37'

@BenchmarkMode(Mode.AverageTime)          // alternatives: Throughput, SampleTime, All
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)         // 5 × 1-second warm-up iterations
@Measurement(iterations = 10, time = 1)   // 10 × 1-second measurement iterations
@Fork(2)                                  // 2 fresh JVM forks per benchmark
@State(Scope.Benchmark)
public class FxRateBenchmark {

    private FxRateCache cacheWithL1;
    private FxRateCache cacheWithoutL1;
    private Blackhole bh;                 // prevents dead-code elimination

    @Setup
    public void setup() {
        cacheWithL1 = new FxRateCache(true);
        cacheWithoutL1 = new FxRateCache(false);
        cacheWithL1.updateRate("EURUSD", 108345L);
        cacheWithoutL1.updateRate("EURUSD", 108345L);
    }

    @Benchmark
    public void withL1Cache(Blackhole bh) {
        bh.consume(cacheWithL1.getRate("EURUSD"));    // result consumed → no dead-code elim
    }

    @Benchmark
    public void withoutL1Cache(Blackhole bh) {
        bh.consume(cacheWithoutL1.getRate("EURUSD")); // forces Redis call every time
    }
}

// Run: java -jar target/benchmarks.jar -prof gc     (shows GC allocation per op)
//      java -jar target/benchmarks.jar -prof stack  (shows stack trace hotspots)
//      java -jar target/benchmarks.jar FxRateBenchmark.withL1 -wi 3 -i 5
```

### Key JMH Annotations Reference

| Annotation | Purpose |
|---|---|
| `@BenchmarkMode(Mode.AverageTime)` | Average time per operation |
| `@BenchmarkMode(Mode.Throughput)` | Operations per second |
| `@BenchmarkMode(Mode.SampleTime)` | Latency distribution (p50, p99, p99.9) |
| `@OutputTimeUnit(TimeUnit.NANOSECONDS)` | Report in nanoseconds |
| `@Warmup(iterations=5)` | JIT warm-up before measuring |
| `@Fork(2)` | Run in 2 fresh JVMs to avoid JIT state |
| `@State(Scope.Benchmark)` | One shared state object per benchmark |
| `@State(Scope.Thread)` | One state object per thread |
| `@Setup(Level.Iteration)` | Reset state before each measurement |
| `Blackhole.consume(x)` | Prevent dead-code elimination of result `x` |

### JMH + `-prof gc` : Allocation Profiling

```bash
# Run with GC profiler — shows bytes allocated per operation
java -jar benchmarks.jar -prof gc

# Output:
# withL1Cache  avgt   10   105.3 ± 2.1  ns/op
#   ·gc.alloc.rate            0.001 MB/sec   ← nearly zero allocation
# withoutL1Cache  avgt  10  215300 ± 1200  ns/op
#   ·gc.alloc.rate           12.3 MB/sec    ← Redis response object allocations
```

This is the right tool to verify that `HotPathAllocationDemo.java` patterns
(object pool, flyweight, primitive arrays) truly achieve zero allocation on the hot path.

### JMH Latency Distribution (SampleTime Mode)

```bash
# Profile p99.9 latency — critical for HFT SLA verification
java -jar benchmarks.jar -bm SampleTime -tu ns

# Output:
# withL1Cache  sample  500000
#   p0.00:     85 ns
#   p0.50:    102 ns
#   p0.90:    128 ns
#   p0.99:    450 ns
#   p0.999:  2100 ns    ← p99.9 spike: likely a GC minor collection
#   p1.00:  15000 ns
```

The p99.9 spike at 2100ns reveals a periodic GC event missed by average-time benchmarks.
Relevant for confirming ZGC is meeting its < 1ms pause target in trading workloads.

---

## Load Testing: Gatling for FX Burst Simulation

### Why Load Testing Matters

FX markets have predictable **burst patterns**:
- **London open (08:00 GMT):** 5–10× normal order volume in 5 minutes
- **NY open (13:30 GMT):** Second burst, especially for USD pairs
- **Economic data releases (NFP, CPI):** Instantaneous 50–100× spikes

A system that handles steady-state 100 req/sec but cannot survive 5,000 req/sec for
30 seconds will fail at the worst possible moment.

### Gatling DSL Example (FX Order Submission)

```scala
// build.gradle: testImplementation 'io.gatling.highcharts:gatling-charts-highcharts:3.10.3'

class FxOrderSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://fx-engine:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  val fxOrderFeeder = Iterator.continually(Map(
    "ccyPair"  -> Seq("EURUSD", "GBPUSD", "USDJPY").apply(Random.nextInt(3)),
    "side"     -> Seq("BUY", "SELL").apply(Random.nextInt(2)),
    "quantity" -> (100000 + Random.nextInt(900000)),
    "clientId" -> s"CLIENT-${Random.nextInt(500)}"
  ))

  val submitOrder = scenario("FX Order Submission")
    .feed(fxOrderFeeder)
    .exec(http("submit order")
      .post("/api/orders")
      .body(StringBody(
        """{"ccyPair":"${ccyPair}","side":"${side}","qty":${quantity},"clientId":"${clientId}"}"""
      ))
      .check(status.is(200))
      .check(responseTimeInMillis.lte(50))   // SLA: 95th percentile < 50ms
    )

  setUp(
    submitOrder.inject(
      // Ramp up to steady state
      rampUsersPerSec(10).to(100).during(30.seconds),
      // Hold steady state
      constantUsersPerSec(100).during(2.minutes),
      // London open burst: 10× spike for 30 seconds
      rampUsersPerSec(100).to(1000).during(10.seconds),
      constantUsersPerSec(1000).during(30.seconds),
      rampUsersPerSec(1000).to(100).during(10.seconds)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.percentile(95).lte(50),   // p95 < 50ms
     global.responseTime.percentile(99).lte(200),  // p99 < 200ms
     global.failedRequests.percent.lte(0.1)        // < 0.1% errors
   )
}
```

### Reading Gatling Reports

Gatling generates HTML reports with latency percentiles, throughput, and error rates.
Key metrics to watch:

| Metric | Target (FX platform) | Action if breached |
|---|---|---|
| p50 response time | < 10ms | Check cache hit rate first |
| p95 response time | < 50ms | Check DB pool exhaustion, GC pauses |
| p99 response time | < 200ms | Check thread pool queue depth (backpressure) |
| Error rate | < 0.1% | Check circuit breaker trips, connection timeouts |
| Throughput drop during burst | < 5% | Check bulkhead sizing (Block3 SystemDesign) |

### Connecting Load Test Results to Root Causes

```
Symptom: p99 spikes during burst, p50 stable
  → Thread pool queue filling up (unbounded LinkedBlockingQueue — OOM risk)
  → Fix: BoundedExecutor + reject policy (Block3 bulkhead pattern)

Symptom: latency ramps up gradually, then recovers
  → GC pressure building up during load
  → Fix: -Xlog:gc* to confirm; reduce allocation rate (object pool, flyweight)

Symptom: specific endpoint degrades, others fine
  → Cache hot key: all threads competing for same lock
  → Fix: striped cache, L1 per-thread copy

Symptom: random 2–5s pauses, then normal
  → System.gc() called by a library
  → Fix: -XX:+DisableExplicitGC (Block2 JVM flags reference)

Symptom: error spikes at exactly burst start
  → Circuit breaker tripping (Block3): miscalibrated thresholds
  → Fix: tune failure-rate threshold and half-open probe count
```

---

## ISO 20022 / CBPR+ — Low-Latency Message Processing

### The Challenge

ISO 20022 is the global standard for financial messaging (payments, FX confirmations,
securities). **CBPR+** (Cross-Border Payments and Reporting Plus) is the ISO 20022
migration for SWIFT interbank payments, mandatory from November 2025.

The compliance challenge: ISO 20022 XML messages are **large** (10–50KB vs old MT
messages at 1–5KB) and require **schema validation** — but adding 10ms of XML parsing
to every transaction is unacceptable.

### Streaming Parser vs DOM Parser

```
DOM parser (javax.xml / JAXB unmarshal):
  Reads entire XML → builds object tree in memory → returns root object
  - Allocates ~10–50 objects per message (Element, Attribute, String nodes)
  - GC pressure on 10M messages/month = significant GC churn
  - Latency: 1–5ms per message
  - Problem: 40% of latency in one FX platform was a DOM XML parser

Streaming parser (StAX — Streaming API for XML):
  Reads XML as event stream (START_ELEMENT, CHARACTERS, END_ELEMENT)
  - No DOM tree built; caller processes each token as it arrives
  - Zero intermediate object allocation (with pre-allocated char buffer)
  - Latency: 50–200µs per message
  - Problem: more code to write; no auto-mapping to POJOs

Binary codec (SBE / FlatBuffers / Protobuf):
  Not XML at all — fixed-offset binary encoding
  - Zero parsing; read fields at known byte offsets (like a C struct)
  - Latency: 5–20µs per message
  - Problem: only works end-to-end if all parties use the same schema
  - ISO 20022 mandates XML on external SWIFT wire; binary only valid internally
```

### Java StAX Streaming Pattern for ISO 20022

```java
// StAX streaming ISO 20022 pacs.008 (Credit Transfer) parser — zero DOM allocation
public class Pacs008StreamParser {
    private final XMLInputFactory factory = XMLInputFactory.newInstance();

    // Pre-allocated char buffer — reused across messages (zero GC on hot path)
    private final char[] charBuf = new char[4096];

    public CreditTransfer parse(InputStream xmlStream) throws XMLStreamException {
        XMLStreamReader reader = factory.createXMLStreamReader(xmlStream);
        CreditTransfer tx = new CreditTransfer();

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "InstrId":
                        tx.instructionId = reader.getElementText();  // no intermediate String[]
                        break;
                    case "IntrBkSttlmAmt":
                        tx.amount = parseLongFixedPoint(reader.getAttributeValue(null, "Ccy"),
                                                        reader.getElementText());
                        break;
                    case "CdtrAcct":
                        tx.creditorIban = parseChildText(reader, "Id");
                        break;
                }
            }
        }
        reader.close();
        return tx;
    }

    // Store amount as fixed-point long (e.g., 10000.00 EUR → 1_000_000L)
    // Avoids Double allocation — same zero-GC principle as FX rate cache above
    private long parseLongFixedPoint(String ccy, String amountStr) {
        return Math.round(Double.parseDouble(amountStr) * 100);
    }
}
```

### Validation Without Full Schema Load

Full XSD schema validation (via `javax.xml.validation.Validator`) loads the entire
schema into memory and validates every node — adds 5–20ms per message.

**Pattern: pre-compile validator once, validate critical fields inline:**

```java
// COLD PATH: compile schema once at startup
Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    .newSchema(getClass().getResource("/iso20022/pacs.008.xsd"));
Validator validator = schema.newValidator();   // thread-local, not shared

// HOT PATH: for throughput, validate only mandatory fields inline during StAX parse
// Full XSD validation only on exception path (suspicious amounts, missing BIC)
if (tx.amount > FAT_FINGER_LIMIT || !BIC_PATTERN.matcher(tx.creditorBic).matches()) {
    validator.validate(new StreamSource(rawXmlStream));   // full validate on suspect only
}
```

This is the same "validate at system boundaries only" principle as the pre-trade
risk checks in `Block3_SystemDesignTrading.java`.

### ISO 20022 Processing Pipeline (Low-Latency Architecture)

```
Inbound SWIFT / CBPR+ message (XML)
          │
          ▼
  ┌───────────────────┐
  │  StAX Stream Parse│  ~100–200µs (streaming, zero DOM)
  │  + inline validate│
  └────────┬──────────┘
           │  CreditTransfer POJO (fixed-point longs, no String amounts)
           ▼
  ┌───────────────────┐
  │  L1 Cache Lookup  │  ~100ns (Caffeine: BIC validation, account status)
  │  (Caffeine)       │
  └────────┬──────────┘
           │
           ▼
  ┌───────────────────┐
  │  Pre-Trade Risk   │  ~1µs (position limit, notional, rate limit — Block3)
  │  Check (in-mem)   │
  └────────┬──────────┘
           │
           ▼
  ┌───────────────────┐
  │  Write-Through    │  ~200µs Redis + async DB (write-behind for audit)
  │  Cache + DB       │
  └────────┬──────────┘
           │
           ▼
  ┌───────────────────┐
  │  SBE-Encode ACK   │  ~20ns (fixed-offset binary for internal services)
  │  → Aeron IPC      │  ~1µs delivery to downstream (settlement, reporting)
  └───────────────────┘

Total pipeline p50: ~500µs–1ms
Total pipeline p99: ~2–5ms (cache miss + DB write)
```

---

## FX-Specific Patterns

### Fixed-Point Arithmetic (Never Use `double` for Money)

```java
// WRONG — floating-point rounding error accumulates across millions of trades
double rate  = 1.08345;
double amount = 1_000_000.0;
double result = amount * rate;   // 1083450.0000000002 — rounding error in the cents!

// CORRECT — fixed-point: store as long, scale by 10^N
// EUR/USD rate 1.08345 → stored as 108345L (scale = 10^5 = 5 decimal places)
long rateFixed  = 108345L;        // 1.08345 × 10^5
long amountUSD  = 1_000_000_00L;  // $1,000,000.00 × 10^2 = $100,000,000 cents
long resultCents = (amountUSD * rateFixed) / 100_000L;
// resultCents = 108345000000 / 100000 = 1083450000 = $10,834,500.00 ✓

// Matches what FX systems do: ICE, Reuters, Bloomberg all use integer fixed-point internally
// Java: FastMoney from javax.money (JSR 354) or custom FixedPoint class
```

### Latency Budget for a Spot FX Trade

```
Component                          Budget    Technique
──────────────────────────────────────────────────────────
Receive FIX/ITCH market data        50µs     NIO / OpenOnload
Deserialise (SBE / StAX)           20µs     Zero-copy, streaming
FX rate lookup (L1 Caffeine)        1µs     In-process cache, fixed-point long
Pre-trade risk check                5µs     In-memory position map, AtomicLong
Order routing decision              5µs     Lock-free ring buffer
Order encode + send (SBE + Aeron)  10µs     Zero-allocation, busy-spin
Exchange ACK round-trip           500µs     Co-location, RDMA or Aeron UDP
──────────────────────────────────────────────────────────
Total p50:                        ~591µs
Total p99 (with cache miss):      ~2–3ms
```

### Spread Leakage from Latency

In spot FX, a market maker quotes a bid and offer. If the market moves before
the firm can update its quote, it trades at a **stale price** — this is spread leakage.

```
Quote lifetime:  the window between quoting and the market moving
  FX volatility: EUR/USD moves ~0.5 pip/second during NY session

  If system latency = 5ms → market can move 0.0025 pips in that window
  At $1M trade size: 0.0025 / 10000 × 1,000,000 = $0.25 leakage per trade

  At 10,000 trades/day: $2,500/day leakage
  If system latency = 50ms: $25,000/day leakage

  Every 1ms of unnecessary latency = ~$500/day leakage at this volume.
  This is why the GC optimisations in Block2 directly translate to P&L.
```

---

## Updated Codebase Map — Capital Markets Additions

| Topic | Where Covered |
|---|---|
| Distributed caching (Caffeine, Redis, Hazelcast) | HFT_LAYERS.md — this section |
| Write-through vs write-behind | HFT_LAYERS.md — this section |
| Hot-key avoidance (LongAdder, striped cache) | HFT_LAYERS.md — this section |
| JMH benchmarking discipline | HFT_LAYERS.md — this section |
| Gatling burst load testing | HFT_LAYERS.md — this section |
| ISO 20022 / CBPR+ StAX streaming parser | HFT_LAYERS.md — this section |
| Fixed-point arithmetic for FX | HFT_LAYERS.md — this section |
| FX spread leakage from latency | HFT_LAYERS.md — this section |
| Object pool (ExecutionReportPool) | `Block2_PerformanceOptimisation.java` |
| Pre-trade risk + circuit breaker | `Block3_SystemDesignTrading.java` |
| Zero-allocation hot path | `HotPathAllocationDemo.java` |
| GC flags (-XX:+UseZGC) | `Block2_PerformanceOptimisation.java` §4 |

---

---

# Book Reference: "Java: How Low Can You Go?" — Zahid Hossain (2025)

*Source: Zahid Hossain, Quanteam UK. Covers Java 24+, RFQ systems, HFT, OS/network tuning,
KDB+. Author: 18 years at Citi, Credit Suisse, Barclays, Bloomberg, State Street.*

The sections below capture content from the book that extends what is already in this file.

---

## Java Types — Low-Latency Traps

### Primitive Types vs Wrapper Types (Memory Layout)

Primitives exist on the **stack or inline in arrays** — no object header, no GC,
no pointer indirection. Wrapper types are heap objects with a 16-byte header.

```
Primitive long:   8 bytes, lives on stack or in array slot — zero GC pressure
Long (wrapper):   16B header + 8B value = 24B on heap — GC must scan and move it

long[] prices = new long[1000];    // 8,000 bytes, contiguous, cache-friendly
Long[] prices = new Long[1000];    // 1000 heap objects + 1000 pointers — 24,000+ bytes
                                   // 1000 GC roots to scan per collection
```

**Rule:** Never use wrapper types on the hot path. Use `long`, `int`, `double`.
Use `LongAdder` / `AtomicLong` for shared counters — never `AtomicReference<Long>`.

### Autoboxing Trap

Java silently boxes primitives when stored in collections or passed to generic methods.
This is invisible in code but creates heap garbage on every call.

```java
// SILENT AUTOBOXING — allocates a new Long object on every loop iteration
Map<String, Long> positionMap = new HashMap<>();
for (int i = 0; i < 1_000_000; i++) {
    positionMap.put("EURUSD", positionMap.getOrDefault("EURUSD", 0L) + 1L);
    //                                                              ^ unbox  ^ rebox
    // Each iteration: getOrDefault unboxes Long→long, +1 boxes long→Long = 1 alloc/iter
    // 1M iterations = 1M Long allocations → GC pressure spike
}

// ZERO ALLOCATION — primitive map (Eclipse Collections or Agrona)
MutableLongLongMap positions = LongLongMaps.mutable.empty();  // Eclipse Collections
positions.addToValue(ccyPairEncoded, 1L);   // no boxing, no allocation
```

### JVM Integer Cache — Dangerous Equality Trap

The JVM caches `Integer` instances for values **-128 to 127** via `Integer.valueOf()`.
Outside this range, each call creates a new object — `==` comparison breaks silently.

```java
Integer a = 127;   // cached: a == b is true (same object reference)
Integer b = 127;
System.out.println(a == b);   // true  ← correct by accident

Integer c = 128;   // NOT cached: new Integer object
Integer d = 128;
System.out.println(c == d);   // FALSE ← silent bug in production
// Always use .equals() for wrapper comparison, or better: use primitives

// JVM flag to extend cache (useful if IDs range 0–9999):
// -XX:AutoBoxCacheMax=9999
```

### String: Intern, Dedup, Avoid

Strings are the #1 source of heap churn in trading systems:
- FIX protocol field values (symbol, side, ordType) are repeatedly created as `String`
- JSON/XML deserialisation creates millions of short-lived `String` objects

```
String interning:  String.intern() → puts string in JVM string table (PermGen/Metaspace)
                   Avoids duplicate String objects; == comparison valid for interned strings
                   Cost: intern() itself is slow (~1µs); good for static symbols only

String dedup:      -XX:+UseStringDeduplication (G1GC only)
                   GC identifies duplicate char[] arrays and shares them
                   Reduces heap; no code change; doesn't help allocation rate

Best approach:     encode symbol as long (see HotPathAllocationDemo.java symbolToLong)
                   Replace "EURUSD" → 4704084L — zero String, zero GC
```

JVM String flags:
```bash
-XX:+UseStringDeduplication          # G1GC only; reduces live heap
-XX:+OptimizeStringConcat            # JIT optimises s1+s2+s3 chains
-XX:StringTableSize=1000003          # larger intern table reduces collisions
-XX:+PrintStringTableStatistics      # diagnostic: see intern table saturation
```

### Java Records for Low-Latency DTOs

`record` (Java 16+) is a compact immutable data carrier. Unlike a regular class,
it has no setter, no mutable state — safe to share between threads without locking.

```java
// Traditional POJO — mutable, boilerplate, risk of accidental mutation
class OrderEvent {
    private long orderId;
    private String symbol;   // String allocation!
    private double price;
    // getters/setters...
}

// Record — immutable, compact, JIT-inlineable, value-like behaviour
record OrderEvent(long orderId, long symbolEncoded, long priceFixed) {}
// 3 primitive longs → 24 bytes total; JIT can inline as value type in Valhalla
// Immutable → safe to publish via volatile without defensive copy
```

### BitSet for Boolean Flags

A `boolean[]` of 1000 elements uses **1000 bytes** (JVM uses 1 byte per boolean).
A `BitSet` of 1000 booleans uses **128 bytes** (1 bit per flag).
On a hot path checking 64+ feature flags per message, the cache line difference matters.

```java
BitSet tradingEnabled = new BitSet(512);   // 512 symbols, 64 bytes (1 cache line!)
tradingEnabled.set(symbolIndex, true);
if (tradingEnabled.get(symbolIndex)) { ... }  // single cache-line read for 512 symbols
```

---

## Collections: Internal Mechanics and Hot-Path Rules

### HashMap Internals — The Resizing Trap

HashMap internal structure:
- `Node<K,V>[] table` — array of buckets (initial capacity: **16**)
- Load factor: **0.75** — resize triggered at `capacity × 0.75` entries
- Resize doubles the array and **rehashes all entries** — O(n) pause

```java
// WRONG on hot path: HashMap with default capacity
Map<String, Long> positions = new HashMap<>();  // capacity=16, resize at 12 entries
// If 13 orders come in → HashMap.resize() called mid-trade → O(n) rehash → latency spike

// CORRECT: pre-size to expected capacity before market open (cold path)
int expectedSymbols = 500;
Map<String, Long> positions = new HashMap<>(expectedSymbols * 2, 0.5f);
// capacity = 1000, load factor = 0.5 → resize at 500 entries → never resizes during trading

// JVM: HashMap treeifies a bucket when it has > 8 entries (TREEIFY_THRESHOLD)
// A treeified bucket uses TreeNode (48 bytes) vs Node (32 bytes)
// Pre-sizing avoids hash collisions that cause treeification
```

### ConcurrentHashMap — Java 8+ Segment → Node Locking

Java 7: ConcurrentHashMap used **16 Segment locks** (16× parallelism max).
Java 8+: Uses **per-node CAS + synchronized on first bucket node** — no Segment overhead.

```
Java 7 ConcurrentHashMap:
  16 segments, each a ReentrantLock
  Max concurrent writers: 16
  Lock granularity: segment (covers many keys)

Java 8+ ConcurrentHashMap:
  CAS for empty bucket (first entry — no lock at all)
  synchronized(node) for non-empty bucket (lock is on the single bucket head node)
  Max concurrent writers: number of buckets (up to 16M)
  Lock granularity: single hash bucket
```

Synchronization trick: `compute()`, `computeIfAbsent()`, `merge()` are **atomic** —
the entire lambda runs under the bucket's synchronized block. Use these instead of
get-then-put sequences which are not atomic.

### Fail-Fast vs Fail-Safe Iteration

| Iterator type | Example | Behaviour | Hot-path safe? |
|---|---|---|---|
| **Fail-Fast** | `HashMap`, `ArrayList` | Throws `ConcurrentModificationException` if modified during iteration | No |
| **Fail-Safe** | `CopyOnWriteArrayList`, `ConcurrentHashMap` | Iterates a snapshot; modifications don't throw | Read-heavy only |

**CopyOnWriteArrayList pattern for HFT listeners:**
```java
// CopyOnWriteArrayList: every write copies the entire array
// Reads are lock-free (iterate the snapshot reference)
// Use when: reads >> writes (e.g., market data subscriber list, rarely changes)
CopyOnWriteArrayList<MarketDataListener> listeners = new CopyOnWriteArrayList<>();
// Hot path (thousands of market data ticks/sec):
for (MarketDataListener l : listeners) { l.onTick(tick); }  // lock-free, no ConcurrentModEx
// Cold path (subscriber joins/leaves):
listeners.add(newListener);   // copies array — acceptable if rare
```

### Agrona — Production-Grade Low-Latency Collections

Real HFT systems replace Java's built-in collections with **Agrona** (Real Logic,
same team as Aeron and SBE) — primitive-specialised, off-heap-capable collections:

| Agrona class | Replaces | Advantage |
|---|---|---|
| `Int2IntHashMap` | `HashMap<Integer,Integer>` | Primitive, no boxing, open-addressing |
| `Long2LongHashMap` | `HashMap<Long,Long>` | Symbol→position map with zero GC |
| `ManyToOneConcurrentArrayQueue` | `LinkedBlockingQueue` | Lock-free, array-backed, bounded |
| `OneToOneConcurrentArrayQueue` | `ArrayBlockingQueue` | SPSC, ultra-low latency |
| `UnsafeBuffer` | `ByteBuffer` | Direct Unsafe access, no bounds check overhead |
| `AtomicBuffer` | `AtomicLong` operations on buffer | Memory-mapped file with atomic ops |

```java
// Agrona Long2LongHashMap: no boxing, open-addressing (no linked list chains)
Long2LongHashMap symbolPosition = new Long2LongHashMap(-1L); // -1 = missing sentinel
long pos = symbolPosition.get(symbolEncoded);    // zero allocation lookup
symbolPosition.put(symbolEncoded, pos + fillQty); // zero allocation update
```

---

## Lock States and Synchronization Internals

### The Four States of a Java Lock (Mark Word)

Every Java object has a 64-bit (or 32-bit compressed) **mark word** in its header
that stores GC age, identity hash code, and — crucially — lock state:

```
Mark word lock state machine:
                                              Mark Word bits
                                              ┌─────────────────────────────┐
  Thread A creates object                     │ age | 0 | 01 (unlocked)    │
  Thread A enters synchronized(obj) first     │ thread-ID | 1 | 01 (biased)│ ← biased lock
  Thread B tries synchronized(obj)            │ LockRecord ptr | 00 (thin) │ ← thin lock (CAS)
  High contention detected                    │ Monitor ptr | 10 (fat)     │ ← fat lock (OS mutex)
                                              └─────────────────────────────┘

Biased lock:  Thread A "owns" the lock for free — future entries cost ~1ns
              (just check thread-ID in mark word; no CAS needed)
              Revoked when another thread tries to lock: ~200ns safepoint cost

Thin lock:    Single CAS on mark word; no OS involvement; ~10–20ns
              Spin briefly waiting; escalates to fat lock if contention persists

Fat lock:     OS mutex (futex on Linux); thread blocks in kernel; ~1µs+ wake-up
              Once inflated, never deflates back to thin/biased

Note: Biased locking removed in Java 17 (-XX:-UseBiasedLocking no longer needed)
      because benchmark showed safepoint overhead of revocation exceeded the savings
      in modern multi-threaded trading code.
```

### CAS vs ReentrantLock — When to Use Which

| Scenario | Recommended | Why |
|---|---|---|
| Single counter, one thread writes | `volatile long` + lazySet | No contention, no CAS needed |
| Single counter, few threads | `AtomicLong.compareAndSet()` | CAS, no OS, ~10–20ns |
| Many threads updating one counter | `LongAdder` | Striped cells, ~5ns per increment |
| Complex multi-step critical section | `ReentrantLock` | Can timeout, can interrupt |
| Read-heavy, infrequent writes | `StampedLock` (optimistic read) | Readers never block |
| Always reader-heavy | `ReadWriteLock` | Writers wait for readers to drain |

### StampedLock — Optimistic Reads (Java 8+)

`StampedLock` is the highest-performance Java read-write lock for read-heavy workloads
like reading an FX rate that updates every 100ms but is read 10,000×/sec.

```java
StampedLock lock = new StampedLock();
long rateFixed;  // current EUR/USD rate as fixed-point

// HOT PATH: optimistic read — NO lock acquisition if no write is happening
long stamp = lock.tryOptimisticRead();   // ~3ns — just reads a version counter
long rate = rateFixed;                   // read the value
if (!lock.validate(stamp)) {            // check if a write happened during our read
    // write happened — fall back to proper read lock
    stamp = lock.readLock();
    try { rate = rateFixed; }
    finally { lock.unlockRead(stamp); }
}
// Optimistic path: 2 memory reads + 1 validate = ~5–10ns total
// vs synchronized block: ~20–100ns (even uncontended)

// COLD PATH: rate update by market data thread
long writeStamp = lock.writeLock();
try { rateFixed = newRate; }
finally { lock.unlockWrite(writeStamp); }
```

### Epsilon GC — The No-Op Garbage Collector

`-XX:+UseEpsilonGC` (Java 11+, requires `-XX:+UnlockExperimentalVMOptions`)

Epsilon GC allocates memory but **never collects**. The JVM crashes with OOM when
the heap fills. Used in HFT when:

1. **Zero-GC design is verified** — all hot-path allocation is provably zero (use JFR
   allocation profiler to confirm before switching to Epsilon)
2. **Restart on schedule** — trading session has defined start/end; JVM restarts between
   sessions before heap fills
3. **Off-heap for all persistent state** — order book, positions, prices all in
   DirectByteBuffer; only JVM internals on heap

```bash
# Epsilon GC — use only if zero-allocation hot path is confirmed
java -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC \
     -Xms512m -Xmx512m \           # fixed heap, pre-touched
     -XX:+AlwaysPreTouch \
     com.example.MatchingEngine
# JVM will crash after ~4-6 hours if any allocation leaks through
# This forces engineers to fix zero-GC discipline or it crashes in prod
```

---

## Zero-GC Java: Complete Checklist

Based on the book's production patterns from Citi, Credit Suisse, Barclays, Bloomberg:

```
COLD PATH (before market open — allocate everything):
  ✓ Pre-size all HashMaps: new HashMap<>(capacity * 2, 0.5f)
  ✓ Pre-allocate object pools (ExecutionReport, MarketTick, Order)
  ✓ Allocate all DirectByteBuffers (order book, ring buffers)
  ✓ Load all static data (symbols, BICs, currency codes)
  ✓ Warm up JIT: send 10,000 synthetic orders through all code paths
  ✓ Force GC once: System.gc() + Thread.sleep(500) to start clean
  ✓ Verify with JFR: zero new allocations on hot path after warm-up

HOT PATH (during trading — zero allocation):
  ✓ Primitives only: long, int, double — never Long, Integer, Double
  ✓ No new SomeThing() — all objects from pool or pre-allocated
  ✓ No String concatenation: "sym=" + symbol → encodes to long
  ✓ No StringBuilder unless pre-allocated ThreadLocal instance
  ✓ No lambdas or anonymous classes: every closure allocates a new object
      HOT:  for (Listener l : listeners) { l.onTick(tick); }        ← no allocation
      COLD: listeners.forEach(l -> l.onTick(tick));                  ← may allocate
  ✓ No exception throwing on hot path: exceptions capture stack trace (expensive)
      Use result codes (int) or sentinel values instead of try/catch
  ✓ No varargs methods: int... args → allocates int[] on every call
  ✓ No autoboxing: map.put(key, value) where value is primitive → boxes to wrapper
  ✓ Reuse Charset encoder/decoder:
      ThreadLocal<CharsetEncoder> encoder = ThreadLocal.withInitial(() ->
          StandardCharsets.US_ASCII.newEncoder());
  ✓ Reuse StringBuilders (for logging/diagnostics, not matching):
      ThreadLocal<StringBuilder> sb = ThreadLocal.withInitial(() -> new StringBuilder(256));
  ✓ Avoid Iterator allocation: use indexed loops on arrays/lists
      for (int i = 0; i < list.size(); i++) {    // no Iterator object
  ✓ No Collections.unmodifiableList() wrappers in hot path (allocates wrapper)

VERIFICATION TOOLS:
  JFR allocation profiling:   java -XX:StartFlightRecording=filename=alloc.jfr,settings=profile
  async-profiler (alloc):     ./profiler.sh -e alloc -d 30 -f alloc.html PID
  JMH + -prof gc:             confirms bytes allocated per operation = 0
```

---

## OS (Linux) Tuning — Complete Checklist

### The Concept of Jitter

Latency **average** is not the enemy — **jitter** (variability) is.
A system with p50=5µs and p99.9=500µs is worse for trading than p50=20µs p99.9=25µs.

```
Sources of jitter on a default Linux server:
  1. Kernel timer tick: HZ=250 → timer interrupt every 4ms (interrupts trading thread)
  2. CPU frequency scaling: OS lowers clock when idle → frequency ramp-up on burst = latency
  3. C-states (power saving): CPU parks cores in deep sleep → wake-up = 100µs+
  4. NUMA cross-socket: thread on CPU0 reads memory on NUMA node 1 → +70ns every access
  5. NIC interrupts on trading CPU: every packet interrupt evicts trading thread's cache
  6. GC threads: even ZGC/Shenandoah use safepoints that interrupt all threads briefly
  7. OS scheduler: background tasks occasionally preempt the trading thread
```

### Measure Before You Tune

```bash
# Install cyclictest (RT-tests package) to measure OS timer jitter
cyclictest -p 99 -t 1 -n -i 200 -l 100000
# Output: Min/Avg/Max latency from timer wake-up
# Untuned server: Max = 500µs–2ms  (kernel jitter)
# Tuned server:   Max = 5–20µs

# perf stat — see context switches, cache misses, CPU migrations
perf stat -e context-switches,cpu-migrations,cache-misses -p <PID> sleep 10

# numactl — see NUMA topology
numactl --hardware

# /proc/interrupts — see which CPU is handling NIC interrupts
cat /proc/interrupts | grep -i eth
```

### BIOS Configuration (Must Do Before OS Tuning)

```
Disable in BIOS / UEFI:
  ✓ CPU C-states: C1E, C3, C6, C7  → deep sleep states add wake-up latency
    BIOS: "CPU C State Control" → Disabled
    Kernel: intel_idle.max_cstate=0 or processor.max_cstate=1

  ✓ P-states / CPU frequency scaling → OS throttles clock to save power
    BIOS: "CPU Power Management" → Disabled / set to Maximum Performance
    Kernel: cpufreq governor = performance
    echo performance > /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor

  ✓ Turbo Boost (Intel) / Precision Boost (AMD)
    Turbo adds non-deterministic frequency spikes → latency variance
    BIOS: "Intel Turbo Boost Technology" → Disabled
    Note: Some firms keep Turbo ON and accept the variance for higher peak throughput

  ✓ Hyper-Threading (SMT)
    Two logical threads share physical core resources (L1 cache, execution units)
    HFT core should have 100% of physical resources → disable HT for trading cores
    BIOS: "Hyper-Threading" → Disabled (or selective isolcpus for HT pairs)
```

### CPU Isolation (isolcpus + nohz_full)

```bash
# /etc/default/grub — isolate CPUs 2,3,4,5 from OS scheduler entirely
GRUB_CMDLINE_LINUX="isolcpus=2,3,4,5 nohz_full=2,3,4,5 rcu_nocbs=2,3,4,5"
# isolcpus: scheduler never assigns tasks to these CPUs
# nohz_full: disable kernel timer tick on these CPUs (tickless mode)
# rcu_nocbs: move RCU callbacks off these CPUs

# Pin trading thread to isolated CPU
taskset -c 2 java -jar trading-engine.jar
# Or in Java:
// OpenHFT / JVM CPU affinity library:
// AffinitySupport.setAffinity(1L << 2);  // pin to CPU 2
```

### Cage Your GC Thread

Even ZGC uses GC threads that can interfere with trading threads via cache eviction.
Pin GC threads to **non-trading CPUs**:

```bash
# Assign GC threads to CPUs 0,1 (not isolated); trading thread on CPU 2
-XX:+UseZGC
-XX:ZConcGCThreads=2
-XX:GCTaskAffinity=0,1    # GC threads on CPU 0 and 1 only

# Combined with isolcpus=2,3 → GC never runs on trading CPUs
```

### IRQ Affinity — Move NIC Interrupts Off Trading CPUs

```bash
# Find NIC interrupt numbers (e.g., interface eth0)
cat /proc/interrupts | grep -i eth0

# Pin NIC interrupt to CPU 1 (non-trading CPU)
echo 2 > /proc/irq/120/smp_affinity   # bitmask: CPU 1 = bit 1 = value 2

# For modern RSS (Receive Side Scaling) NICs with multiple RX queues:
for IRQ in $(grep eth0 /proc/interrupts | awk -F: '{print $1}'); do
    echo 2 > /proc/irq/$IRQ/smp_affinity
done
# Trading CPU (CPU 2, bitmask=4) now NEVER handles NIC interrupts
```

### Red Hat Linux Tuned Profiles

```bash
# Install and apply the latency-performance tuned profile
tuned-adm profile latency-performance
# Sets: no power saving, no CPU frequency scaling, IRQ balancing off

# Ultra-low latency profile (requires rt-kernel):
tuned-adm profile realtime
# Additional: disables transparent huge pages merging, sets CPU governor, nohz

# Verify active profile:
tuned-adm active
```

### Hugepages

```bash
# Hugepages (2MB) reduce TLB entries needed for large heaps
# Without hugepages: 4GB heap = 1M TLB entries (4KB pages) → frequent TLB misses
# With hugepages:    4GB heap = 2048 TLB entries (2MB pages) → near-zero TLB misses

# Reserve 2048 × 2MB hugepages = 4GB
echo 2048 > /sys/kernel/mm/hugepages/hugepages-2048kB/nr_hugepages

# Tell JVM to use hugepages:
-XX:+UseLargePages          # use OS hugepages for Java heap
-XX:+UseTransparentHugePages # let kernel merge small pages (less control)
-XX:LargePageSizeInBytes=2m  # explicit 2MB pages

# DPDK / Aeron also need hugepages for their shared memory regions:
-XX:+AlwaysPreTouch         # fault all hugepages at startup; no page fault during trading
```

---

## Java 24+ Projects — The Next Generation

### Project Valhalla — Value Classes

Valhalla introduces **value classes** (Java 24+): objects without identity. The JVM
can store them **inline** in arrays and on the stack — no heap allocation, no GC.

```java
// Current Java: Order object on heap (16B header + fields)
class Order { long id; long price; long qty; char side; }
Order[] book = new Order[1000];  // 1000 heap objects + 1000 pointers

// Valhalla value class: stored inline like a struct (NO heap, NO GC)
value class Order { long id; long price; long qty; char side; }
Order[] book = new Order[1000];  // contiguous memory: 1000 × 25 bytes = 25KB
                                  // ONE allocation, ONE cache region

// Why this matters for HFT:
// Current order book with 1000 levels: 1000 heap allocations, pointer chasing
// Valhalla order book: 25KB contiguous array, 2–4 cache line reads for 100 levels
```

Valhalla also enables **primitive generics**: `List<long>` instead of `List<Long>` —
no boxing, no wrapper objects, zero GC for primitive collections.

### Project Leydon — AOT and Faster Startup

Leydon (Java 24+) addresses JVM startup and warm-up time through:

1. **Ahead-of-Time (AOT) compilation**: compile hot methods at build time instead of
   waiting for JIT to warm up
2. **Static image generation**: create a pre-initialized JVM snapshot

```
Current JVM startup for trading engine:
  JVM start → class loading → JIT C1 warmup → JIT C2 compilation (after 10,000 iterations)
  Time to peak performance: 30s–5 minutes
  Risk: latency spike during morning warm-up before market open

Leydon AOT:
  Build time: compile hot methods to native code, save to .so
  Runtime: JVM loads pre-compiled methods → near-peak performance from first request
  Startup to peak: seconds, not minutes
  Benefit: exchange morning session starts immediately at peak performance
```

### Project Panama — Foreign Function & Memory API (JDK 22 stable)

Panama replaces JNI for calling native code (C libraries like libibverbs, DPDK).
Safer, no JNI boilerplate, direct memory access with clear lifecycle.

```java
// JNI (old way): requires C header, C stub file, JNI_OnLoad, reflection...
// Panama Foreign Function API (Java 22 stable):
try (Arena arena = Arena.ofConfined()) {
    // Allocate off-heap struct for RDMA work request
    MemorySegment wr = arena.allocate(IbvSendWr.LAYOUT);
    IbvSendWr.opcode(wr, IBV_WR_RDMA_WRITE);
    IbvSendWr.remoteAddr(wr, remoteBufferAddress);
    IbvSendWr.rkey(wr, remoteMrRkey);

    // Call C function directly — no JNI stub file needed
    MethodHandle ibvPostSend = linker.downcallHandle(
        lookup.find("ibv_post_send").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    ibvPostSend.invoke(qp, wr, badWr);
}
// Arena ensures off-heap memory freed when try-block exits — no manual free()
```

### Project Loom — Virtual Threads (JDK 21 stable)

Virtual threads mount on carrier platform threads and unmount on blocking operations.
They enable millions of concurrent threads with O(1) memory per thread.

```
Platform thread:  OS thread, ~2MB stack, ~1µs context switch, OS-scheduled
Virtual thread:   JVM-managed, ~few KB stack, ~100ns context switch, cooperative

Use virtual threads for:
  ✓ High-concurrency REST endpoints (many simultaneous requests, each blocking on DB)
  ✓ FIX session handlers (one virtual thread per counterparty connection)
  ✓ Risk calculation fan-out (1000 scenarios in parallel, each waiting on results)

Do NOT use virtual threads for HFT hot path:
  ✗ Matching engine inner loop: must be pinned platform thread (CPU affinity)
  ✗ Busy-spin subscribers: virtual thread cannot hold CPU exclusively (it yields)
  ✗ Any code using synchronized blocks on carrier pinning (J21 pinning bug)
  ✗ Code requiring NUMA-local memory pinning (virtual threads can migrate)
```

### Project Babylon — Code Reflection and GPU Compute

Babylon (preview, Java 24+) enables **Java code to be compiled to GPU kernels (OpenCL/CUDA)**
via a Code Reflection API. The compiler can inspect method bytecodes as a structured IR
and translate to GPU execution.

```
HFT use case: Monte Carlo option pricing
  Current:   Java → JNI → CUDA C++ kernel → GPU
  Babylon:   Java → Code Reflection API → GPU kernel (no C++ required)

  1M option paths priced in parallel on GPU: 50ms → 5ms (GPU parallelism)
  Risk calculation for 100K positions: seconds → milliseconds
```

---

## Trading System Architectures

### RFQ — Request for Quote System

An RFQ (Request for Quote) system is used for **OTC (over-the-counter) trading**
where a client requests prices from multiple dealers rather than hitting an exchange.

```
RFQ Flow:

Client (Institutional)
    │  RFQ: "price 10M EUR/USD spot"
    ▼
RFQ Distribution Engine
    │  broadcast to 3–8 market makers simultaneously (fan-out)
    │  CompletableFuture.allOf() / timeout: 200ms SLA
    ▼
Market Maker A ──► Quote: 1.08340 / 1.08355 (bid/ask)
Market Maker B ──► Quote: 1.08342 / 1.08357
Market Maker C ──► Quote: 1.08338 / 1.08353  ← best offer
    │
    ▼
Quote Aggregation Engine
    │  collect responses, rank by price, manage timeouts
    │  best offer displayed to client within 200ms
    ▼
Client selects Quote C
    │  DEAL: buy 10M EUR at 1.08353
    ▼
Execution Engine
    │  send execution to Market Maker C
    ▼
Post-Trade: settlement, reporting, position update

Latency budget:
  Distribution fan-out:  <1ms (Aeron UDP multicast)
  Maker pricing logic:   <5ms (L1 cache + VWAP + risk)
  Quote collection:      <200ms timeout
  Execution:             <10ms
```

### Market Making Architecture

A market maker **continuously quotes bid and ask prices** across hundreds of symbols,
profiting from the spread while managing inventory risk.

```
Market Making Components:

  Market Data Feed ──► Pricing Engine ──► Quote Engine ──► Exchange
                              │
                        ┌─────▼──────┐
                        │  Inventory  │ ← current position in symbol
                        │  Risk Mgr  │ ← skews quotes if long/short
                        └─────┬──────┘
                              │  skew signal
                        ┌─────▼──────┐
                        │ Fair Value  │ ← mid-price estimate
                        │ Calculator │ ← VWAP, EMA of trades
                        └─────┬──────┘
                              │
                        ┌─────▼──────┐
                        │  Spread    │ ← bid = FV - halfSpread - skew
                        │  Engine    │   ask = FV + halfSpread + skew
                        └─────┬──────┘
                              │  bid/ask quotes
                        ┌─────▼──────┐
                        │ Quote Mgr  │ ← cancel stale, resend on delta
                        └─────┬──────┘
                              │  NewOrderSingle (FIX 4.2)
                        Exchange Matching Engine

Lead Market Maker (LMM):
  Exchange designates an LMM who must quote within a tight spread (e.g., ±0.5 tick)
  in exchange for lower fees or rebates. LMM must be present >95% of session time.

Quote lifetime: typically 500ms–2s; cancelled and resubmitted as market moves.
```

### How Exchanges Match Orders — Price-Time vs Pro-Rata

```
Price-Time Priority (FIFO):
  Orders at same price matched in time order (first in, first out)
  Used by: CME for futures, NYSE, NASDAQ equities
  HFT advantage: co-location and low latency → get to front of queue

  BUY QUEUE at $100.00:  Order1(100) → Order2(50) → Order3(200)
  SELL arrives: 120 shares → fills Order1(100) fully, Order2(20) partial

Pro-Rata (Size Priority):
  Orders at same price matched proportional to their size
  Used by: CME options, some bond markets
  HFT advantage: large order size → better fill ratio

  BUY QUEUE at $100.00:  Order1(100) → Order2(50) → Order3(200)
  Total: 350 shares. SELL arrives: 70 shares
  Order1 gets: 70 × 100/350 = 20 shares
  Order2 gets: 70 × 50/350  = 10 shares
  Order3 gets: 70 × 200/350 = 40 shares

NBBO (National Best Bid and Offer):
  US regulation requires brokers to route orders to the exchange
  currently showing the best bid or offer (Reg NMS)
  Smart Order Router uses NBBO to decide which venue to hit
```

### Dark Pools

A dark pool is a private trading venue where orders are not displayed to the public
before execution (no pre-trade transparency). Post-trade reporting is required.

```
Why dark pools exist:
  Large institutional orders (e.g., buy 5M shares of AAPL) cannot go to a lit exchange
  without "market impact" — the order book moves against the buyer as the order fills.
  Dark pools allow block trades without telegraphing intent to HFT algos.

Matching mechanisms:
  MIDPOINT: trade at midpoint of NBBO best bid/ask — no spread cost for either party
  VWAP:     match at VWAP over the session — used for algorithmic benchmarks
  LIMIT:    buyer specifies max price; only fills if contra order matches

Major dark pools (as of 2025):
  Credit Suisse Crossfinder (now UBS): largest ATS by volume
  Goldman Sachs Sigma X
  UBS ATS
  IEX (uses 350µs "speed bump" to disadvantage HFT)
  Liquidnet (buy-side only, block trades)

Regulatory: MiFID II (EU), Reg ATS (US) — limits dark trading to <8% of stock volume
            before circuit breaker triggers mandatory lit execution
```

### Smart Order Router (SOR)

A Smart Order Router (SOR) dynamically chooses **which venue** to send each order
to achieve best execution (price, speed, fill probability).

```
SOR Decision Factors:
  1. Price:  venue showing best NBBO bid/ask for the symbol
  2. Size:   venue with sufficient liquidity to fill without slippage
  3. Speed:  venue latency (co-lo advantage) vs price improvement
  4. Fee:    maker/taker fee schedule; rebate optimization
  5. Dark:   check dark pools for large orders first (avoid market impact)

SOR Algorithm (simplified):
  1. Receive client order: BUY 50,000 AAPL
  2. Query NBBO: NYSE $150.10, NASDAQ $150.10, BATS $150.11
  3. Check dark pool: Crossfinder has 30,000 contra at midpoint ($150.105)
  4. Route 30,000 to Crossfinder (dark, midpoint, no spread cost)
  5. Route 20,000 to NYSE (best lit price, co-lo to minimize slippage)
  6. Monitor fills; if NYSE partial, reroute residual to NASDAQ

Drop Copy:
  Regulatory requirement: all order/execution reports sent to a separate
  monitoring system in real-time for compliance and risk surveillance.
  Drop Copy = shadow copy of every FIX message sent to regulators/prime broker.

SOR latency target: <500µs decision + routing (venue selection is in-memory lookup)
```

### Algorithmic Execution Strategies

| Strategy | What it does | Use case |
|---|---|---|
| **VWAP** | Trade at volume-weighted avg price over session | Minimize market impact on large order |
| **TWAP** | Trade equal slices at equal time intervals | Reduce timing risk (not volume-weighted) |
| **POV** | Participate at X% of market volume | Stay below radar; flow with market |
| **IS (Implementation Shortfall)** | Minimize gap between decision price and execution price | Minimize delay cost on urgent orders |
| **Arrival Price** | Target price at time order arrives | Benchmark: did we improve on the arrival price? |

```
POV (Participation of Volume) example:
  Target: buy 100,000 shares at 20% participation rate
  Market trades 500 shares → algo buys 100 shares (20%)
  Market trades 1000 shares → algo buys 200 shares (20%)
  Total when 500,000 shares traded → algo buys 100,000 shares ✓

  Risk: if market volume is low (e.g., holiday), order takes all day
  Benefit: algo never "moves the market" — always 20% of flow
```

---

## KDB+ — The Low-Latency Time-Series Database

### Why KDB+ in HFT

Standard relational databases (PostgreSQL, Oracle) store data **row-by-row**.
Time-series analytics require **column-by-column** operations: "give me all prices
for AAPL over the last hour" touches only the `price` column — but a row DB reads
entire rows including symbol, size, side, order ID etc.

KDB+ stores data **column-by-column in RAM**:
```
Row-based DB:
  AAPL, 150.10, 100, BUY, 2025-01-01 09:00:01
  AAPL, 150.11, 200, SELL, 2025-01-01 09:00:02
  Read all prices: scan entire row store → reads symbol, size, side unnecessarily

KDB+ columnar:
  price column:  150.10, 150.11, 150.12, 150.09, ...  (contiguous float array in RAM)
  Select prices:  read one contiguous float array → fits entirely in L3 cache
  VWAP query:    sum(price * size) % sum(size) → vectorised operation on two arrays
```

### KDB+ Architecture in Trading

```
                    Exchange Feed (ITCH/OPRA/FIX)
                              │
                    ┌─────────▼─────────┐
                    │    Tickerplant    │  pub/sub message bus; log all ticks
                    │  (kdb+ process)   │  persists raw data to HDB
                    └─────┬─────────┬───┘
                          │         │
              ┌───────────▼──┐   ┌──▼───────────┐
              │  Real-Time   │   │  Historical   │
              │  Subscriber  │   │  Database     │
              │  (RDB)       │   │  (HDB)        │
              │  in-memory   │   │  on NVMe SSD  │
              │  today's data│   │  years of data│
              └──────┬───────┘   └──────┬────────┘
                     │                  │
              ┌──────▼──────────────────▼────────┐
              │         Analytics / Gateway       │
              │  VWAP, Greeks, intraday risk,     │
              │  rolling stats, signal generation │
              └───────────────────────────────────┘

Citi Equity Derivatives (author's experience):
  kdb+ RDB: intraday Greeks monitoring, real-time P&L, market-sensitive signals
  Query latency: <1ms for complex VWAP/rolling stats on millions of ticks
```

### Why KDB+ is Fast

1. **Columnar in-memory**: column arrays fit in L3 cache; SIMD vectorized operations
2. **Q language**: array-oriented, no row loops — `sum price * size` operates on full arrays
3. **Compression**: intelligent LZ4/gzip for HDB; decompresses at memory bandwidth speed
4. **Temporal partitioning**: HDB split by date; query for "today" touches one partition
5. **IPC**: binary protocol over TCP with zero-copy buffer sharing between kdb+ processes

### Q Language Quick Reference

```q
/ Load market data table
trade:([] time:`timestamp$(); sym:`symbol$(); price:`float$(); size:`int$())

/ VWAP per symbol — vectorized, no loop
select vwap: sum[price * size] % sum size by sym from trade

/ 5-period moving average of bid price
select ma5: 5 mavg bid by sym from quote

/ Time-bucketed VWAP (1-minute bars)
select vwap: sum[price*size] % sum size by sym, 1 xbar time.minute from trade

/ Rolling 1-hour VWAP — last 3600 seconds of data
select from trade where time > .z.p - 0D01:00

/ Greeks real-time monitoring (Citi Equity Derivatives use case)
select sym, delta:sum delta, gamma:sum gamma, vega:sum vega by sym from greeks
/ Returns risk exposures per symbol in <1ms even on 100K position records
```

### KDB+ vs Other Time-Series Databases

| Database | Latency (query 1M rows) | Use in Finance | Notes |
|---|---|---|---|
| KDB+ | < 1ms | Goldman, Barclays, Citi, JPMorgan | Industry standard; commercial licence |
| InfluxDB | 50–500ms | Monitoring, metrics | Not suited for tick analytics |
| TimescaleDB | 10–100ms | General time-series | PostgreSQL extension; easier SQL |
| DuckDB | 5–50ms | Analytical OLAP | Columnar, open-source; emerging in quant |
| Arctic (Man Group) | 5–50ms | Pandas + MongoDB | Python-friendly; good for backtesting |
| Chronicle Map | < 1µs | HFT (Java) | Off-heap, not a DB but used for live data |

---

## Market Data Feed — UDP vs TCP

### Why Exchanges Use UDP Multicast

```
Exchange market data (e.g., CME Globex, NASDAQ ITCH):
  Sends to 100s of subscribers simultaneously using UDP multicast.
  One packet → hardware replicates to all NIC members of multicast group.
  No ACK, no retransmit from exchange side → minimal latency for all receivers.

  TCP alternative: exchange would need 100 TCP connections, ACK each subscriber.
  At 100K messages/sec × 100 subscribers → 10M ACKs/sec → exchange bottleneck.

  UDP multicast: 100K messages/sec × 1 UDP packet → all subscribers simultaneously.

Reliability layer (application-level):
  Each message has a sequence number.
  Receiver detects gap (seq 500 then seq 502) → requests seq 501 via separate TCP
  retransmit channel (e.g., CME "recovery feed").
  This is the same pattern as Aeron's selective NAK (see aeron.md).
```

### Market Data Protocol Formats

| Format | Exchange/Vendor | Encoding | Latency |
|---|---|---|---|
| **ITCH 5.0** | NASDAQ | Binary, fixed-length | < 1µs parse |
| **OPRA** | Options exchanges (US) | Binary, UDP multicast | < 1µs parse |
| **CME MDP 3.0** | CME Group (futures) | SBE binary | < 1µs parse |
| **FIX/FAST** | Many exchanges | Binary compressed FIX | 1–5µs parse |
| **Level 2 FIX** | Traditional OMS | ASCII text FIX | 50–500µs parse |
| **ITCH Nordic** | Nasdaq Nordic | Binary | < 1µs parse |

**Why ITCH is preferred over FIX for market data:**
- ITCH: fixed-length binary fields, no tag parsing, no delimiters — field at known byte offset
- FIX: tag=value ASCII pairs, variable length — requires scanning for tag numbers

```
ITCH message parse (binary):
  byte[0]       = message type (1 byte lookup)
  bytes[1..8]   = timestamp  (fixed offset, long read)
  bytes[9..12]  = stock locate (fixed offset, int read)
  bytes[13..20] = order reference (fixed offset, long read)
  Parse time: ~50ns (just memory reads at known offsets)

FIX ASCII parse:
  "8=FIX.4.2|9=178|35=D|49=CLIENT|56=BROKER|..."
  Scan each tag= prefix, find = delimiter, read value until |
  Parse time: ~5–20µs (string scanning, multiple strlen calls)
```

---

## Summary: What the Book Adds vs What Was Already Here

| Topic | Source |
|---|---|
| Primitive vs wrapper memory layout | Book: Java Types chapter |
| Autoboxing trap + Integer cache (-128 to 127) | Book: Java Types chapter |
| String dedup flags (`-XX:+UseStringDeduplication`) | Book: Java Types chapter |
| Records for low-latency DTOs | Book: Java Types chapter |
| BitSet for boolean flag arrays | Book: Java Types chapter |
| HashMap resizing trap + pre-sizing formula | Book: Collections chapter |
| ConcurrentHashMap Java 8 node-locking detail | Book: Collections chapter |
| Agrona (primitive collections, no boxing) | Book: Collections chapter |
| Fail-fast vs fail-safe iteration | Book: Collections chapter |
| Lock states: biased→thin→fat (mark word) | Book: Synchronization chapter |
| StampedLock optimistic reads | Book: Synchronization chapter |
| Epsilon GC (no-op collector) | Book: GC Evolution chapter |
| Zero-GC checklist (lambdas, varargs, Charset, StringBuilder) | Book: No-GC chapter |
| OS Linux tuning checklist (BIOS, isolcpus, IRQ, hugepages) | Book: OS Tuning chapter |
| Jitter concept + cyclictest measurement | Book: OS Tuning chapter |
| Project Valhalla (value classes, inline types) | Book: Java Recent Projects |
| Project Leydon (AOT, static image) | Book: Java Recent Projects |
| Project Panama (Foreign Function API) | Book: Java Recent Projects |
| Project Loom (virtual threads — HFT cautions) | Book: Java Recent Projects |
| Project Babylon (GPU compute via Java) | Book: Java Recent Projects |
| RFQ system architecture | Book: Trading Systems chapter |
| Market making architecture (fair value, skew, spread) | Book: Trading Systems chapter |
| Exchange matching: price-time vs pro-rata | Book: Trading Systems chapter |
| Dark pools (Crossfinder, Sigma X, IEX) | Book: Trading Systems chapter |
| Smart Order Router + Drop Copy | Book: Trading Systems chapter |
| POV / TWAP / IS / Arrival Price strategies | Book: Trading Systems chapter |
| KDB+ columnar time-series DB | Book: KDB+ chapter |
| Q language (VWAP, moving avg, time buckets) | Book: KDB+ chapter |
| UDP multicast for market data feeds | Book: Market Data chapter |
| ITCH / OPRA / CME MDP 3.0 binary protocols | Book: Market Data chapter |

---

---

# Zero-Copy in Java for HFT — Six Techniques

**Source file:** `hft/zerocopy/ZeroCopyDemo.java`

"Zero-copy" means moving data between buffers **without the CPU copying bytes**.
Every copy is latency (10–20ns per 64 bytes) + allocation (GC pressure).
At 500,000 messages/sec, eliminating copies saves 5–10ms/sec of wasted CPU.

---

## Why Copying is the Enemy

```
Standard Java message passing (THREE copies):

  NIC DMA → kernel socket buffer          (copy 1 — DMA, not CPU)
  kernel socket buffer → JVM byte[]       (copy 2 — CPU, syscall)
  JVM byte[] → application POJO fields    (copy 3 — CPU, parse/deserialise)

  Each CPU copy: ~10–20ns for 64 bytes
  At 500K msg/sec: ~5–10ms/sec wasted
  Plus: every byte[] allocation → GC pressure → p99 latency spikes

Zero-copy goal:
  NIC DMA → pre-allocated DirectByteBuffer (off-heap, mapped to NIC)
  Application reads fields at fixed offsets from same buffer (no copy, no parse)
  Total: 0 CPU copies, 0 allocations, ~10–30ns decode
```

---

## Technique 1 — DirectByteBuffer (Off-Heap Storage)

`ByteBuffer.allocateDirect(n)` allocates `n` bytes in **native (C) heap**.
The JVM GC never scans, never moves, never pauses for it.
A NIC can DMA directly into this memory — no copy into kernel buffer first.

```java
// One allocation for entire order book — pre-allocated at startup (cold path)
ByteBuffer buffer = ByteBuffer.allocateDirect(capacity * ORDER_SIZE);

// HOT PATH: write order at fixed offset — ~4–5ns per putLong
int base = slot * ORDER_SIZE;
buffer.putLong(base,      orderId);    // 8 bytes at offset 0
buffer.putLong(base + 8,  symbol);    // 8 bytes at offset 8
buffer.putLong(base + 16, price);     // 8 bytes at offset 16
buffer.putLong(base + 24, qty);       // 8 bytes at offset 24
buffer.put    (base + 32, side);      // 1 byte  at offset 32
// Zero allocation. Zero copy. No GC root added.

// HOT PATH: read order — direct getLong at known offset
long orderId = buffer.getLong(slot * ORDER_SIZE);   // ~3–4ns
```

**Memory layout (64 bytes = 1 cache line):**
```
offset  0:  orderId    (8B)   ← getLong(base + 0)
offset  8:  symbolCode (8B)   ← getLong(base + 8)   symbol encoded as long
offset 16:  price      (8B)   ← getLong(base + 16)  fixed-point: 1.08345 → 108345L
offset 24:  quantity   (8B)   ← getLong(base + 24)
offset 32:  side       (1B)   ← get(base + 32)       0=BUY 1=SELL
offset 33:  orderType  (1B)
offset 34:  status     (1B)
offset 35:  padding    (29B)  ← fills to 64-byte cache line boundary
```

---

## Technique 2 — MappedByteBuffer (mmap — File as Virtual Memory)

`FileChannel.map()` maps a file region into the process's virtual address space.
No `read()`/`write()` syscalls — the OS page cache is the buffer.

```java
FileChannel   channel = new RandomAccessFile(path, "rw").getChannel();
MappedByteBuffer buf  = channel.map(FileChannel.MapMode.READ_WRITE, 0, totalSize);

// WRITE (append to journal):
buf.putLong(base, orderId);    // dirtied page cache — no syscall
buf.putLong(base + 8, price);

// DURABLE FLUSH (call once per batch, not per message):
buf.force();   // msync(MS_SYNC) — blocks until dirty pages written to disk
```

**What happens in the kernel:**
```
First access to a page → page fault → kernel loads page from disk into page cache
All subsequent accesses → page cache hit → ~3–5ns, no syscall
Write  → dirties page cache → kernel flushes asynchronously (or on force())
mmap for IPC → two processes map the SAME FILE → share the same physical page
   Process A writes → Process B reads the same physical RAM → zero copy, zero syscall
```

**Production uses:**
- Chronicle Queue (persistent ring buffer — same mechanism)
- Event sourcing journal in `DeterministicExchangeSM.java`
- Aeron IPC backing store (`/dev/shm/` = tmpfs = RAM-backed, never hits disk)

---

## Technique 3 — FileChannel.transferTo() (sendfile syscall)

Standard file-to-socket: **4 copies** (2 CPU copies).
`transferTo()`: **2 copies**, **0 CPU copies** — pure DMA.

```java
try (FileChannel src = FileChannel.open(dataFile, StandardOpenOption.READ)) {
    long transferred = src.transferTo(0, src.size(), socketChannel);
    // Linux sendfile(): page cache → NIC directly via DMA
    // CPU never touches the bytes
}
```

```
Standard InputStream → OutputStream:
  disk → page cache (DMA copy 1)
  page cache → byte[] in JVM heap (CPU copy 2)  ← eliminated by transferTo
  byte[] → kernel socket buffer (CPU copy 3)     ← eliminated by transferTo
  socket buffer → NIC (DMA copy 4)

transferTo() (sendfile):
  disk → page cache (DMA copy 1)
  page cache → NIC  (DMA copy 2)
  CPU involvement: zero
```

**Benchmark:** Sending 10MB trade report file:
- `InputStream.read()` loop: ~5ms
- `transferTo()`: ~500µs (10× faster, no CPU copies)

---

## Technique 4 — ByteBuffer Slice and Duplicate (Views Without Copying)

`slice()` and `duplicate()` create logical **windows** into an existing buffer.
No bytes are copied. Both share the same backing memory.

```java
ByteBuffer nicDmaBuffer = ByteBuffer.allocateDirect(65536); // pre-allocated receive buffer

// RECEIVE: NIC DMA writes ITCH message at offset 1024 in the buffer
// DECODE:  create a view — zero copy, zero allocation
ByteBuffer msg = nicDmaBuffer.duplicate();           // new position/limit, same memory
msg.position(1024).limit(1060);                      // ITCH AddOrder = 36 bytes
ByteBuffer slice = msg.slice();                      // window into those 36 bytes

// READ fields at fixed offsets — direct reads, no parse loop
long timestampNanos = slice.getLong(5);              // bytes 5–12
long orderRef       = slice.getLong(13);             // bytes 13–20
byte buySell        = slice.get(21);                 // byte 21
int  shares         = slice.getInt(22);              // bytes 22–25
long priceRaw       = Integer.toUnsignedLong(slice.getInt(32)); // bytes 32–35

// vs FIX ASCII parse:
//   "35=D|49=CLIENT|55=EURUSD|54=1|38=1000000|44=1.08345|"
//   → scan tags, find delimiters, parse doubles from strings → ~5–20µs + allocations
```

---

## Technique 5 — SBE-Style Fixed-Offset Encoding

SBE (Simple Binary Encoding) is the serialisation format used by Aeron, CME, and
most HFT firms. Every field at a **known byte offset** — no length prefixes, no tags,
no variable-length fields on the hot path.

```java
// ENCODE — write directly into pre-allocated DirectByteBuffer
ByteBuffer wire = ByteBuffer.allocateDirect(256);   // reused across all orders
wire.putShort(0, (short) 56);        // block length header
wire.putShort(2, (short) 1);         // template ID
wire.putLong (8, clOrdId);           // field at fixed offset 8
wire.putLong (16, symbolCode);       // field at fixed offset 16
wire.putLong (24, priceFixed);       // field at fixed offset 24
wire.putLong (32, orderQty);         // field at fixed offset 32
wire.put     (40, side);             // field at fixed offset 40
// Total encode time: ~20–30ns (8 putLong calls + 1 put)

// DECODE — read fields at fixed offsets from the same buffer
long clOrdId    = wire.getLong(8);   // ~3ns
long price      = wire.getLong(24);  // ~3ns
long qty        = wire.getLong(32);  // ~3ns
// Total decode time: ~10–15ns (no scanning, no parsing)
```

**Comparison:**
```
Format          Encode      Decode      Allocations   Message size
SBE             ~20–30ns    ~10–15ns    0             64B (fixed)
Protobuf        ~200–500ns  ~200–500ns  3–10 objects  variable
FlatBuffers     ~100–200ns  ~5–10ns     0             variable (larger)
JSON (Jackson)  ~5,000ns    ~20,000ns   20–50 objects variable (large)
FIX ASCII       N/A         ~5,000ns    10–30 objects ~200B+
```

---

## Technique 6 — `sun.misc.Unsafe` (Raw Native Memory)

The fastest Java path: raw `putLong`/`getLong` at a native memory address.
No bounds check. No null check. No JVM overhead.
Used internally by: LMAX Disruptor (sequence), Aeron (ring buffer), Chronicle Map.

```java
Unsafe UNSAFE = ...;  // obtained via reflection

// Allocate native memory (like C malloc)
long baseAddress = UNSAFE.allocateMemory(slots * 64L);
UNSAFE.setMemory(baseAddress, slots * 64L, (byte) 0);

// WRITE (~2–3ns per field — no bounds check branch vs ~4–5ns for ByteBuffer)
UNSAFE.putLong(baseAddress + slot * 64 + 0,  orderId);
UNSAFE.putLong(baseAddress + slot * 64 + 16, price);
UNSAFE.putLong(baseAddress + slot * 64 + 24, qty);

// READ (~2–3ns per field)
long orderId = UNSAFE.getLong(baseAddress + slot * 64);

// ORDERED STORE — store-release without full StoreLoad fence (~1ns vs volatile ~5ns)
// Used in LMAX Disruptor to publish sequence (SPSC producer)
UNSAFE.putOrderedLong(null, sequenceAddress, newSequence);

// Always free when done
UNSAFE.freeMemory(baseAddress);
```

---

## Technique 7 — Shared Memory IPC via mmap (Aeron IPC Pattern)

Two processes map the **same file** (on `/dev/shm` = RAM-backed tmpfs).
A write in Process A is instantly visible to Process B — no network, no pipe, no socket.
Latency: **200–500ns** (same machine, no syscall after setup).

```
/dev/shm/hft-ring  (RAM-backed file, never hits disk)
         │
         ├── mapped by Process A (matching engine)
         │    buffer.putLong(base, orderId)  // writes to page cache
         │
         └── mapped by Process B (risk engine)
              long id = buffer.getLong(base)  // reads same physical RAM page
              // No network. No pipe. No copy. Just a memory read.

Aeron IPC (aeron:ipc) uses exactly this mechanism.
Chronicle Queue uses it with a ring buffer wrapper for persistence.
```

---

## Zero-Copy Decision Guide

```
What are you doing?                           Use
───────────────────────────────────────────────────────────────────────
Store order book / positions in-process    → DirectByteBuffer (Technique 1)
Persist events to disk (journal)           → MappedByteBuffer / Chronicle Queue (T2)
Send large file over network (TCP)         → FileChannel.transferTo() (T3)
Parse incoming binary market data message  → ByteBuffer.slice() + fixed offsets (T4)
Serialise order for network wire           → SBE-style fixed-offset encoding (T5)
Maximum speed ring buffer / Disruptor      → Unsafe putOrderedLong (T6)
IPC between two processes on same machine  → Shared mmap on /dev/shm (T7)
IPC with reliability + flow control        → Aeron IPC (wraps T7)
```

**File:** `hft/zerocopy/ZeroCopyDemo.java` — all seven techniques with runnable `main()`.