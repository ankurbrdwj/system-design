                                                # CLAUDE.md — System Design Practice Project

## Project Purpose
30-minute interview practice POC covering system design, HFT, concurrency, and Spring Boot REST APIs.

---

## Build & Run Commands

```bash
# Compile only
./gradlew compileJava

# Run all tests
./gradlew test

# Full build
./gradlew build

# Run a specific test class
./gradlew test --tests "com.ankur.design.disruptor.RingBufferTest"
```

---

## Known Issues

### Spring Boot tests (pre-existing failures — do not fix unless asked)
9 tests always fail with `APPLICATION FAILED TO START`:
- `SystemDesignApplicationTests`
- `HotelControllerTest` (4 tests)
- `RatingControllerTest`, `SearchControllerTest`, `DefaultRatingServiceTest`
- `TestTaskController`

These are pre-existing Spring context failures unrelated to HFT/concurrency code.

### WebCurve excluded from Gradle build
`build.gradle` excludes `**/webcurve/**` because the webcurve source files under
`src/main/java/com/ankur/design/hft/webcurve/src/` depend on QuickFIX/J and Apache MINA JARs
that are not in the Gradle classpath. Those JARs live at `hft/webcurve/*.jar`.
Do not remove this exclusion.

---

## Package Map

### HFT & Low Latency
| Package | Contents |
|---|---|
| `hft/orderbook` | Order book data structures, cache locality, lock-free queues, profiling (7 files) |
| `hft/profiling` | async-profiler demos: sampling, safe-point bias, flame graphs, allocation, cache miss, wall-clock, lock contention, native memory (9 files) |
| `hft/trading` | HFT trading concept demo (1 file) |
| `hft/webcurve` | WebCurveSim exchange simulator — full FIX 4.2 order matching engine (excluded from build; has own README.md) |
| `lowlatency` | Low-latency trading system demos: order book, IMC, order router, message processing, GC mitigation, NUMA, messaging fabric, atomic transactions, failover (14 files) |
| `disruptor` | LMAX Disruptor: RingBuffer (entry-based), SpscRingBuffer (publish/consume API) (2 files) |

### Core Trading & Multithreading
| Package | Contents |
|---|---|
| `core/trading` | C++→Java HFT translations: STL collections, POSIX threading, lock-free Disruptor, off-heap memory, NIO sockets, Aeron shared memory, profiling tools (7 files) |
| `multithreaded/concurrentmap` | ConcurrentHashMap demos, lock-per-key, computeIfAbsent vs compute (3 files) |
| `multithreaded/coordination` | Thread coordination patterns (2 files) |
| `multithreaded/executor` | ExecutorService patterns (5 files) |
| `multithreaded/correctness` | Thread safety correctness demos (1 file) |
| `multithreaded/scarcity` | Resource scarcity / starvation (1 file) |
| `multithreaded/square` | Parallel square computation (3 files) |
| `rate` | Currency rate service with lock-per-key, ConcurrentHashMap (5 files) |

### System Design APIs
| Package | Contents |
|---|---|
| `booking/recruitment/hotel` | Hotel booking REST API — Spring Boot, JPA, H2 (28 files) |
| `bloomberg` | Bloomberg service v1 and v2 (18 files) |
| `rest` | Generic REST controller/service/repository example (8 files) |
| `ppro` | PPRO service POC (2 files) |
| `paxos/simple` | Paxos consensus algorithm (2 files) |
| `hashing` | Consistent hashing (1 file) |
| `collections` | Custom collection implementations (5 files) |
| `solid/payroll` | SOLID principles payroll example (1 file) |

### Training
| Package | Contents |
|---|---|
| `training/java8` | Java 8: lambdas, streams, method references, functional interfaces (49 files) |
| `training/java8/concurrency` | Java 8 concurrency: multithreading, reentrant locks, semaphores, wait/notify, cyclic barriers, thread pools |

---

## Test Structure

```
src/test/java/com/ankur/design/
├── disruptor/RingBufferTest.java        ← 18 tests, ALL PASS (SpscRingBuffer)
├── multithreading/correctness/          ← 2 test files
├── multithreading/parallelism/          ← 1 test file
├── multithreading/square/               ← 1 test file
├── rate/                                ← 2 test files
├── booking/recruitment/hotel/           ← 6 test files (FAIL — Spring context)
├── rest/controller/                     ← 1 test file (FAIL — Spring context)
└── ppro/                                ← 1 test file
```

---

## Key Design Patterns in This Codebase

- **SPSC Ring Buffer** (`SpscRingBuffer`): `AtomicLong.lazySet()` as store-release, `& MASK` bitwise wrap, `Thread.onSpinWait()` busy-spin
- **Lock-per-key**: `ConcurrentHashMap.computeIfAbsent()` for per-symbol/per-currency locks
- **Object pooling**: `ArrayBlockingQueue` pool to avoid GC on hot path
- **Wall-clock vs CPU profiling**: sample all thread states vs RUNNABLE only
- **False sharing**: 56-byte padding between hot `volatile long` fields
- **Cache locality**: `long[]` contiguous arrays vs node-based `TreeMap`

---

## Tech Stack

- **Java** (targets Java 21+ — uses virtual threads, records, `Thread.ofPlatform()`)
- **Spring Boot 3.5.6** — web, data-jpa, data-rest
- **Hibernate 6** + **H2** (in-memory, for REST API demos)
- **Lombok 1.18.42**
- **JUnit 5** (Jupiter)
- **Gradle 9**

---

## HFT Sub-project ReadMe Files

Each HFT sub-folder has a markdown file explaining the concepts:

| File | Topic |
|---|---|
| `hft/orderbook/ReadMe.md` | Low Latency Trading in C++ talk summary — 9 principles |
| `hft/profiling/` | async-profiler: sampling, safe-point bias, wall-clock, flame graphs |
| `hft/webcurve/README.md` | WebCurveSim architecture, package map, FIX 4.2 config |
| `hft/architecture/architecture.md` | HFT system architecture |
| `hft/latency/lowLatency.md` | Low latency patterns |
| `hft/microservices/microservices.md` | HFT microservices |
| `core/trading/ReadMe.md` | C++ STL → Java collections, POSIX → JUC, Disruptor, off-heap, NIO, Aeron |
| `disruptor/Readme.md` | LMAX Disruptor write mechanics, two-phase commit, ring buffer |
| `rate/Readme.md` | Currency rate service with concurrent map patterns |