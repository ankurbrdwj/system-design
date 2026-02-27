package com.ankur.design.hft.zerocopy;

import sun.misc.Unsafe;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Zero-Copy in Java for HFT — Six Techniques
 *
 * "Zero-copy" means moving data between buffers WITHOUT the CPU copying bytes.
 * In HFT this matters because:
 *   - A byte[] copy of a 64-byte order message costs ~10–20ns
 *   - At 500,000 messages/sec that is 5–10ms/sec wasted on pure copying
 *   - Plus every copy is a new heap allocation → GC pressure
 *
 * Techniques demonstrated:
 *   1. DirectByteBuffer          — off-heap; GC never touches it; DMA-friendly
 *   2. MappedByteBuffer (mmap)   — file mapped to virtual memory; kernel skips copies
 *   3. FileChannel.transferTo()  — sendfile() syscall; zero CPU copies file→socket
 *   4. ByteBuffer slice/view     — logical sub-buffer; no byte copying
 *   5. SBE-style fixed-offset    — encode order fields at known byte offsets; no parse
 *   6. Unsafe putLong/getLong    — raw memory read/write; bypasses all JVM safety
 *
 * Shared-memory IPC (Aeron pattern) is documented in hft/architecture/aeron.md.
 */
public class ZeroCopyDemo {

    // =========================================================================
    // 1. DirectByteBuffer — Off-Heap, GC-Invisible
    //
    // ByteBuffer.allocateDirect() allocates memory in NATIVE (C) heap, not JVM heap.
    // The GC never scans, never moves, never pauses for it.
    // The NIC can DMA directly from this memory — no copy into kernel buffer first.
    //
    // Use: order book storage, network receive buffers, ring buffer backing
    // =========================================================================

    /**
     * Order encoded directly into off-heap DirectByteBuffer at fixed offsets.
     *
     * Layout (64 bytes — fits one cache line):
     *   offset  0: orderId    (8 bytes, long)
     *   offset  8: symbolCode (8 bytes, long — symbol encoded as long, no String)
     *   offset 16: price      (8 bytes, long — fixed-point, e.g. 1.08345 → 108345L)
     *   offset 24: quantity   (8 bytes, long)
     *   offset 32: side       (1 byte,  0=BUY 1=SELL)
     *   offset 33: orderType  (1 byte,  0=LIMIT 1=MARKET 2=IOC)
     *   offset 34: status     (1 byte,  0=NEW 1=PARTIAL 2=FILLED 3=CANCELLED)
     *   offset 35: padding    (29 bytes to reach 64 bytes)
     */
    static final int ORDER_SIZE        = 64;
    static final int OFF_ORDER_ID      = 0;
    static final int OFF_SYMBOL        = 8;
    static final int OFF_PRICE         = 16;
    static final int OFF_QUANTITY      = 24;
    static final int OFF_SIDE          = 32;
    static final int OFF_ORDER_TYPE    = 33;
    static final int OFF_STATUS        = 34;

    static final class DirectOrderBook {
        private final ByteBuffer buffer;   // native memory — GC never sees it
        private final int        capacity;
        private int              count;

        DirectOrderBook(int capacity) {
            this.capacity = capacity;
            // One allocateDirect for the entire book — single off-heap region
            this.buffer = ByteBuffer.allocateDirect(capacity * ORDER_SIZE);
        }

        /** Write order fields at fixed offsets — zero allocation, zero copy. */
        void addOrder(long orderId, long symbolCode, long price, long qty,
                      byte side, byte orderType) {
            if (count >= capacity) throw new IllegalStateException("book full");
            int base = count++ * ORDER_SIZE;
            buffer.putLong(base + OFF_ORDER_ID,   orderId);
            buffer.putLong(base + OFF_SYMBOL,      symbolCode);
            buffer.putLong(base + OFF_PRICE,       price);
            buffer.putLong(base + OFF_QUANTITY,    qty);
            buffer.put    (base + OFF_SIDE,        side);
            buffer.put    (base + OFF_ORDER_TYPE,  orderType);
            buffer.put    (base + OFF_STATUS,      (byte) 0); // NEW
            // No new Object(), no String, no GC root added
        }

        /** Read a field at a fixed offset — just a getLong into a register. */
        long  getOrderId  (int i) { return buffer.getLong(i * ORDER_SIZE + OFF_ORDER_ID); }
        long  getSymbol   (int i) { return buffer.getLong(i * ORDER_SIZE + OFF_SYMBOL);   }
        long  getPrice    (int i) { return buffer.getLong(i * ORDER_SIZE + OFF_PRICE);    }
        long  getQty      (int i) { return buffer.getLong(i * ORDER_SIZE + OFF_QUANTITY); }
        byte  getSide     (int i) { return buffer.get    (i * ORDER_SIZE + OFF_SIDE);     }
        byte  getStatus   (int i) { return buffer.get    (i * ORDER_SIZE + OFF_STATUS);   }
        int   size        ()      { return count; }

        /**
         * Bulk copy from one slot to another — still zero-copy in the JVM sense
         * because no new byte[] is allocated; we use ByteBuffer.put(ByteBuffer).
         * The native memmove operates inside the off-heap region.
         */
        void copySlot(int src, int dst) {
            ByteBuffer srcView = buffer.duplicate();   // view, no copy
            srcView.limit(src * ORDER_SIZE + ORDER_SIZE).position(src * ORDER_SIZE);
            ByteBuffer dstView = buffer.duplicate();
            dstView.position(dst * ORDER_SIZE);
            dstView.put(srcView);                      // native memmove, no JVM copy
        }
    }


    // =========================================================================
    // 2. MappedByteBuffer (mmap) — File as Virtual Memory
    //
    // FileChannel.map() maps a file region into the process's virtual address space.
    // No read()/write() syscalls needed — access the file like a byte array.
    //
    // Kernel behaviour:
    //   - First access: page fault → kernel loads page from disk into page cache
    //   - Subsequent access: page cache hit → no syscall, no copy
    //   - Writes: dirty the page cache → kernel flushes asynchronously (or on force())
    //
    // Use: Chronicle Queue (persistent ring buffer), event sourcing journal,
    //      shared memory between processes (map same file with MAP_SHARED)
    // =========================================================================

    static final class MmapJournal implements AutoCloseable {
        private static final int  HEADER_SIZE  = 16;  // [writePos: 8B][readPos: 8B]
        private static final int  RECORD_SIZE  = 64;
        private static final int  CAPACITY     = 1024; // max records in ring

        private final RandomAccessFile file;
        private final FileChannel      channel;
        private final MappedByteBuffer buffer;
        private final int              totalSize;

        MmapJournal(String path) throws IOException {
            totalSize = HEADER_SIZE + CAPACITY * RECORD_SIZE;
            file    = new RandomAccessFile(path, "rw");
            file.setLength(totalSize);                    // pre-allocate file size
            channel = file.getChannel();
            buffer  = channel.map(FileChannel.MapMode.READ_WRITE, 0, totalSize);
            // buffer is backed by the file's page cache pages — no byte[] anywhere
        }

        /**
         * Append an order event to the journal.
         * The write goes directly into the OS page cache — zero copy from JVM.
         * The kernel will flush to disk asynchronously (or on force()).
         */
        void append(long orderId, long symbolCode, long priceFixed, long qty, byte side) {
            long writePos = buffer.getLong(0);            // current write position
            int  slot     = (int)(writePos % CAPACITY);
            int  base     = HEADER_SIZE + slot * RECORD_SIZE;

            buffer.putLong(base,      orderId);
            buffer.putLong(base + 8,  symbolCode);
            buffer.putLong(base + 16, priceFixed);
            buffer.putLong(base + 24, qty);
            buffer.put    (base + 32, side);

            buffer.putLong(0, writePos + 1);              // advance write cursor
            // No syscall. No kernel context switch. Just a dirty page in page cache.
        }

        /**
         * Durable flush — force dirty pages to disk.
         * Blocks until all dirty pages are written.
         * Call after each committed batch, not per-message.
         */
        void forceFlush() {
            buffer.force();   // msync(MS_SYNC) — blocks until fsync complete
        }

        /** Read an entry by absolute sequence number. */
        long readOrderId(long seqNum) {
            int slot = (int)(seqNum % CAPACITY);
            return buffer.getLong(HEADER_SIZE + slot * RECORD_SIZE);
        }

        @Override
        public void close() throws IOException {
            channel.close();
            file.close();
        }
    }


    // =========================================================================
    // 3. FileChannel.transferTo() — sendfile() Syscall
    //
    // Standard file-to-socket copy (4 copies):
    //   disk → page cache (DMA)
    //   page cache → user buffer (CPU copy)
    //   user buffer → socket buffer (CPU copy)
    //   socket buffer → NIC (DMA)
    //
    // FileChannel.transferTo() (2 copies, 0 CPU copies):
    //   disk → page cache (DMA)
    //   page cache → NIC directly via sendfile() (DMA)
    //   CPU never touches the data bytes at all.
    //
    // Use: sending historical market data files, FIX drop copy replay,
    //      sending large trade reports over TCP
    // =========================================================================

    static void zeroCopyFileToSocket(Path dataFile, SocketChannel socket) throws IOException {
        try (FileChannel src = FileChannel.open(dataFile, StandardOpenOption.READ)) {
            long size       = src.size();
            long position   = 0;
            while (position < size) {
                // transferTo maps to sendfile() on Linux — zero CPU copies
                long transferred = src.transferTo(position, size - position, socket);
                position += transferred;
            }
        }
        // Compared to InputStream → OutputStream loop:
        //   InputStream.read(byte[])  → CPU copy: page cache → byte[] → socket buffer
        //   transferTo()              → DMA:       page cache → NIC
        // At 10MB trade report: ~2ms (copy) vs ~200µs (transferTo)
    }


    // =========================================================================
    // 4. ByteBuffer Slice and Duplicate — Logical Views Without Copying
    //
    // slice()     — new ByteBuffer view starting at current position; shares backing memory
    // duplicate() — new ByteBuffer view from same backing memory; independent position
    //
    // Neither allocates bytes. They are "windows" into the same off-heap region.
    //
    // Use: parse sub-fields of a received FIX/ITCH message without copying
    //      split a large DirectByteBuffer ring into per-slot views
    // =========================================================================

    static final class SliceDemo {

        /**
         * Decode a received ITCH 5.0 AddOrder message from a NIC DMA buffer
         * using slice() — zero copy, zero allocation.
         *
         * ITCH AddOrder layout (36 bytes):
         *   byte 0:    message type (0x41 = 'A')
         *   bytes 1-2: stock locate
         *   bytes 3-4: tracking number
         *   bytes 5-12: timestamp (nanoseconds)
         *   bytes 13-20: order reference number
         *   byte 21:   buy/sell indicator
         *   bytes 22-25: shares
         *   bytes 26-31: stock (6-char ASCII right-padded)
         *   bytes 32-35: price (fixed-point, 4 decimal places)
         */
        static void decodeItchAddOrder(ByteBuffer nicDmaBuffer, int msgOffset) {
            // Create a slice starting at the message — zero copy, zero allocation
            ByteBuffer msg = nicDmaBuffer.duplicate();  // same backing memory
            msg.position(msgOffset).limit(msgOffset + 36);
            ByteBuffer slice = msg.slice();             // read-only view of 36 bytes

            // Read fields at fixed offsets — no scanning, no delimiters, no String parse
            byte  messageType      = slice.get(0);
            short stockLocate      = slice.getShort(1);
            long  timestampNanos   = slice.getLong(5) & 0x0000FFFFFFFFFFFFFL; // 6-byte varint
            long  orderRef         = slice.getLong(13);
            byte  buySell          = slice.get(21);
            int   shares           = slice.getInt(22);
            long  priceRaw         = Integer.toUnsignedLong(slice.getInt(32));
            // priceRaw: 4 decimal places implicit, e.g. 10008600 = $1000.8600

            // Symbol: read 6 bytes from offset 26 — no String allocation needed
            // Encode as long for zero-GC symbol lookup (see HotPathAllocationDemo)
            long symbolCode = ((long) slice.get(26) << 40) | ((long) slice.get(27) << 32)
                            | ((long) slice.get(28) << 24) | ((long) slice.get(29) << 16)
                            | ((long) slice.get(30) << 8)  | ((long) slice.get(31));

            // Zero allocations. Zero copies. ~50ns total.
            // If this were Jackson JSON parse: ~5–20µs + 10–50 object allocations.
        }

        /**
         * Split a large ring buffer DirectByteBuffer into per-slot views.
         * Each slot view shares the same off-heap memory — no copies.
         */
        static ByteBuffer[] createSlotViews(ByteBuffer ringBuffer, int slotSize, int slots) {
            ByteBuffer[] views = new ByteBuffer[slots];
            for (int i = 0; i < slots; i++) {
                ByteBuffer dup = ringBuffer.duplicate();
                dup.position(i * slotSize).limit((i + 1) * slotSize);
                views[i] = dup.slice();  // logical window, zero copy
            }
            return views;
            // Now views[42].putLong(0, orderId) writes directly into the off-heap ring.
        }
    }


    // =========================================================================
    // 5. SBE-Style Fixed-Offset Encoding — Zero-Parse, Zero-Copy
    //
    // Simple Binary Encoding (SBE) is the serialisation standard used by Aeron,
    // CME, and many HFT firms. The principle:
    //   - All fields at KNOWN BYTE OFFSETS (no length prefixes, no delimiters)
    //   - Fixed-size fields (no variable-length strings on hot path)
    //   - Encode/decode directly into a pre-allocated DirectByteBuffer
    //   - "Read" = a single getLong() at a known offset (~3ns)
    //   - No intermediate objects, no parse step, no copy
    //
    // Compare:
    //   Protobuf decode:  ~200–500ns + allocates message object + field objects
    //   JSON (Jackson):   ~5,000–20,000ns + allocates HashMap + String keys
    //   SBE decode:       ~10–30ns, zero allocation, zero copy
    // =========================================================================

    /** NewOrderSingle message — SBE-style, encodes into a pre-allocated buffer. */
    static final class SbeNewOrderSingle {
        // Schema constants — the "contract" shared between encoder and decoder
        static final int BLOCK_LENGTH   = 56;
        static final int TEMPLATE_ID    = 1;
        static final int SCHEMA_ID      = 100;
        static final int SCHEMA_VERSION = 1;

        // Field offsets (in bytes from start of message body)
        static final int OFF_CL_ORD_ID    = 0;   // 8 bytes (long)
        static final int OFF_SYMBOL_CODE  = 8;   // 8 bytes (long — symbol-as-long)
        static final int OFF_PRICE        = 16;  // 8 bytes (long — fixed-point scale 5)
        static final int OFF_ORDER_QTY    = 24;  // 8 bytes (long)
        static final int OFF_SIDE         = 32;  // 1 byte  (1=BUY, 2=SELL)
        static final int OFF_ORD_TYPE     = 33;  // 1 byte  (1=MARKET, 2=LIMIT, 3=IOC)
        static final int OFF_TIME_IN_FORCE= 34;  // 1 byte  (0=DAY, 3=IOC, 4=FOK)
        static final int OFF_TRANSACT_TIME= 35;  // 8 bytes (long — epoch nanos)
        static final int OFF_ACCOUNT      = 43;  // 8 bytes (long — account ID)
        // padding: 5 bytes to reach BLOCK_LENGTH=56

        private final ByteBuffer buffer;  // pre-allocated DirectByteBuffer — reused
        private int              offset;

        SbeNewOrderSingle(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        /** Encoder — write fields at fixed offsets directly into the pre-allocated buffer. */
        SbeNewOrderSingle wrapForEncode(int offset) {
            this.offset = offset;
            // Write SBE message header (8 bytes) inline
            buffer.putShort(offset,     (short) BLOCK_LENGTH);
            buffer.putShort(offset + 2, (short) TEMPLATE_ID);
            buffer.putShort(offset + 4, (short) SCHEMA_ID);
            buffer.putShort(offset + 6, (short) SCHEMA_VERSION);
            return this;
        }

        // Each setter: one putLong/putInt/put into the buffer — ~3–5ns each
        SbeNewOrderSingle clOrdId    (long v)  { buffer.putLong(offset + 8 + OFF_CL_ORD_ID,    v); return this; }
        SbeNewOrderSingle symbolCode (long v)  { buffer.putLong(offset + 8 + OFF_SYMBOL_CODE,  v); return this; }
        SbeNewOrderSingle price      (long v)  { buffer.putLong(offset + 8 + OFF_PRICE,        v); return this; }
        SbeNewOrderSingle orderQty   (long v)  { buffer.putLong(offset + 8 + OFF_ORDER_QTY,    v); return this; }
        SbeNewOrderSingle side       (byte v)  { buffer.put    (offset + 8 + OFF_SIDE,         v); return this; }
        SbeNewOrderSingle ordType    (byte v)  { buffer.put    (offset + 8 + OFF_ORD_TYPE,     v); return this; }
        SbeNewOrderSingle transactTime(long v) { buffer.putLong(offset + 8 + OFF_TRANSACT_TIME,v); return this; }
        SbeNewOrderSingle account    (long v)  { buffer.putLong(offset + 8 + OFF_ACCOUNT,      v); return this; }

        /** Decoder — read fields at fixed offsets. Zero copy, zero allocation. */
        SbeNewOrderSingle wrapForDecode(ByteBuffer src, int offset) {
            this.offset = offset;
            return this;
        }
        long  clOrdId()     { return buffer.getLong(offset + 8 + OFF_CL_ORD_ID);    }
        long  symbolCode()  { return buffer.getLong(offset + 8 + OFF_SYMBOL_CODE);  }
        long  price()       { return buffer.getLong(offset + 8 + OFF_PRICE);        }
        long  orderQty()    { return buffer.getLong(offset + 8 + OFF_ORDER_QTY);    }
        byte  side()        { return buffer.get    (offset + 8 + OFF_SIDE);         }
        long  transactTime(){ return buffer.getLong(offset + 8 + OFF_TRANSACT_TIME);}
        int   messageLength(){ return 8 + BLOCK_LENGTH; }
    }


    // =========================================================================
    // 6. sun.misc.Unsafe — Raw Memory: the Fastest Path
    //
    // Unsafe.putLong(address, value) writes 8 bytes to a native memory address.
    // No bounds check. No null check. No JVM safety net.
    // Used in: LMAX Disruptor (sequence), Aeron (ring buffer), Chronicle Map.
    //
    // WARNING: incorrect use causes JVM crash or silent data corruption.
    // Production use: wrap in a clearly documented utility class with known offsets.
    // =========================================================================

    static final class UnsafeOrderSlot {
        private static final Unsafe UNSAFE;
        static {
            try {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                UNSAFE = (Unsafe) f.get(null);
            } catch (Exception e) { throw new ExceptionInInitializerError(e); }
        }

        // Pre-allocated native memory block (equivalent to malloc)
        private final long baseAddress;
        private final int  slotCount;

        // Slot layout offsets (same as DirectOrderBook above)
        private static final int SLOT_SIZE       = 64;
        private static final int ORDER_ID_OFFSET = 0;
        private static final int PRICE_OFFSET    = 16;
        private static final int QTY_OFFSET      = 24;
        private static final int SIDE_OFFSET     = 32;

        UnsafeOrderSlot(int slotCount) {
            this.slotCount   = slotCount;
            this.baseAddress = UNSAFE.allocateMemory((long) slotCount * SLOT_SIZE);
            UNSAFE.setMemory(baseAddress, (long) slotCount * SLOT_SIZE, (byte) 0);
        }

        /**
         * Write order to native memory slot — raw putLong, no JVM overhead.
         * Benchmark: ~2–3ns per field vs DirectByteBuffer.putLong() ~4–5ns
         * (ByteBuffer has a bounds-check branch; Unsafe does not)
         */
        void writeOrder(int slot, long orderId, long price, long qty, byte side) {
            long base = baseAddress + (long) slot * SLOT_SIZE;
            UNSAFE.putLong (base + ORDER_ID_OFFSET, orderId);  // raw 8-byte write
            UNSAFE.putLong (base + PRICE_OFFSET,    price);
            UNSAFE.putLong (base + QTY_OFFSET,      qty);
            UNSAFE.putByte (base + SIDE_OFFSET,     side);
        }

        long readOrderId(int slot) { return UNSAFE.getLong(baseAddress + (long) slot * SLOT_SIZE + ORDER_ID_OFFSET); }
        long readPrice  (int slot) { return UNSAFE.getLong(baseAddress + (long) slot * SLOT_SIZE + PRICE_OFFSET); }

        /**
         * Ordered store — equivalent to volatile write but without full memory fence.
         * Guarantees: write is visible to all threads EVENTUALLY (store-release).
         * Used in LMAX Disruptor to publish sequence: lazySet equivalent.
         * ~1ns vs volatile write ~5ns (no StoreLoad fence needed for SPSC)
         */
        void publishSequence(long sequenceAddress, long value) {
            UNSAFE.putOrderedLong(null, sequenceAddress, value);
        }

        /** Volatile read — equivalent to volatile long read: full load-acquire. */
        long readVolatile(long address) {
            return UNSAFE.getLongVolatile(null, address);
        }

        void free() { UNSAFE.freeMemory(baseAddress); }
    }


    // =========================================================================
    // 7. Shared Memory IPC via MappedByteBuffer — Aeron IPC Pattern
    //
    // Two processes map the SAME FILE into their virtual address space.
    // When Process A writes to the mapped buffer, the OS page cache is dirtied.
    // Process B reads the same page — it sees the write without ANY copy or syscall.
    //
    // This is the mechanism behind Aeron IPC (aeron:ipc channel).
    // Chronicle Queue uses the same mechanism for its persistent ring buffer.
    //
    // Latency: ~200–500ns (same machine, page cache in RAM, no network)
    // =========================================================================

    static final class SharedMemoryRing {
        // File on /dev/shm (tmpfs backed by RAM — no disk I/O at all)
        private static final String SHM_PATH    = "/dev/shm/hft-order-ring";
        private static final int    RING_SLOTS  = 1024;             // power of 2
        private static final int    SLOT_SIZE   = 64;
        private static final int    HEADER_BYTES= 128;              // producer/consumer sequences
        private static final int    TOTAL_SIZE  = HEADER_BYTES + RING_SLOTS * SLOT_SIZE;
        private static final int    MASK        = RING_SLOTS - 1;  // for & instead of %

        // Header offsets (AtomicLong-equivalent using volatile reads/writes)
        private static final int PRODUCER_SEQ_OFFSET = 0;   // 8 bytes
        private static final int CONSUMER_SEQ_OFFSET = 64;  // 8 bytes (separate cache line)

        private final MappedByteBuffer buffer;
        private final FileChannel       channel;

        /** Producer (e.g. matching engine) opens or creates the shared region. */
        static SharedMemoryRing openProducer() throws IOException {
            return new SharedMemoryRing();
        }

        private SharedMemoryRing() throws IOException {
            RandomAccessFile raf = new RandomAccessFile(SHM_PATH, "rw");
            raf.setLength(TOTAL_SIZE);
            channel = raf.getChannel();
            buffer  = channel.map(FileChannel.MapMode.READ_WRITE, 0, TOTAL_SIZE);
            buffer.order(ByteOrder.nativeOrder());  // native byte order for fastest putLong
        }

        /**
         * Producer: claim next slot and write order data.
         * Uses a spin-wait if ring is full (backpressure).
         * No syscall. No lock. No GC.
         */
        void publish(long orderId, long symbolCode, long priceFixed, long qty, byte side) {
            long producerSeq = buffer.getLong(PRODUCER_SEQ_OFFSET);

            // Spin until slot is available (consumer has drained it)
            long consumerSeq;
            do {
                consumerSeq = buffer.getLong(CONSUMER_SEQ_OFFSET);
            } while (producerSeq - consumerSeq >= RING_SLOTS);  // ring full: busy-wait

            int  slot = (int)(producerSeq & MASK);
            int  base = HEADER_BYTES + slot * SLOT_SIZE;

            // Write payload — directly into mmap'd page cache (no copy)
            buffer.putLong(base,      orderId);
            buffer.putLong(base + 8,  symbolCode);
            buffer.putLong(base + 16, priceFixed);
            buffer.putLong(base + 24, qty);
            buffer.put    (base + 32, side);

            // Publish: advance producer sequence (visible to consumer via page cache)
            // Use putLong with volatile semantics (or putOrderedLong for lazySet)
            buffer.putLong(PRODUCER_SEQ_OFFSET, producerSeq + 1);
        }

        /**
         * Consumer: poll for next available order.
         * Returns true if an order was read, false if ring is empty.
         * No syscall. No lock. The data is already in RAM (page cache shared).
         */
        boolean poll(long[] orderOut) {
            long consumerSeq = buffer.getLong(CONSUMER_SEQ_OFFSET);
            long producerSeq = buffer.getLong(PRODUCER_SEQ_OFFSET);

            if (consumerSeq >= producerSeq) return false;  // ring empty

            int slot = (int)(consumerSeq & MASK);
            int base = HEADER_BYTES + slot * SLOT_SIZE;

            // Read payload directly from shared page cache — zero copy
            orderOut[0] = buffer.getLong(base);       // orderId
            orderOut[1] = buffer.getLong(base + 8);   // symbolCode
            orderOut[2] = buffer.getLong(base + 16);  // price
            orderOut[3] = buffer.getLong(base + 24);  // quantity
            orderOut[4] = buffer.get(base + 32);      // side

            buffer.putLong(CONSUMER_SEQ_OFFSET, consumerSeq + 1);
            return true;
        }
    }


    // =========================================================================
    // MAIN — run all demos
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("=== Zero-Copy in Java for HFT ===\n");

        // -----------------------------------------------------------------
        // Demo 1: DirectByteBuffer order book
        // -----------------------------------------------------------------
        System.out.println("--- 1. DirectByteBuffer Off-Heap Order Book ---");
        DirectOrderBook book = new DirectOrderBook(100);

        // EUR/USD encoded as long: 'E'=69,'U'=85,'R'=82,'U'=85,'S'=83,'D'=68
        long EURUSD = encodeSymbol("EURUSD");
        long GBPUSD = encodeSymbol("GBPUSD");

        book.addOrder(1001L, EURUSD, 108345L, 1_000_000L, (byte)0, (byte)0); // BUY LIMIT
        book.addOrder(1002L, GBPUSD, 127210L,   500_000L, (byte)1, (byte)0); // SELL LIMIT
        book.addOrder(1003L, EURUSD, 108350L, 2_000_000L, (byte)0, (byte)2); // BUY IOC

        System.out.printf("  Order book capacity: %d slots in native memory%n", 100);
        System.out.printf("  Memory used: %d bytes (off-heap, GC-invisible)%n", 100 * ORDER_SIZE);
        for (int i = 0; i < book.size(); i++) {
            System.out.printf("  [%d] orderId=%-6d symbol=%-6s price=%.5f qty=%-10d side=%s%n",
                i,
                book.getOrderId(i),
                decodeSymbol(book.getSymbol(i)),
                book.getPrice(i) / 100_000.0,
                book.getQty(i),
                book.getSide(i) == 0 ? "BUY" : "SELL");
        }
        System.out.println();

        // -----------------------------------------------------------------
        // Demo 2: MmapJournal
        // -----------------------------------------------------------------
        System.out.println("--- 2. MappedByteBuffer Event Sourcing Journal ---");
        Path journalPath = Path.of(System.getProperty("java.io.tmpdir"), "hft-journal.dat");
        try (MmapJournal journal = new MmapJournal(journalPath.toString())) {
            journal.append(1001L, EURUSD, 108345L, 1_000_000L, (byte)0);
            journal.append(1002L, GBPUSD, 127210L,   500_000L, (byte)1);
            // Flush every N events, not per-message
            journal.forceFlush();
            System.out.printf("  Journal at: %s%n", journalPath);
            System.out.printf("  Entry 0 orderId: %d%n", journal.readOrderId(0));
            System.out.printf("  Entry 1 orderId: %d%n", journal.readOrderId(1));
            System.out.printf("  No syscalls during append — page cache only%n");
        }
        Files.deleteIfExists(journalPath);
        System.out.println();

        // -----------------------------------------------------------------
        // Demo 3: SBE-style encode/decode
        // -----------------------------------------------------------------
        System.out.println("--- 3. SBE-Style Fixed-Offset Encoding ---");
        ByteBuffer wire = ByteBuffer.allocateDirect(256);  // pre-allocated wire buffer
        SbeNewOrderSingle encoder = new SbeNewOrderSingle(wire);
        encoder.wrapForEncode(0)
               .clOrdId(42_000_001L)
               .symbolCode(EURUSD)
               .price(108345L)          // 1.08345 × 10^5
               .orderQty(1_000_000L)
               .side((byte) 1)          // BUY
               .ordType((byte) 2)       // LIMIT
               .transactTime(System.nanoTime())
               .account(999_888L);

        // Decode — same buffer, zero copy
        SbeNewOrderSingle decoder = new SbeNewOrderSingle(wire);
        decoder.wrapForDecode(wire, 0);
        System.out.printf("  SBE decoded: clOrdId=%d symbol=%s price=%.5f qty=%d side=%s%n",
            decoder.clOrdId(),
            decodeSymbol(decoder.symbolCode()),
            decoder.price() / 100_000.0,
            decoder.orderQty(),
            decoder.side() == 1 ? "BUY" : "SELL");
        System.out.printf("  Message size: %d bytes (no length prefixes, no tag scanning)%n",
            encoder.messageLength());
        System.out.println();

        // -----------------------------------------------------------------
        // Demo 4: Unsafe raw memory
        // -----------------------------------------------------------------
        System.out.println("--- 4. Unsafe Raw Native Memory ---");
        UnsafeOrderSlot unsafe = new UnsafeOrderSlot(64);
        unsafe.writeOrder(0, 9001L, 108345L, 1_000_000L, (byte)0);
        unsafe.writeOrder(1, 9002L, 127210L,   500_000L, (byte)1);
        System.out.printf("  Unsafe slot 0: orderId=%d price=%.5f%n",
            unsafe.readOrderId(0), unsafe.readPrice(0) / 100_000.0);
        System.out.printf("  Unsafe slot 1: orderId=%d price=%.5f%n",
            unsafe.readOrderId(1), unsafe.readPrice(1) / 100_000.0);
        System.out.printf("  Raw native memory — no bounds check, no GC, ~2–3ns per field%n");
        unsafe.free();
        System.out.println();

        // -----------------------------------------------------------------
        // Latency comparison summary
        // -----------------------------------------------------------------
        System.out.println("--- Zero-Copy Latency Summary ---");
        System.out.printf("  %-45s %s%n", "Technique", "Latency");
        System.out.printf("  %-45s %s%n", "-".repeat(44), "-".repeat(20));
        System.out.printf("  %-45s %s%n", "byte[] copy (JVM heap)",                    "~10–20ns per 64B");
        System.out.printf("  %-45s %s%n", "DirectByteBuffer.putLong()",                "~4–5ns per field");
        System.out.printf("  %-45s %s%n", "MappedByteBuffer.putLong() (page cached)",  "~3–5ns per field");
        System.out.printf("  %-45s %s%n", "Unsafe.putLong() (raw native)",             "~2–3ns per field");
        System.out.printf("  %-45s %s%n", "SBE encode 64-byte order (all fields)",     "~20–30ns total");
        System.out.printf("  %-45s %s%n", "SBE decode 64-byte order (all fields)",     "~10–15ns total");
        System.out.printf("  %-45s %s%n", "FileChannel.transferTo() 1MB file",         "~50µs (vs ~500µs copy)");
        System.out.printf("  %-45s %s%n", "Shared mmap IPC (same machine)",            "~200–500ns");
        System.out.printf("  %-45s %s%n", "Protobuf encode 64-byte order",             "~200–500ns + allocs");
        System.out.printf("  %-45s %s%n", "JSON (Jackson) encode 64-byte order",       "~5,000–20,000ns");
    }


    // =========================================================================
    // Helpers — symbol encoding (same principle as HotPathAllocationDemo)
    // =========================================================================

    /** Encode up to 6 ASCII chars as a long — no String, no heap allocation. */
    static long encodeSymbol(String s) {
        long result = 0L;
        for (int i = 0; i < Math.min(s.length(), 6); i++) {
            result = (result << 8) | (s.charAt(i) & 0xFF);
        }
        return result;
    }

    /** Decode a symbol long back to String — only used in diagnostics, not hot path. */
    static String decodeSymbol(long code) {
        char[] buf = new char[6];
        int    len = 0;
        for (int i = 5; i >= 0; i--) {
            byte b = (byte)(code & 0xFF);
            if (b != 0) buf[len++] = (char) b;
            code >>= 8;
        }
        // Reverse
        char[] rev = new char[len];
        for (int i = 0; i < len; i++) rev[i] = buf[len - 1 - i];
        return new String(rev);
    }
}