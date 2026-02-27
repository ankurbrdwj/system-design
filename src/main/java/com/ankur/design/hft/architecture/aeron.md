# How Aeron Achieves Ultra-Low Latency

## What is Aeron?

Aeron is an open-source messaging library built by Real Logic (Martin Thompson,
Todd Montgomery) designed for **reliable, ordered, low-latency message transport**
between processes or across a network. It is used in production by Coinbase,
Adaptive Financial Consulting, and numerous HFT firms as the backbone for
inter-service communication in trading systems.

Aeron is not a message broker (no central server like Kafka). It is a
**point-to-point or multicast transport** — think of it as UDP made reliable,
without any of the kernel networking overhead.

---

## The Core Problem Aeron Solves

Standard Java networking stack for sending one message:

```
Application
    ↓  write()
JVM socket buffer
    ↓  system call (context switch into kernel)
Linux TCP/UDP stack  →  lots of copying, locks, queuing
    ↓  DMA
NIC hardware
    ↓  wire
```

Every system call costs **500ns–5µs** in context-switch overhead alone.
At 300,000 messages/sec that is **150ms–1.5 seconds** wasted in kernel calls
per second — unacceptable for a system with a 50µs end-to-end SLA.

---

## The Seven Techniques That Make Aeron Fast

### 1. Kernel Bypass (the biggest win)

Aeron avoids the Linux networking kernel stack entirely by writing directly
to a shared memory region that maps to the NIC.

```
Application
    ↓  memory write (no syscall)
Shared memory region (mmap'd)
    ↓  DMA (no CPU copy)
NIC hardware
    ↓  wire
```

**In a data centre:** OpenOnload (Solarflare) — the NIC polls the shared
memory region directly. Zero kernel involvement.

**On AWS:** DPDK (Data Plane Development Kit) — user-space NIC driver;
the application polls the NIC ring buffer directly without any kernel call.

**In-process (same JVM):** Aeron IPC uses a memory-mapped file on
`/dev/shm` (RAM-backed tmpfs). Two processes share the same physical
memory pages — a "send" is just a memory write; a "receive" is just a
memory read. No network hardware involved at all.

Coinbase uses this for communication between the matching engine process
and the risk engine process running on the same machine.

---

### 2. Lock-Free Ring Buffer (the log)

Aeron stores messages in a **pre-allocated ring buffer** — a fixed-size
circular array backed by a memory-mapped file.

```
  ┌───┬───┬───┬───┬───┬───┬───┬───┐
  │ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │ 7 │   ← ring buffer (power-of-2 size)
  └───┴───┴───┴───┴───┴───┴───┴───┘
        ↑ tail (publisher writes here)
                  ↑ head (subscriber reads here)
```

**Publisher** claims a slot with a single CAS (Compare-And-Swap) on
the `tail` sequence number — no lock, no blocking.

**Subscriber** reads from `head`, advances with a plain store — no lock.

This is the same principle as the LMAX Disruptor (see `disruptor/Readme.md`),
but extended to work across process and network boundaries.

No locks → no mutex contention → no OS scheduler involvement → predictable
sub-microsecond latency.

---

### 3. Busy-Spin Polling (no blocking I/O)

Traditional messaging: subscriber **blocks** on `recv()` waiting for data.
When a message arrives, the OS wakes the thread (scheduler latency: 10–100µs).

Aeron subscriber **busy-spins** — it continuously polls the ring buffer head
in a tight loop. When a message appears, it is processed immediately with
**0µs scheduler wake-up latency**.

```java
// Aeron subscriber pattern — busy-spin on hot path
IdleStrategy idle = new BusySpinIdleStrategy();   // or NoOpIdleStrategy
while (running) {
    int fragmentsRead = subscription.poll(handler, FRAGMENT_LIMIT);
    idle.idle(fragmentsRead);   // BusySpin: Thread.onSpinWait(); no sleep
}
```

The CPU core is dedicated to this loop — it is **pinned** to a specific core
(CPU affinity) so the OS never context-switches it away. This matches Coinbase's
"isolating CPUs from user-space tasks to minimize interference" approach.

**Trade-off:** burns 100% of one CPU core continuously. Acceptable in HFT
where latency matters more than CPU utilisation.

---

### 4. Simple Binary Encoding (SBE) for Serialisation

Aeron is agnostic to serialisation format, but pairs naturally with **SBE**
(Simple Binary Encoding), also by Real Logic, which Coinbase uses explicitly.

```
JSON / Protobuf:
  allocates Strings, byte[], Map objects → GC pressure → latency spikes

SBE:
  encodes directly into a pre-allocated DirectByteBuffer
  → zero allocation on the hot path
  → fixed-size fields → no length prefix parsing needed
  → reads fields directly from buffer offsets (like a C struct)
```

A SBE-encoded 64-byte order message can be written in **~20ns** and read
in **~10ns** vs **~500ns** for JSON with Jackson.

---

### 5. Zero-Copy Message Passing

Aeron never copies message bytes between buffers. The publisher writes once
into the ring buffer. The subscriber reads directly from the same memory.

```
Standard queue (e.g. LinkedBlockingQueue):
  publisher  → new byte[] copy into queue node → GC pressure
  subscriber → copy out of queue node → another allocation

Aeron ring buffer:
  publisher  → write directly into pre-allocated ring slot (1 copy)
  subscriber → read directly from ring slot (0 copy)
```

For in-process IPC via `/dev/shm`: the publisher and subscriber literally
share the same physical RAM pages — the "message" is never copied at all.

---

### 6. Reliable UDP (not TCP)

Aeron runs over **UDP**, not TCP. TCP has hidden latency costs:

| TCP behaviour | Latency cost |
|---|---|
| Nagle's algorithm | buffers small packets, adds delay |
| Congestion control | throttles sender based on estimated loss |
| Head-of-line blocking | one lost packet blocks all subsequent |
| ACK delay | receiver waits 40ms before sending ACK |

Aeron implements its own **reliability layer on top of UDP**:
- Receiver sends **NAK** (negative acknowledgement) only for gaps it detects
- Sender **retransmits only the missing packet** — no head-of-line blocking
- No Nagle, no congestion control, no ACK delay

This gives reliability without TCP's latency overhead. Median UDP + Aeron
transport latency on a 10GbE network: **~1–3µs** vs TCP: **~20–100µs**.

---

### 7. CPU Pinning and NUMA Awareness

Aeron's driver thread is pinned to a dedicated CPU core using OS-level
thread affinity (`pthread_setaffinity_np` on Linux via JNI).

This means:
- The driver is **never context-switched** off its core
- Its working set (ring buffer) stays **hot in L1/L2 cache**
- No cache miss penalty from another thread evicting Aeron's data

On multi-socket servers (NUMA), Aeron allocates the ring buffer on the
**same NUMA node** as the driver thread's CPU — memory access stays local
(~80ns) rather than crossing the QPI bus to another socket (~150ns).

---

## Aeron vs Alternatives

| Transport | Latency (p50) | Latency (p99) | Use case |
|---|---|---|---|
| Kafka | 1–5ms | 10–50ms | Durable event streaming, audit log |
| ActiveMQ / RabbitMQ | 0.5–2ms | 5–20ms | Enterprise messaging |
| ZeroMQ | 50–200µs | 500µs–2ms | Low-latency pub/sub |
| **Aeron IPC** | **<1µs** | **<5µs** | Same-machine inter-process |
| **Aeron UDP** | **1–3µs** | **10–30µs** | Cross-machine, LAN |
| LMAX Disruptor | <100ns | <500ns | Single-JVM inter-thread only |

**Rule:**
- Same JVM, thread-to-thread → **LMAX Disruptor**
- Same machine, process-to-process → **Aeron IPC** (`aeron:ipc`)
- Cross-machine, LAN → **Aeron UDP** (`aeron:udp`)
- Durable audit / replay / cross-DC → **Kafka**

Coinbase uses Aeron for the hot path (matching engine ↔ risk engine ↔ OMS)
and Kafka for the cold path (trade reporting, audit trail, settlement).

---

## Aeron Cluster (Raft Consensus)

Coinbase's derivatives exchange uses **Aeron Cluster** — a built-in
replicated state machine on top of Aeron transport, using the **Raft
consensus algorithm**.

```
         ┌─────────────────────────────────────────────┐
         │              Aeron Cluster                  │
         │                                             │
  Client →  [Leader node]  →  log replication          │
         │       ↓         →  [Follower A]              │
         │  State machine  →  [Follower B]              │
         │  (matching eng) ←  consensus (2-of-3 quorum) │
         └─────────────────────────────────────────────┘
```

- All orders arrive at the **leader** via Aeron UDP
- Leader appends to the **replicated log** — followers must acknowledge
- Once a quorum (2 of 3) acknowledges, the event is committed
- The **matching engine** (deterministic state machine) processes the committed log
- If the leader fails, Raft elects a new leader in **<200ms** — trading resumes

This gives fault tolerance (N/2 + 1 nodes can fail) while preserving
the deterministic ordering that a matching engine requires.

---

## Summary: Why Aeron is Fast

```
Problem                          → Aeron Solution
─────────────────────────────────────────────────────
Kernel system-call overhead      → kernel bypass (DPDK / OpenOnload / IPC)
Lock contention on queues        → lock-free ring buffer (CAS on sequence)
Scheduler wake-up latency        → busy-spin polling (no blocking recv())
Serialisation allocation (GC)    → SBE zero-allocation encoding
Buffer copies                    → zero-copy shared memory ring
TCP latency (Nagle, HOL)         → reliable UDP with selective NAK
Cache eviction from context swap → CPU pinning + NUMA-local allocation
```

**Net result:** Aeron IPC achieves **sub-microsecond** median latency between
two processes on the same machine. Aeron UDP achieves **1–3µs** across a LAN.
This is why Coinbase chose it as the nervous system of their derivatives
exchange, targeting a <50µs end-to-end round-trip SLA.