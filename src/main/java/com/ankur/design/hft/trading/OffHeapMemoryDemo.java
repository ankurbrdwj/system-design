package com.ankur.design.hft.trading;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/**
 * Memory Management → Off-heap in Java
 *
 * From the README:
 *   "C++ manual memory management (new/delete, custom allocators) maps to
 *    Java's off-heap memory via sun.misc.Unsafe or ByteBuffer.allocateDirect().
 *    HFT Java systems avoid GC pauses by allocating off-heap — libraries like
 *    Chronicle Map and Agrona do this."
 *
 *   "GC pauses are Java's equivalent of C++'s memory allocation overhead on
 *    the hot path — both cause latency spikes you need to eliminate."
 *
 * Three strategies demonstrated:
 *   1. ON-HEAP allocation  — baseline; subject to GC.
 *   2. OFF-HEAP DirectByteBuffer — outside GC heap; manual lifecycle.
 *   3. OBJECT POOL — on-heap objects recycled; minimises allocation rate.
 *
 * Layout of one order record in off-heap buffer (40 bytes):
 *   offset  0 : orderId   (long,  8 bytes)
 *   offset  8 : price     (double,8 bytes)
 *   offset 16 : quantity  (long,  8 bytes)
 *   offset 24 : timestamp (long,  8 bytes)
 *   offset 32 : side      (byte,  1 byte)  + 7 bytes padding
 */
public class OffHeapMemoryDemo {

    private static final int RECORD_BYTES = 40;

    // =========================================================================
    // 1. On-heap baseline: new Order() per event
    // =========================================================================
    record Order(long orderId, double price, long quantity, long timestamp, byte side) {}

    static long onHeapBenchmark(int count) {
        long checksum = 0;
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            Order o = new Order(i, 150.0 + i * 0.001, 100, System.nanoTime(), (byte)'B');
            checksum ^= o.orderId();
        }
        System.out.printf("  On-heap  : %,d ns total  %,d ns/alloc  checksum=%d%n",
                System.nanoTime() - start, (System.nanoTime() - start) / count, checksum);
        return checksum;
    }

    // =========================================================================
    // 2. Off-heap DirectByteBuffer: memory outside JVM heap → GC never sees it.
    //    Equivalent to C++ new/delete or a custom allocator slab.
    //    The JVM GC will never pause to collect these objects.
    // =========================================================================
    static final class OffHeapOrderStore {
        private final ByteBuffer buffer;  // native memory — not on JVM heap
        private int writeIdx = 0;

        OffHeapOrderStore(int capacity) {
            // ByteBuffer.allocateDirect() → mmap / malloc outside JVM heap
            buffer = ByteBuffer.allocateDirect(capacity * RECORD_BYTES);
        }

        void write(long orderId, double price, long qty, long ts, byte side) {
            int base = writeIdx * RECORD_BYTES;
            buffer.putLong(base,      orderId);
            buffer.putDouble(base+8,  price);
            buffer.putLong(base+16,   qty);
            buffer.putLong(base+24,   ts);
            buffer.put(base+32,       side);
            writeIdx++;
        }

        long readId(int i)     { return buffer.getLong(i * RECORD_BYTES); }
        double readPrice(int i){ return buffer.getDouble(i * RECORD_BYTES + 8); }
        int size()             { return writeIdx; }
    }

    static long offHeapBenchmark(int count) {
        OffHeapOrderStore store = new OffHeapOrderStore(count);
        long checksum = 0;
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            store.write(i, 150.0 + i * 0.001, 100, System.nanoTime(), (byte)'B');
        }
        long writeNs = System.nanoTime() - start;

        start = System.nanoTime();
        for (int i = 0; i < count; i++) checksum ^= store.readId(i);
        long readNs = System.nanoTime() - start;

        System.out.printf("  Off-heap : write=%,d ns/rec  read=%,d ns/rec  checksum=%d%n",
                writeNs / count, readNs / count, checksum);
        return checksum;
    }

    // =========================================================================
    // 3. Object Pool: pre-allocate on-heap objects, recycle via a pool.
    //    Zero allocation on the hot path after warm-up → minimal GC pressure.
    //    Equivalent to C++ memory pool / slab allocator.
    // =========================================================================
    static final class MutableOrder {
        long orderId; double price; long quantity; long timestamp; byte side;

        void populate(long id, double px, long qty, long ts, byte sd) {
            this.orderId = id; this.price = px; this.quantity = qty;
            this.timestamp = ts; this.side = sd;
        }
    }

    static final class OrderPool {
        private final ArrayDeque<MutableOrder> pool = new ArrayDeque<>();
        private long totalAcquired = 0, newAllocations = 0;

        OrderPool(int preAlloc) {
            for (int i = 0; i < preAlloc; i++) pool.push(new MutableOrder());
        }

        MutableOrder acquire() {
            totalAcquired++;
            MutableOrder o = pool.poll();
            if (o == null) { newAllocations++; return new MutableOrder(); }
            return o;
        }

        void release(MutableOrder o) { pool.push(o); }

        void printStats() {
            System.out.printf("  Pool: acquired=%,d  new-allocs=%,d (%.1f%% pool hits)%n",
                    totalAcquired, newAllocations,
                    100.0 * (totalAcquired - newAllocations) / totalAcquired);
        }
    }

    static long poolBenchmark(int count) {
        OrderPool pool = new OrderPool(64); // pre-warm with 64 objects
        long checksum = 0;
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            MutableOrder o = pool.acquire();
            o.populate(i, 150.0 + i * 0.001, 100, System.nanoTime(), (byte)'B');
            checksum ^= o.orderId;
            pool.release(o);  // return to pool — no GC
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("  Pool     : %,d ns total  %,d ns/cycle  checksum=%d%n",
                elapsed, elapsed / count, checksum);
        pool.printStats();
        return checksum;
    }

    // =========================================================================

    public static void main(String[] args) {
        System.out.println("====================================================");
        System.out.println("  Memory Management → Off-heap / Object Pool");
        System.out.println("====================================================\n");

        int COUNT = 1_000_000;

        // Warm up JIT
        System.out.println("[warm-up]");
        onHeapBenchmark(100_000);
        offHeapBenchmark(100_000);
        poolBenchmark(100_000);

        System.out.printf("%n[benchmark — %,d records]%n", COUNT);
        onHeapBenchmark(COUNT);
        offHeapBenchmark(COUNT);
        poolBenchmark(COUNT);

        System.out.println("\nKey insights:");
        System.out.println("  • Off-heap: GC never sees these objects → no stop-the-world pauses");
        System.out.println("  • Object pool: zero allocation on hot path after warm-up");
        System.out.println("  • On-heap: every 'new' is a potential GC trigger");
        System.out.println("  • Chronicle Map / Agrona use off-heap for production HFT");
    }
}
