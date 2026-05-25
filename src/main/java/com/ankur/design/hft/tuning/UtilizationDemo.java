package com.ankur.design.hft.tuning;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TOPIC: Queueing Theory — utilization above ~70% causes response time to blow up.
 *
 * LITTLE'S LAW: L = λ * W
 *   L = average number of items in the system
 *   λ = arrival rate (items/second)
 *   W = average time an item spends in the system (wait + service)
 *
 * M/M/1 QUEUE formula (single server, Poisson arrivals, exponential service time):
 *   Utilization (ρ)     = λ / μ       (arrival rate / service rate)
 *   Response Time (W)   = ServiceTime / (1 - ρ)
 *   Queue Depth (Lq)    = ρ² / (1 - ρ)
 *
 * The (1 - ρ) denominator causes HYPERBOLIC BLOWUP:
 *   ρ = 0.50 → W = 2 * ServiceTime   (2x)
 *   ρ = 0.65 → W = 2.86 * ServiceTime
 *   ρ = 0.80 → W = 5 * ServiceTime
 *   ρ = 0.90 → W = 10 * ServiceTime
 *   ρ = 0.95 → W = 20 * ServiceTime
 *   ρ = 0.99 → W = 100 * ServiceTime ← catastrophic
 *
 * RULE OF THUMB: Keep utilization below 70% for stable, predictable response times.
 *
 * Real-world HFT applications:
 *   - NIC receive queue: if utilization > 70%, packets start queuing → tail latency explodes
 *   - Order matching engine: if CPU utilization > 70%, orders queue up → execution latency spikes
 *   - Thread pool: if all threads busy > 70% of time → task queue grows → response time degrades
 */
public class UtilizationDemo {

    // Service time: each "job" takes 10ms to process
    static final long SERVICE_TIME_MS = 10;
    static final int  QUEUE_CAPACITY  = 10_000;

    // -------------------------------------------------------------------------
    // Show the analytical formula results
    // -------------------------------------------------------------------------

    static void showQueueingTheory() {
        System.out.println("--- Analytical: M/M/1 Queue Formula ---");
        System.out.println("  ResponseTime = ServiceTime / (1 - Utilization)");
        System.out.println("  QueueDepth   = Utilization^2 / (1 - Utilization)");
        System.out.println();
        System.out.printf("  %-14s %-20s %-15s%n", "Utilization", "ResponseTime", "Queue Depth");
        System.out.printf("  %-14s %-20s %-15s%n", "-----------", "------------", "-----------");

        double[] utils = {0.10, 0.30, 0.50, 0.65, 0.70, 0.80, 0.90, 0.95, 0.99};
        for (double rho : utils) {
            double responseTime = SERVICE_TIME_MS / (1.0 - rho);
            double queueDepth   = (rho * rho) / (1.0 - rho);
            String flag = rho >= 0.70 ? " ← DANGER" : "";
            System.out.printf("  %-14.0f%% %-20.1f ms  %-10.1f%s%n",
                    rho * 100, responseTime, queueDepth, flag);
        }
        System.out.println();
        System.out.println("  Notice: at 95% utilization, response time is 20x the service time.");
        System.out.println("  The queue grows without bound if arrivals ever briefly exceed service rate.");
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Simulation state shared between producer and server threads
    // -------------------------------------------------------------------------

    static class Stats {
        final AtomicLong totalWaitNs = new AtomicLong(0);
        final AtomicLong processedCount = new AtomicLong(0);
        final AtomicLong droppedCount = new AtomicLong(0);
        volatile long maxQueueDepth = 0;
        volatile long maxWaitNs = 0;

        void recordWait(long waitNs, long queueDepth) {
            totalWaitNs.addAndGet(waitNs);
            processedCount.incrementAndGet();
            if (queueDepth > maxQueueDepth) maxQueueDepth = queueDepth;
            if (waitNs > maxWaitNs) maxWaitNs = waitNs;
        }
    }

    // Message: carries the timestamp when it entered the queue
    static class Message {
        final long enqueueTimeNs;
        Message(long ts) { this.enqueueTimeNs = ts; }
    }

    // -------------------------------------------------------------------------
    // Run simulation for given utilization level
    // -------------------------------------------------------------------------

    /**
     * @param utilization 0.0 to 1.0 — fraction of server capacity consumed by arrivals
     * @param durationMs  how long to run the simulation
     */
    static Stats runSimulation(double utilization, long durationMs) throws InterruptedException {
        Stats stats = new Stats();
        AtomicBoolean running = new AtomicBoolean(true);
        BlockingQueue<Message> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

        // Arrival rate: if service time = 10ms and utilization = 0.95,
        // then arrivalRate = utilization / serviceTime = 0.95 / 10ms = 95 msgs/sec
        // Inter-arrival delay = 1 / arrivalRate = serviceTime / utilization
        long interArrivalDelayMs = (long) (SERVICE_TIME_MS / utilization);
        // Cap to prevent division by zero with very low utilization
        if (interArrivalDelayMs < 1) interArrivalDelayMs = 1;

        final long finalInterArrivalDelayMs = interArrivalDelayMs;

        // Server thread: dequeues messages, simulates SERVICE_TIME_MS work, records wait
        Thread server = Thread.ofPlatform().name("server").start(() -> {
            while (running.get() || !queue.isEmpty()) {
                try {
                    Message msg = queue.poll();
                    if (msg != null) {
                        long waitNs = System.nanoTime() - msg.enqueueTimeNs;
                        long depth = queue.size();
                        // Simulate service time (actual sleep for demo purposes)
                        Thread.sleep(SERVICE_TIME_MS);
                        stats.recordWait(waitNs, depth);
                    } else {
                        Thread.sleep(1); // idle — wait for next message
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // Producer thread: sends messages at the computed arrival rate
        Thread producer = Thread.ofPlatform().name("producer").start(() -> {
            long endTime = System.currentTimeMillis() + durationMs;
            while (System.currentTimeMillis() < endTime) {
                try {
                    boolean offered = queue.offer(new Message(System.nanoTime()));
                    if (!offered) {
                        stats.droppedCount.incrementAndGet(); // queue full → dropped
                    }
                    Thread.sleep(finalInterArrivalDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            running.set(false);
        });

        producer.join();
        server.join(durationMs + 5000); // give server time to drain
        return stats;
    }

    // -------------------------------------------------------------------------
    // Print results
    // -------------------------------------------------------------------------

    static void printResults(String label, double utilization, Stats stats) {
        long count = stats.processedCount.get();
        if (count == 0) {
            System.out.printf("%-40s — no messages processed%n", label);
            return;
        }
        double avgWaitMs = (double) stats.totalWaitNs.get() / count / 1_000_000;
        double maxWaitMs = stats.maxWaitNs / 1_000_000.0;

        // Theoretical response time from formula (wait only, not including service)
        double theoreticalWaitMs = SERVICE_TIME_MS * utilization / (1.0 - utilization);

        System.out.printf("%-40s processed=%,5d  dropped=%,d  avgWait=%6.1fms  maxWait=%7.1fms  maxQueueDepth=%,d  (theory: %.1fms)%n",
                label, count, stats.droppedCount.get(),
                avgWaitMs, maxWaitMs, stats.maxQueueDepth, theoreticalWaitMs);
    }

    // -------------------------------------------------------------------------
    // Show queue depth growth analytically
    // -------------------------------------------------------------------------

    static void showQueueGrowth() {
        System.out.println("--- Queue Depth Growth: Analytical ---");
        System.out.println("  If arrivals briefly exceed service rate, queue grows unboundedly.");
        System.out.println("  At 95% utilization, queue is already at ρ²/(1-ρ) = 18 messages deep.");
        System.out.println("  Any burst doubles this immediately — recovery takes minutes.");
        System.out.println();
        System.out.println("  At 65% utilization, queue depth = 1.2 messages deep.");
        System.out.println("  A burst doubles this briefly — recovery takes seconds.");
        System.out.println();
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== UtilizationDemo — Queueing Theory ===");
        System.out.println();
        System.out.printf("Service time per request: %dms%n", SERVICE_TIME_MS);
        System.out.println();

        showQueueingTheory();
        showQueueGrowth();

        System.out.println("--- Live Simulation ---");
        System.out.println("  Running producer+server simulation for each utilization level...");
        System.out.println("  (This takes ~30 seconds due to real Thread.sleep() for service time)");
        System.out.println();

        long simDurationMs = 5_000; // 5 seconds per scenario

        // BAD: 95% utilization — response times blow up
        System.out.println("Running BAD scenario (95% utilization)...");
        Stats badStats = runSimulation(0.95, simDurationMs);
        printResults("BAD  (95% utilization):", 0.95, badStats);

        // GOOD: 65% utilization — stable response times
        System.out.println("Running GOOD scenario (65% utilization)...");
        Stats goodStats = runSimulation(0.65, simDurationMs);
        printResults("GOOD (65% utilization):", 0.65, goodStats);

        // Very good: 50% utilization
        System.out.println("Running SAFE scenario (50% utilization)...");
        Stats safeStats = runSimulation(0.50, simDurationMs);
        printResults("SAFE (50% utilization):", 0.50, safeStats);

        System.out.println();
        System.out.println("Key takeaways:");
        System.out.println("  1. M/M/1 formula: ResponseTime = ServiceTime / (1 - Utilization)");
        System.out.println("  2. Below 70% utilization: response time is bounded and stable");
        System.out.println("  3. Above 85% utilization: small increases cause explosive response time growth");
        System.out.println("  4. At 95% utilization: queue grows unboundedly under ANY brief burst");
        System.out.println("  5. In HFT: design systems for 50-60% peak utilization");
        System.out.println("     — leave headroom for burst traffic (market open, news events)");
        System.out.println("  6. Horizontal scaling reduces per-server utilization:");
        System.out.println("     2 servers at 50% each is far better than 1 server at 95%");
    }
}