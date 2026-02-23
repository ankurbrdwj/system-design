Summary: Low Latency Trading System Engineering in C++
David, an experienced market maker and software engineer, presents a detailed talk on engineering low latency trading systems in C++. The focus is on practical engineering challenges and optimizations, rather than theory, emphasizing the importance of simplicity, measurement, and hardware-aware programming for market making.

Historical Context and Market Making Overview
The talk begins with an analogy to the Roman Empire, highlighting its success due to strong planning, infrastructure, and discipline.
Romans pioneered early forms of derivative trading through future contracts to reduce economic uncertainty, a concept still relevant in modern financial markets.
Market making is described as a losers game: consistent excellence across many factors is required since profits per trade are small and risks from stale prices or big market news can cause losses.
Two key reasons for low latency systems:
Fast reaction to market events (e.g., news).
Being smart and accurate in pricing, requiring rapid ingestion and processing of information.
Modern Trading System Architecture and Low Latency Software
Despite the use of fast hardware like FPGAs, software remains critical due to cost, flexibility, and complexity.
Strategies send simple rules to FPGAs (e.g., if price > X, update order), requiring strategies themselves to operate under low latency constraints.
A typical trading system involves:
Exchange feeds delivering prices.
A fast order book maintained in software.
Shared memory and queues to disseminate information efficiently to multiple strategies.
Bypassing kernel networking stacks to reduce latency.
Core Data Structures: The Order Book
The order book is central: it tracks the best bid and ask prices, volumes, and order IDs.
Key properties:
Two ordered sequences: bids (buyers) and asks (sellers).
Roughly 1,000 price levels per side in stock order books.
Operations: add, modify, delete orders by ID.
A hash map is essential to retrieve order details from order IDs.
Common initial implementation uses std::map (balanced binary trees) for ordered price levels with iterators remaining valid after updates.
Latency profiling reveals:
Two peaks in latency due to hashmap lookups and binary tree operations.
Poor cache locality in node-based containers like std::map harms performance.
Performance Principles and Optimizations
Avoid node-based containers (e.g., std::map, std::set) due to poor cache locality.
Understand the problem domain and data distribution deeply before optimization.
Leverage specific problem properties for performance gains.
Favor simplicity combined with speed: simple solutions like linear search often outperform complicated ones.
Practice mechanical sympathy: write algorithms harmonious with hardware (cache-friendly, branch predictor friendly).
Vector-based Order Book Implementation
Using std::vector and std::lower_bound for order levels improves cache locality.
Best price updates concentrate near the top of the book; reversing the vector order significantly reduces data movement and latency tails.
Linear search, despite its theoretical O(n) complexity, outperforms binary search in this context due to small collection sizes and hardware effects.
Branchless binary search reduces branch mispredictions but increases memory touched, causing mixed latency results.
Profiling and Measurement Techniques
Profiling tools like perf and Intel Top-Down Microarchitecture Analysis help identify bottlenecks:
Categories: retiring instructions, bad speculation, front-end bound, back-end bound.
High branch misprediction (~25%) found in binary search motivated branchless alternatives.
Clang’s X-Ray instrumentation allows low-overhead, accurate function-level profiling without recompilation, improving developer workflow.
Intrusive profiling (manual timing with TSC counters) is precise but expensive and hard to maintain.
Networking and Concurrency
For low latency:
Bypass OS kernel networking stacks using technologies like Solarflare Onload, EFVI, or DPDK.
Achieve sub-microsecond latencies on UDP packets (~700 ns).
Use shared memory for inter-process communication on the same server to minimize overhead.
Shared memory queues are bounded, lock-free, and support multiple consumers.
Design considerations include message variable length, avoiding blocking, and preventing slow consumers from affecting writers.
Simple, minimalistic queue implementations (around 150 lines of code) can outperform complex frameworks.
Optimizations:
Batch updates to atomic counters to reduce contention.
Avoid cache-line alignment on atomic counters to improve data locality.
Skip redundant reads of atomic counters when already known.
Additional Insights and Principles
Latency distribution matters: focusing on averages or medians hides tail latency problems critical in trading.
Multi-core performance depends on cache hierarchy; shared L3 cache can cause contention.
You’re not alone: system-wide performance depends on all processes on the server, requiring teamwork and holistic optimization.
Keep latency and simplicity balanced: simpler code is easier to maintain, debug, and extend, facilitating faster time-to-market.
No silver bullet exists; consistent, disciplined engineering is key.
Q&A Highlights
Hybrid search strategies: Combining linear search for top levels and binary search for deeper levels was tried but linear search suffices for typical order book sizes.
Struct of arrays: Splitting price and volume into separate arrays improves SIMD usage and cache locality, yielding slight performance gains.
Persistence of event states: Techniques like write-ahead logs (WAL) from databases can be adapted for durability; presented queues do not implement this.
Security concerns with shared memory: Not deeply covered; typical trading systems run on controlled servers reducing external attack risks.
Linux kernel message queues: Kernel message queues are generally fast but incur kernel transition overhead and jitter, which trading systems seek to avoid.
Concurrency frameworks: Preference for minimal dependencies and manual optimizations over complex frameworks for critical low latency paths.
Timeline Table (Selected Key Topics)
Time Range	Topic	Key Points
00:10–07:34	Introduction and Market Making Background	Roman Empire analogy; market making as consistent small profits game; need for low latency
07:34–13:34	Order Book Data Structure Definition and Basics	Order book concepts; use of hashmap + ordered container; std::map baseline implementation
13:34–26:35	Vector Implementation and Data Distribution Analysis	Importance of cache locality; reversing vector; linear vs binary search
26:35–40:20	Profiling Techniques and Branch Prediction Issues	Intel top-down analysis; perf and assembly inspection; branchless binary search optimization
40:20–59:02	Networking and Shared Memory Queues	Kernel bypassing; Solarflare, EFVI, DPDK; bounded shared memory queues; concurrency design
59:02–01:14:37	Latency Measurement, Multi-core Scaling, and Wrap-up	Intrusive profiling; clang X-Ray; multi-core cache effects; principles summary; Q&A
Key Insights and Conclusions
Low latency trading systems require a holistic engineering approach combining hardware-aware algorithms, profiling, and simplicity.
Node-based containers (like std::map) are generally unsuitable for performance-critical order books due to cache inefficiency.
Simple data structures (vectors, linear search) often outperform complex ones given the problem size and hardware characteristics.
Profiling and measurement are essential to identify bottlenecks and validate optimizations.
Bypassing kernel networking stacks and using shared memory queues are essential techniques for minimizing latency.
Trade-offs between accuracy, speed, and complexity define design decisions in market making systems.
Collaboration and system-wide thinking matter: performance depends on the entire server environment.
Time to market and maintainability are important latency considerations alongside execution speed.
Keywords
Low latency trading
Market making
Order book
C++
Cache locality
Vector vs map
Linear search
Branch prediction
Profiling (perf, Intel top-down, clang X-Ray)
Shared memory queues
Kernel bypass networking (Solarflare, EFVI, DPDK)
Concurrency
Mechanical sympathy
Data structures optimization
Intrusive profiling
Time to market
This summary reflects the video content faithfully, capturing key technical points, engineering principles, and practical insights from the talk.