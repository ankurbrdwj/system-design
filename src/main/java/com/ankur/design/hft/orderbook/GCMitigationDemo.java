package com.ankur.design.hft.orderbook;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keyword: Garbage Collection (GC) Mitigation
 *
 * Java GC "stop-the-world" pauses are the enemy of latency SLAs.
 * Three canonical mitigation strategies are demonstrated:
 *
 * 1. OBJECT POOLING — pre-allocate and recycle objects; zero GC pressure.
 * 2. OFF-HEAP MEMORY — use DirectByteBuffer; JVM GC never sees this memory.
 * 3. SYSTEM WARM-UP — force JIT compilation before the hot path begins.
 *
 * Run with: -Xmx256m -verbose:gc -XX:+PrintGCDetails to observe GC behaviour.
 */
public class GCMitigationDemo {

    // =========================================================================
    // 1. OBJECT POOLING
    // =========================================================================
    static final class Order {
        long   orderId;
        String symbol;
        double price;
        long   quantity;
        char   side;  // 'B' or 'S'

        /** Reset for reuse — no allocation, no GC. */
        void reset(long id, String sym, double px, long qty, char side) {
            this.orderId  = id;
            this.symbol   = sym;
            this.price    = px;
            this.quantity = qty;
            this.side     = side;
        }
    }

    static final class OrderPool {
        private final ArrayBlockingQueue<Order> pool;
        private final AtomicLong allocated = new AtomicLong();
        private final AtomicLong recycled  = new AtomicLong();

        OrderPool(int capacity) {
            pool = new ArrayBlockingQueue<>(capacity);
            for (int i = 0; i < capacity; i++) pool.offer(new Order());
            allocated.set(capacity);
        }

        Order acquire() {
            Order o = pool.poll();
            if (o == null) {  // pool exhausted — must allocate (triggers GC eventually)
                o = new Order();
                allocated.incrementAndGet();
            }
            return o;
        }

        void release(Order o) {
            o.reset(0, null, 0, 0, ' ');
            if (!pool.offer(o)) { /* pool full, let it be GC'd */ }
            else recycled.incrementAndGet();
        }

        long getAllocated() { return allocated.get(); }
        long getRecycled()  { return recycled.get(); }
    }

    // =========================================================================
    // 2. OFF-HEAP MEMORY (DirectByteBuffer)
    //    Stores a fixed-size order record outside JVM heap — GC invisible.
    //
    //    Layout per record (32 bytes):
    //      offset 0 : orderId  (8 bytes, long)
    //      offset 8 : price    (8 bytes, double)
    //      offset 16: quantity (8 bytes, long)
    //      offset 24: side     (1 byte, 'B'=66 / 'S'=83) + 7 padding
    // =========================================================================
    static final class OffHeapOrderStore {
        private static final int RECORD_SIZE = 32;
        private final ByteBuffer buffer;
        private final int capacity;
        private int writeIndex = 0;

        OffHeapOrderStore(int capacity) {
            this.capacity = capacity;
            this.buffer   = ByteBuffer.allocateDirect(capacity * RECORD_SIZE);
        }

        boolean write(long orderId, double price, long quantity, char side) {
            if (writeIndex >= capacity) return false;
            int base = writeIndex * RECORD_SIZE;
            buffer.putLong(base,      orderId);
            buffer.putDouble(base+8,  price);
            buffer.putLong(base+16,   quantity);
            buffer.put(base+24,       (byte) side);
            writeIndex++;
            return true;
        }

        long readOrderId(int index) { return buffer.getLong(index * RECORD_SIZE); }
        double readPrice(int index)  { return buffer.getDouble(index * RECORD_SIZE + 8); }
        long readQty(int index)      { return buffer.getLong(index * RECORD_SIZE + 16); }
        char readSide(int index)     { return (char) buffer.get(index * RECORD_SIZE + 24); }
        int size()                   { return writeIndex; }
    }

    // =========================================================================
    // 3. SYSTEM WARM-UP
    // =========================================================================
    static long processOrder(Order o) {
        // Simulate computation the JIT will optimise after warm-up
        return o.orderId ^ Double.doubleToLongBits(o.price) ^ o.quantity;
    }

    static void warmUp(OrderPool pool, int iterations) {
        System.out.println("[warm-up] Running " + iterations + " iterations to trigger JIT...");
        for (int i = 0; i < iterations; i++) {
            Order o = pool.acquire();
            o.reset(i, "WARM", 100.0 + i, 10, 'B');
            processOrder(o);
            pool.release(o);
        }
        // Hint to JVM that warm-up is done; real systems may also call
        // sun.misc.Unsafe.fullFence() or use JMH blackholes here.
        System.gc();
        System.out.println("[warm-up] Done. GC should have little work left on hot path.");
    }

    // =========================================================================

    public static void main(String[] args) throws Exception {
        int POOL_SIZE   = 1024;
        int ORDER_COUNT = 500_000;

        OrderPool pool = new OrderPool(POOL_SIZE);

        // ---- 3. WARM UP FIRST ----
        warmUp(pool, 50_000);

        // ---- 1. OBJECT POOL benchmark ----
        long startPool = System.nanoTime();
        long checksum = 0;
        for (int i = 0; i < ORDER_COUNT; i++) {
            Order o = pool.acquire();
            o.reset(i, "AAPL", 150.0 + i * 0.001, 100, i % 2 == 0 ? 'B' : 'S');
            checksum += processOrder(o);
            pool.release(o);
        }
        long poolNs = System.nanoTime() - startPool;

        System.out.printf("%n=== Object Pool ===%n");
        System.out.printf("Orders processed : %,d%n", ORDER_COUNT);
        System.out.printf("Objects allocated: %,d (vs %,d without pool)%n",
                pool.getAllocated(), ORDER_COUNT + POOL_SIZE);
        System.out.printf("Objects recycled : %,d%n", pool.getRecycled());
        System.out.printf("Total time       : %,d ms%n", poolNs / 1_000_000);
        System.out.printf("Avg per order    : %,d ns  (checksum=%d)%n", poolNs / ORDER_COUNT, checksum);

        // ---- 2. OFF-HEAP benchmark ----
        OffHeapOrderStore store = new OffHeapOrderStore(ORDER_COUNT);

        long startOffHeap = System.nanoTime();
        for (int i = 0; i < ORDER_COUNT; i++) {
            store.write(i, 150.0 + i * 0.001, 100, i % 2 == 0 ? 'B' : 'S');
        }
        long writeNs = System.nanoTime() - startOffHeap;

        long startRead = System.nanoTime();
        long readChecksum = 0;
        for (int i = 0; i < ORDER_COUNT; i++) {
            readChecksum += store.readOrderId(i) + Double.doubleToLongBits(store.readPrice(i));
        }
        long readNs = System.nanoTime() - startRead;

        System.out.printf("%n=== Off-Heap DirectByteBuffer ===%n");
        System.out.printf("Records stored   : %,d%n", store.size());
        System.out.printf("Write throughput : %,d ns avg per record%n", writeNs / ORDER_COUNT);
        System.out.printf("Read  throughput : %,d ns avg per record  (checksum=%d)%n",
                readNs / ORDER_COUNT, readChecksum);
        System.out.println("Off-heap memory is invisible to GC — no pause risk.");
    }
}