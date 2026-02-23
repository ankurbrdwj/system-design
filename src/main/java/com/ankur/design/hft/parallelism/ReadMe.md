Key Themes and Concepts
Moore’s Law and Hardware Trends

Historically, transistor counts have doubled roughly every 18 months (Moore’s Law), but clock speeds plateaued around 2003, limiting performance gains from faster single cores.
Instead of faster cores, chips now feature more cores (multi-core architectures), shifting the performance improvement strategy from frequency scaling to parallelism.
However, the number of cores has not scaled as rapidly as transistor counts due to economic and practical constraints, with typical high-end chips today having dozens (not thousands) of cores.
Concurrency vs Parallelism

Concurrency is about program structure and correctly coordinating access to shared resources, involving complex synchronization primitives (locks, semaphores, etc.).
Parallelism is about using multiple cores to get an answer faster, by partitioning the problem into independent tasks, which is typically easier to reason about than concurrency.
Parallelism is strictly an optimization—if you only have one core, you can still do the computation sequentially.
When Does Parallelism Help?

Parallelism only improves performance if the overhead of managing parallel tasks is outweighed by the speed gains from concurrent execution.
Problems that are inherently sequential or have strong data dependencies won’t benefit from parallelism.
A dataflow dependency diagram can quickly reveal if a problem is parallelizable (independent tasks) or not (sequential dependencies).
Common Pitfalls in Parallel Code

Shared mutable state is a major source of bugs and performance bottlenecks in parallel programs.
The accumulator anti-pattern occurs when multiple threads contend to update a shared variable, causing data races or excessive locking overhead that hurts performance.
Safer approaches include avoiding shared state by partitioning data and combining results afterwards.
Divide and Conquer Parallelism Pattern

The fundamental parallel programming pattern is divide-and-conquer: recursively split the problem into smaller independent subproblems, solve them in parallel, and combine the results.
This approach is natural for recursive data structures (e.g., trees) and is well-supported by Java’s Fork/Join framework (introduced in Java 7), which manages task splitting and joining efficiently.
Performance Factors Affecting Parallelism

Splitting cost: How expensive it is to divide the problem into subproblems.
Task overhead: The cost of creating, scheduling, and joining tasks.
Combination cost: The cost to merge partial results (cheap for sums, expensive for merging complex data structures like hash maps).
Hardware locality: Cache performance and memory access patterns are critical—parallelism suffers if tasks frequently cause cache misses or memory contention.
Data size and workload per element: Parallelism needs enough data volume and computational intensity per element to overcome overhead and achieve speedup.
Streams and Parallel Streams in Java 8+

Streams provide a declarative programming model that separates “what” from “how,” enabling easy switching between sequential and parallel execution.
Parallel streams internally use the fork/join framework for task management.
Not all streams parallelize well: The efficiency depends heavily on the source data structure (arrays split nicely, linked lists do not), the cost of splitting, and the nature of the operations.
Operations sensitive to encounter order (e.g., limit(), skip(), findFirst()) limit parallelism because they require preserving element order. If order doesn’t matter, marking streams as unordered can improve parallelism.
Practical Advice for Using Parallelism

Start with sequential streams; only consider parallelism after profiling and identifying performance bottlenecks.
Use analysis and measurement to decide if parallelism yields actual speedup—parallelism is not guaranteed to be faster.
Avoid premature optimization; only parallelize when business or performance requirements justify it.
JVM Warm-up and Parallelism

JVM warm-up involves class loading, JIT compilation, and initializing runtime structures.
Parallel streams require warm-up of the common ForkJoinPool threads, which incurs initial overhead but threads remain alive for reuse to amortize costs.
Cache warm-up can also affect early performance of parallel tasks.
Timeline Table of Historical and Technological Progression
Timeframe	Event / Trend	Impact on Parallelism
Pre-2003	Moore’s Law increases transistor count and clock speeds	Single-core performance improved; sequential speedup
2003 onwards	Clock speeds plateau; power consumption limits reached	Shift to multi-core chips; parallelism needed for gains
2007-2008	Multi-core chips become mainstream	Java concurrency tools evolve: thread pools, concurrent collections
Java 7 (2011)	Introduction of Fork/Join framework	Efficient task management for divide-and-conquer parallelism
Java 8 (2014)	Introduction of Streams API and parallel streams	Easier declarative parallelism, but requires understanding of costs
Comparative Table: Concurrency vs Parallelism
Aspect	Concurrency	Parallelism
Definition	Program structure for cooperative tasks	Using multiple cores to speed up task
Focus	Correct and efficient access to shared resources	Partitioning work to run simultaneously
Complexity	High; requires locks, semaphores, etc.	Lower; independent tasks easier to reason about
Goal	Correctness and throughput	Speedup and reduced wall-clock time
Common Issues	Data races, deadlocks	Overhead, task splitting, merging cost
Example Java Support	Threads, locks, concurrent collections	Fork/Join framework, parallel streams
Practical Guidelines for Effective Parallelism
Analyze Problem Parallelizability:

Use dataflow dependency to check for task independence.
Avoid inherently sequential computations.
Avoid Shared Mutable State:

Partition data to avoid contention.
Use immutable or thread-safe data structures.
Consider Data Structure and Source:

Arrays and ArrayLists split efficiently and have good locality.
Linked lists and iterators split poorly, limiting parallelism.
Balance Workload and Data Size:

Small data sets or trivial operations often run faster sequentially.
Large data sets or computationally expensive tasks justify parallelism.
Beware Operations Sensitive to Encounter Order:

Operations like limit(), skip(), findFirst() can hinder parallel speedup.
Use .unordered() when order does not matter to improve parallelism.
Profile and Measure:

Always benchmark before and after applying parallelism.
Use performance targets tied to business needs as optimization triggers.
Core Concepts and Definitions
Term	Definition
Moore’s Law	Doubling of transistor counts approximately every 18 months
Concurrency	Structuring code to handle multiple tasks efficiently and correctly, often involving shared state
Parallelism	Running multiple tasks simultaneously on multiple cores for faster completion
Fork/Join Framework	Java 7 API for managing recursive task decomposition and joining results
Streams API	Java 8 framework for functional, declarative data processing, supporting easy parallelism
Encounter Order	The order in which elements appear in a stream, which can affect parallel execution
Accumulator Anti-pattern	Shared mutable variable updated concurrently, leading to contention and poor performance
Final Key Insights
Parallelism is a powerful but non-trivial optimization that requires careful problem analysis and understanding of overheads.
Not all problems or data structures are suitable for parallel execution; knowing the underlying data and operations is crucial.
Java’s fork/join framework and parallel streams provide high-level abstractions, but they are not a silver bullet.
Start with sequential code; optimize with parallelism only when justified by profiling and performance requirements.
Understanding hardware factors like cache locality and memory access patterns is essential for achieving good parallel speedup.
