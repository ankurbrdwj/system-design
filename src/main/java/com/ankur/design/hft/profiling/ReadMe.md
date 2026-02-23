Core Concepts and Overview
Async Profiler is an open-source Java profiler designed to detect performance bottlenecks, native memory leaks, I/O issues, and other pitfalls in JVM applications.
The session covered profiling theory, limitations of existing profilers, and advanced features of async profiler, including its tight integration with IntelliJ IDEA.
Profilers are essential for diagnosing performance issues, but the choice of profiler depends on the problem nature and environment (development vs production).
Profiling Categories
Instrumenting Profilers: Modify bytecode to record every method entry/exit.

Pros: Full execution trace with timings.
Cons: High overhead and distortion of real execution.
Sampling Profilers: Take periodic snapshots of thread states.

Pros: Lightweight, suitable for production.
Cons: Limited by JVM safe points and may miss long-running code without loops.
Limitations of Traditional Profiling APIs
Standard Java APIs for stack traces are safe point biased; they only capture stack traces at JVM safe points, which may miss significant code sections, especially long-running or native methods.
JVM cannot distinguish between a native method that is busy (CPU intensive) or sleeping (waiting on I/O), leading to misleading profiling data.
Flight Recorder and VisualVM also suffer from similar safe point biases.
Native method profiling is limited by lack of visibility into native call states.
Async Profiler’s Innovations
Uses AsyncGetCallTrace API, an internal HotSpot JVM API, that enables capturing stack traces asynchronously, not limited by safe points.
Combines Linux kernel’s perf event open API (hardware performance counters and kernel-level sampling) with JVM-level async stack traces.
This hybrid approach overcomes many traditional limitations by capturing both native and JVM frames accurately.
Does not require root privileges but needs kernel tuning (e.g., perf_event_paranoid sysctl).
Provides interactive flame graphs visualizing CPU usage, native calls, and kernel activity.
Key Profiling Modes Supported by Async Profiler
Profiling Mode	Description
CPU Profiling	Samples CPU usage, including native and JVM frames.
Wall Clock Profiling	Samples thread states regardless of running/sleeping status, useful for detecting wait times.
Lock Profiling	Measures contention on locks, showing where threads spend time waiting.
Allocation Profiling	Samples object allocations, focusing on slow paths (outside thread-local allocation buffers).
Native Memory Profiling	Profiles native allocations via system calls like malloc and mmap, useful for detecting leaks.
Hardware Counters	Profiles cache misses, branch misses, page faults, context switches using perf hardware counters.
Demonstrations and Use Cases
StringBuilder Test: Showed that traditional profilers misleadingly report most time in native methods; async profiler correctly identifies costly array copying.
Socket I/O Test: Differentiated between busy and idle socket threads by excluding sleeping threads via async profiler.
File Reading Test: Demonstrated that very large buffers cause page faults and slowdowns due to kernel memory management.
Lock Contention Test: Identified excessive lock contention on DatagramChannel sockets causing performance bottlenecks.
Native Memory Leak Detection: Profiling native malloc and mmap calls revealed unclosed streams causing memory leaks.
Allocation Profiling: Pinpointed inefficient string splitting causing excessive allocations and GC pressure.
Cache Miss Profiling: Identified performance degradation due to cache misses when accessing large arrays.
Integration and Usage
Async Profiler is integrated into IntelliJ IDEA Ultimate (2018.3+) with GUI support for CPU, allocation, lock, and wall clock profiling.
Also available as a command-line tool and a Java API enabling programmatic profiling control (start, stop, resume).
Profiling can be attached:
At JVM startup (recommended for best accuracy and debug info).
Dynamically at runtime (may lose some accuracy but still effective).
Supports various JVMs based on HotSpot (AdoptOpenJDK, OpenJDK, Zing), but not supported on OpenJ9.
No Windows support yet; primarily Linux-based due to dependency on perf and kernel APIs.
Comparisons and Misconceptions
Tool/Feature	Notes
Async Profiler	Uses async stack traces + perf events, shows native & JVM stacks, lower overhead, open source.
JProfiler	Commercial, traditionally safe point biased, recently added experimental async sampling mode.
Flight Recorder	Modern, low overhead, but suffers from safe point bias and limited native profiling.
VisualVM	Common free tool, but less accurate for production-level profiling and native calls.
Best Practices and Recommendations
Use sampling profilers in production to minimize overhead.
Tune JVM options (e.g., -XX:+PreserveFramePointer, debug info flags) for best profiling accuracy.
Use allocation profiling to target GC and allocation hotspots rather than premature optimization.
Combine CPU, lock, allocation, and native memory profiling for comprehensive diagnostics.
Utilize interactive flame graphs to visualize hot spots and drill down to problematic code sections.
For containerized environments, adjust container security policies to allow perf syscalls or fallback to timer-based profiling.
Future Directions and Open Questions
Support for Java virtual threads (Project Loom) is planned but not yet available.
Potential to implement method-based profiling triggers (start profiling upon entering specific Java methods).
Integration with new JVM APIs for allocation profiling (post JDK 11) is under consideration.
Continuous improvements to reduce overhead and improve accuracy in complex scenarios.
Additional Notes
Async Profiler is licensed under Apache 2.0 and hosted on GitHub for community contributions.
Documentation and sample code are publicly available but may require updates.
Users are encouraged to provide feedback, report bugs, and contribute pull requests, especially for documentation.
Key Takeaways
Async Profiler is a powerful, low-overhead sampling profiler that overcomes many traditional profiling limitations by combining JVM async stack traces with kernel-level perf events.
It supports diverse profiling modes (CPU, wall clock, locks, allocations, native memory) with rich visualization in IntelliJ IDEA.
Essential for diagnosing subtle performance issues such as native memory leaks, lock contention, cache misses, and inefficient native calls.
Provides actionable insights for optimizing JVM applications in production environments.
Active development and community involvement ensure ongoing enhancements and extended support.
This comprehensive session provided deep insights into JVM profiling challenges and showcased async profiler as a versatile and practical tool for developers seeking to optimize Java applications effectively.