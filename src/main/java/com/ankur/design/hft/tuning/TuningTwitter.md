Summary of JVM Performance Tuning and Latency Reduction Insights from Twitter Experience
This talk, presented by a former Twitter runtime systems engineer, delves into practical experiences and nuanced strategies for tuning Java Virtual Machines (JVMs) to optimize performance, particularly focusing on latency reduction in large-scale web services.

Background and Context
The speaker worked extensively at Twitter, primarily on JVM runtime systems, responsible for ensuring JVMs operated smoothly and efficiently.
Key experience includes tuning JVMs for performance, optimizing garbage collection (GC), and memory footprint reduction.
After Twitter, the speaker worked at Oracle on the Nasorn JavaScript runtime on JVM and is currently involved in a startup focusing on operational databases.
Twitter’s backend transitioned from Ruby to mostly Scala and Java services, heavily JVM-based.
Core Latency Challenge in JVM-Based Web Services
Latency is the critical enemy for web services: slow responses lead to poor user experience.
On the JVM, garbage collection is the dominant cause of latency.
Other latency factors (locking, thread scheduling, I/O, algorithm inefficiencies) exist but were not the focus.
Memory tuning is crucial because the garbage collector’s behavior depends heavily on memory usage patterns.
Memory Tuning Strategies and Observations
Memory footprint tuning is important because less memory usage reduces GC pressure, leading to better latency.
Key memory tuning goals include:
Reducing memory footprint to avoid out-of-memory (OOM) errors.
Ensuring sufficient free memory overhead to maintain application responsiveness.
Memory inefficiencies often come from:
Large amounts of data.
Inefficient data representation (“fat data”).
Memory leaks (application bugs, not discussed in detail).
JVM heap sizes typically exceed actual used memory; JVM holds allocated memory for efficiency.
Live set size after GC (the amount of memory still used) is a critical metric to monitor.
If memory is insufficient, and more can be allocated, “throwing more memory” at the JVM often improves performance.
JVM Object Memory Layout and Optimization
Every JVM object has a 16-byte header (64-bit architecture: two machine words).
Arrays have even more overhead (minimum ~24 bytes for zero-length arrays).
Small classes with primitive fields incur padding to align to multiples of 8 bytes.
Subclassing increases padding due to per-subclass memory layout, potentially inflating memory usage.
Flattening class hierarchies and minimizing object indirection can reduce memory overhead.
Compressed object pointers reduce pointer size by assuming 8-byte alignment, allowing addressing up to 32 GB heap efficiently.
Beyond ~32 GB heap, JVM disables compressed pointers, causing a significant memory overhead increase.
Heap sizes between 32–48 GB are inefficient due to pointer inflation; recommended to jump above 48 GB if more memory is needed.
Avoid boxed primitive wrappers (e.g., Integer instead of int) as they consume significantly more memory.
Aspect	Compressed Pointers (≤32 GB Heap)	Uncompressed Pointers (>32 GB Heap)
Pointer size	32 bits	64 bits
Maximum efficient heap size	~32 GB	>32 GB
Memory overhead	Reduced by 25–33%	Higher due to larger pointers
Recommended heap sizing advice	Stay below 32 GB or >48 GB	Avoid 32–48 GB range
Practical JVM Tuning Tips
Use verbose GC logging to observe GC events and memory usage patterns.
Tune Young Generation (Eden + Survivor spaces) carefully:
Eden space allocation is fast; minor GCs happen when Eden fills up.
Survivor spaces hold objects surviving young collections before tenuring to old generation.
Ideal survivor space occupancy is under 100%; above that causes premature promotion to old generation, increasing old gen GC pressure.
For throughput-oriented services (e.g., queuing), use throughput collectors without adaptive sizing.
For latency-sensitive web services, use throughput collectors with adaptive sizing or Concurrent Mark-Sweep (CMS) collector.
CMS collector requires careful memory over-provisioning (~25–33% more than observed max working set) to avoid full GC pauses.
Full GCs cause catastrophic pauses (minutes-long), which are unacceptable in distributed systems.
Sometimes reducing the size of the Young Generation helps if many objects survive minor GCs, pushing them to CMS-managed old gen.
Reduce the number of threads if possible, as threads are GC roots and increase GC workload.
When tuning fails, scaling horizontally by splitting application load across multiple JVMs/processes is recommended.
Data Structure and Library Considerations
Avoid using generated Thrift objects directly as in-memory domain objects because they contain costly overhead (e.g., bit sets for optional fields add ~72 bytes per object).
Refactor Thrift-generated classes into lightweight, specialized in-memory representations that share common data (e.g., interning country codes) to drastically reduce memory usage.
Libraries can have surprising memory footprints; profiling is essential to understand real memory usage.
Example: Google Guava’s default concurrent maps consume more memory due to internal striping/concurrency levels but can be tuned by reducing concurrency level to one, reducing memory by 8x.
Off-Heap Storage Use
Twitter experimented with off-heap storage, especially with Cassandra committers tuning off-heap buffers.
Off-heap storage requires manual memory management and encoding/decoding of raw bytes.
It is effective if data lifetimes are linear and well-bounded.
Off-heap buffers do not benefit from JVM GC and add complexity, so should be used wisely.
Noteworthy Anomalies and Lessons
The craziest OOM error involved permanent generation (permgen) exhaustion caused by classloader leaks, despite ample heap memory.
This was due to soft references not being cleared timely, causing permgen to fill up.
JVM tuning requires profiling and understanding of specific application object lifecycles.
Summary Table: JVM GC Generations and Their Roles
Generation	Description	GC Type	Key Tuning Point
Young Generation	Eden + Survivor spaces; short-lived objects	Stop-the-world copy collection	Size Eden and Survivor spaces to fit request objects; minimize survivor overflow
Old Generation	Long-lived objects (tenured)	Concurrent Mark-Sweep (CMS) or G1GC	Over-provision memory to avoid fragmentation; minimize full GC pauses
Permanent Gen*	Class metadata (pre-Java 8)	N/A (removed in Java 8)	Watch for permgen leaks in older JVMs
*Note: Permanent Generation removed in Java 8, replaced by Metaspace.

Key Takeaways
Garbage collection tuning is paramount for JVM-based web services latency.
Memory footprint and GC behavior must be profiled and understood deeply.
JVM object layout and pointer compression have significant impact on memory usage.
Libraries and generated code can introduce surprising overheads—profiling is essential.
Off-heap storage can be beneficial but requires careful management.
JVM tuning is an iterative process involving profiling, memory adjustment, and sometimes architectural changes like splitting workloads.
JVM tuning and optimization are distinct; tuning works within constraints, optimization changes algorithms or code.
Final Note
This talk provides a comprehensive real-world perspective on JVM memory and GC tuning, emphasizing practical lessons from Twitter’s scale and complexity, with insights applicable to JVM-based services facing latency and memory pressure challenges.

