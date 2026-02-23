Summary of Talk: Low Latency Trading Systems in C++
David, an industry professional with 10 years of trading and market-making experience, presents a detailed exploration of engineering low latency trading systems using C++. The talk combines historical context, engineering principles, data structure choices, concurrency, and performance profiling to provide a comprehensive overview of designing efficient trading infrastructure.

Historical Context & Market Making
The concept of derivative trading dates back to the Roman Empire (~2000 years ago), where Romans used early futures contracts to stabilize prices for large festivals.
Roman success was attributed to planning, infrastructure, and discipline, which resonates with modern trading system requirements.
Market making is described as a “losers game” requiring consistent excellence across all operational aspects; profits are small, and risks from stale prices or news can cause losses.
Low latency is crucial to:
React quickly to market events (e.g., news).
Maintain accurate, up-to-date prices and avoid stale information.
Modern market-making systems involve multiple actors, with FPGA hardware offering very low latency but with high cost and complexity.
Software remains essential due to flexibility, cost-effectiveness, and complexity trade-offs.
Core Data Structure: Order Book
The order book is fundamental, representing:
Bids: Prices and volumes buyers are willing to pay (best bid is the highest).
Asks: Prices and volumes sellers are willing to accept (best ask is the lowest).
Typical stock order books have ~1000 price levels per side.
Key requirements:
Fast ingestion and updates of prices and volumes.
Handle network buffer constraints to avoid data loss.
Operations on order book messages: Add, Modify, and Delete orders.
Implementation considerations:
A hash map is essential to map order IDs to price levels and volumes.
Natural initial implementation: std::map for ordered storage of bids and asks.
Profiling reveals poor cache locality and latency spikes due to node-based containers (std::map).
Principle 1: Avoid node-based containers (std::map, std::set, unordered_map, list) in latency-critical code due to poor cache behavior.
Improved Data Structures and Techniques
Replacement with std::vector and binary search (lower_bound) offers better cache locality but has drawbacks:
Insertion is linear time due to element shifts.
Iterator invalidation requires redesign of lookup strategies.
Key insight: Reversing the vector order (best price at the end) reduces memory shifts and latency tail significantly.
Principle 2: Well-stated problems and domain understanding are critical to optimization.
Principle 3: Leverage specific properties of your domain/problem for performance gains.
Surprisingly, linear search outperforms binary search for typical order book sizes due to excellent cache locality and predictable update patterns.
Principle 4: Simple and fast solutions often outperform complex ones.
Principle 5: Mechanical sympathy—design algorithms harmonious with hardware characteristics (cache, branch prediction).
Profiling and Performance Analysis
Introduces Intel’s Top-Down Microarchitecture Analysis categories for CPU bottlenecks:
Retiring instructions, bad speculation, front-end bound, back-end bound.
Branch mispredictions cause significant CPU time waste in binary search.
Switch to branchless binary search improves IPC but increases memory touches.
Profiling tools used: perf, Intel VTune, perf record, and clang x-ray for low overhead and accurate profiling, including in production.
Principle 6: Be mindful of the tools and libraries you use—choose those that fit your needs and avoid unnecessary overhead.
Networking and Concurrency
Low latency networking requires bypassing the kernel (e.g., Solarflare network cards with Onload, EFVI, DPDK).
Kernel bypass reduces latency from microseconds to sub-microsecond levels.
Within a server, shared memory queues are used for inter-process communication, avoiding kernel overhead.
Shared memory queues are typically:
Bounded, non-blocking, and support multiple readers and one writer.
Use atomic counters to manage write/read positions and avoid contention.
Principle 7: Use the right concurrency primitives and data structures for your specific task.
Queue optimizations:
Batch updating atomic counters to reduce contention.
Avoid unnecessary cache line alignment to improve locality.
Use local counters to limit frequent atomic accesses.
Compared to other libraries (Disruptor, Iron), a simple custom queue performs competitively.
Practical Engineering Advice & Final Principles
Profiling with intrusive timers and clang x-ray improves performance insights without recompile cycles.
Continuous measurement and alerting on latency metrics are essential to maintain performance over time.
Principle 8: Being fast is good; staying fast is the real challenge.
“You’re not alone” principle:
Performance depends on overall system and server state, including other processes.
Cache hierarchy, CPU sharing, and memory bandwidth must be considered holistically.
Simplicity in design aids maintainability and shipping speed (time to market is also a form of latency).
No silver bullet exists; consistent discipline and simplicity yield better long-term results.
Summary Table: Key Principles for Low Latency Trading System Engineering
Principle No.	Description
1	Avoid node-based containers (std::map, std::set) for latency-critical data structures.
2	Clearly understand and define the problem domain before optimizing data structures.
3	Leverage domain-specific properties to gain performance advantages.
4	Prefer simple, fast, and mechanical-sympathetic solutions over complex ones.
5	Design algorithms harmonious with hardware (cache, branch prediction, memory access).
6	Use efficient, appropriate tools and libraries; avoid unnecessary kernel overhead.
7	Choose concurrency primitives suited to the task; keep IPC mechanisms simple and scalable.
8	Measure continuously; focus on staying fast, not just initial speed.
9 (Bonus)	Consider overall system environment and co-running processes for real-world performance.
Highlights and Key Insights
The Roman Empire’s futures contracts illustrate early risk management through derivative trading.
Market making requires consistent excellence; no silver bullet for beating the market.
FPGA hardware provides low latency but at a cost; software flexibility remains essential.
Order book updates are heavily skewed toward top levels, favoring linear search over binary search.
Cache locality is paramount; data structures must minimize pointer indirections.
Profiling tools like perf and clang x-ray enable low-overhead, production-ready performance analysis.
Efficient kernel bypass networking and shared memory IPC are crucial for sub-microsecond latency.
Simple, bounded queues with atomic counters provide robust concurrency with minimal overhead.
Maintaining low latency is a continuous engineering challenge requiring discipline, measurement, and collaboration.
Simplicity accelerates both performance and development speed, impacting time to market.
FAQ Summary
Q: Why avoid std::map for order books?
A: Poor cache locality causes latency spikes and unpredictable performance.

Q: Why linear search beats binary search?
A: Small data size and skewed updates make linear search more cache-friendly and predictable.

Q: How to profile low latency code effectively?
A: Use sampling profilers (perf, VTune) and clang x-ray for low overhead and production-friendly instrumentation.

Q: What about security in shared memory?
A: Not deeply covered; typically these systems run in controlled environments minimizing external attack risks.

Q: Can struct-of-arrays improve performance?
A: Yes, selectively used for better SIMD utilization and cache packing.

Q: How does custom shared memory queue compare to Linux kernel queues?
A: Custom queues avoid kernel overhead and provide lower jitter, critical for low latency.

Q: How to handle state persistence for queues?
A: Techniques like write-ahead logging (WAL) from databases can be adapted, but add complexity and latency.

This talk provides an expert, pragmatic roadmap for engineers building and optimizing low latency trading systems, emphasizing simplicity, domain knowledge, hardware-aware design, profiling, and system-level thinking.