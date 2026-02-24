Block 1 (Morning, ~2 hrs): Core Java & Concurrency
This is the heart of the role — "excellent knowledge of core Java, collections, multi-threading, networking, 5+ years." Expect at least 40% of the interview here.
Collections — know these cold:
HashMap internals: array of buckets, hash collision handling via chaining (linked list → treeified to red-black tree at 8 entries in Java 8+), load factor (default 0.75), resizing (doubles the array, rehashes everything — expensive, always pre-size if you know cardinality). ConcurrentHashMap vs Hashtable: Hashtable locks the entire map, ConcurrentHashMap uses segment/bucket-level locking (Java 8 uses CAS + synchronized on individual bins) — much better throughput under concurrent writes. LinkedHashMap for LRU cache (access-order mode + override removeEldestEntry). TreeMap for sorted keys (red-black tree, O(log n) — directly relevant for order book price levels).
Concurrency — the deepest area:
Java Memory Model: happens-before relationships, volatile guarantees visibility (not atomicity), synchronized guarantees both. volatile is correct for a single-writer/multiple-reader flag. Never use volatile for i++ (read-modify-write — not atomic).
ExecutorService vs raw threads: always use thread pools in production. Know FixedThreadPool, CachedThreadPool, ScheduledThreadPool, and ForkJoinPool. Know why Executors.newFixedThreadPool(n) uses an unbounded LinkedBlockingQueue internally — this can cause OOM under load (a classic interview trap). For trading, you'd use a bounded queue with a RejectedExecutionHandler.
CompletableFuture: chaining async operations with thenApply, thenCompose, thenCombine. Know the difference between thenApply (sync, same thread) and thenApplyAsync (new thread from pool). For the trading role, CompletableFuture.allOf() for fan-out requests to multiple market venues is a great talking point.
ReentrantLock vs synchronized: ReentrantLock gives you tryLock (avoid deadlock), timed lock, and interruptible lock — use it when you need fine-grained control. ReadWriteLock for read-heavy structures (market data cache: many readers, infrequent writes).
CountDownLatch (one-time barrier, can't be reset) vs CyclicBarrier (reusable, all threads wait for each other). Semaphore for rate limiting or connection pool size control.
LMAX Disruptor: the ring buffer pattern for ultra-low latency inter-thread messaging. Know it conceptually — it avoids locks entirely using memory barriers and sequence numbers, and is used in production HFT Java systems.
Likely interview questions in this block:

"How does ConcurrentHashMap achieve thread safety without a global lock?"
"What is the difference between wait()/notify() and Condition?"
"Explain happens-before in the Java Memory Model with an example."
"How would you implement a thread-safe LRU cache in Java?"
"What are the risks of Executors.newFixedThreadPool()?"


Block 2 (Late Morning, ~1.5 hrs): Performance Optimisation
The JD says "experience in performance optimisation of multi-threaded Java applications." This is very likely to be a discussion rather than a coding question — they want to know how you think about performance.
GC tuning: Know the GC options: G1GC (default Java 9+, good balance, predictable pause targets with -XX:MaxGCPauseMillis), ZGC (sub-millisecond pauses, Java 15+ production-ready, ideal for trading), Shenandoah (similar to ZGC, RedHat-backed). For low-latency trading: pre-allocate objects during warmup, use object pools (commons-pool2), avoid allocations in the hot path (no new in the order processing loop), and use primitive arrays over boxed collections (int[] not List<Integer>).
Key JVM flags to know: -Xms and -Xmx set equal (avoid heap resize pauses), -XX:+UseZGC for latency, -XX:+AlwaysPreTouch (pre-fault all heap memory at startup — no page faults during trading), -XX:+DisableExplicitGC (prevent System.gc() calls), -verbose:gc or -Xlog:gc for GC logging.
False sharing: Two threads writing to fields on the same 64-byte cache line cause constant cache invalidation. Fix with @Contended annotation (JDK internal) or manual 128-byte padding. This is a real pattern in trading order processors.
JMH: Java Microbenchmark Harness is the correct way to benchmark Java code. Mention it — it shows seniority. Know why naive benchmarks are wrong (JIT warmup, dead code elimination).
Off-heap memory: For zero-GC data structures — ByteBuffer.allocateDirect(), Chronicle Map/Queue. The trading system can store the order book off-heap so GC never touches it.
Likely interview questions:

"How would you diagnose a latency spike that happens randomly in a trading application?"
"What GC would you use for a low-latency trading system and why?"
"Explain false sharing and how you would detect and fix it."
"How do you avoid garbage generation in a hot trading path?"


Block 3 (Afternoon, ~1.5 hrs): System Design for Trading
This is where your Market Risk background at DB becomes a huge advantage — use it.
Design question most likely to be asked: "Design a high-performance order management system (OMS) or a market data feed handler in Java."
For the OMS, walk through: incoming order validation (pre-trade risk checks — max position, max notional — these are exactly what you work with in Market Risk!), order routing to exchange connectors via a non-blocking queue (LMAX Disruptor or ArrayBlockingQueue), state machine for order lifecycle (New → PendingAck → Acknowledged → PartialFill → Filled/Cancelled), and execution report processing back from the exchange via FIX protocol.
Messaging solutions (JD requirement): Know Kafka well — producers, consumers, consumer groups, partitions (partitioning by instrument for ordered processing), offsets, at-least-once vs exactly-once delivery. For internal in-process messaging: LMAX Disruptor. For low-latency inter-service: Aeron. Know when to use each. Kafka is for durable, high-throughput event streaming. Disruptor is for sub-microsecond in-process handoff.
Fault tolerance and load balancing (JD requirement): Circuit breaker pattern (Resilience4j — open after N failures, half-open to test recovery). Retry with exponential backoff + jitter. Bulkhead pattern (separate thread pools per downstream service so one slow exchange doesn't block all others). For load balancing: round-robin, least-connections, and consistent hashing (for session affinity — route orders from the same client to the same OMS instance). Health checks and automatic failover.
Your Market Risk angle: When they ask about system design, mention that you understand the downstream consumers of trading data — the risk systems. This gives you insight into what the trading system must guarantee: correct sequence of fills, idempotent execution reports, accurate timestamps for P&L attribution. Most tech candidates don't know this — it's your differentiator.
Likely questions:

"How would you design a fault-tolerant order routing system?"
"How do you ensure exactly-once order submission to an exchange?"
"How would you handle 100,000 market data updates per second in Java?"
"Walk me through the trade lifecycle from order submission to settlement."


Block 4 (Evening, ~45 min): Algorithms Quick Drill
The JD says "good math and strong knowledge of algorithms and complexity." They won't ask you to implement Dijkstra from scratch. They want to see you think clearly about trade-offs.
Must know: Big-O for the collections you'll use (HashMap O(1) avg, TreeMap O(log n), LinkedList O(n) — always prefer ArrayDeque). Binary search O(log n) — know when a sorted array beats a hash map (cache locality). Sliding window for time-series problems (e.g., VWAP calculation over a rolling window). Priority queue for best bid/ask tracking.
One problem to practice: Implement a rate limiter in Java using a sliding window algorithm — this is a real system design + algorithms combo question that appears frequently in trading interviews. Use a ConcurrentLinkedDeque to store timestamps of recent requests, evict entries older than the window, check if count < limit.

Behavioral Prep (20 min tonight)
Three questions to prepare answers for:
"Why are you moving from Market Risk to tech?" Best answer: you want to build the systems that generate the data you've been consuming. You understand the risk impact of latency, incorrect fills, and data quality issues — you want to fix those problems at the source. This is a compelling story that almost no external candidate can tell.
"Tell me about a complex technical problem you solved." If you have any scripting, automation, or data pipeline work from Market Risk, use it. Frame it technically — what was the input, what was the constraint, what was your solution, what was the measured impact.
"How do you handle a production incident?" Show calm + systematic process: isolate the blast radius first (is it one client or all?), check logs and metrics, roll back if possible, fix forward if not, post-mortem with root cause and prevention.

Tonight: The 3 Things to Review Before Sleeping

ConcurrentHashMap internals and the difference between volatile, synchronized, and AtomicInteger — this is the single most likely topic.
GC options and why ZGC is right for trading — one clean answer here signals strong seniority.
Your personal "why internal transfer" story — make it crisp and confident.