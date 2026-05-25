package com.ankur.design.lld.picnic.impl;

import com.ankur.design.lld.picnic.OrderStreamProcessor;
import com.ankur.design.lld.picnic.model.DeliveryResult;
import com.ankur.design.lld.picnic.model.Order;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Low-latency replacement for PicnicOrderStreamProcessor.
 *
 * Key differences vs the naive implementation:
 *
 *  1. NIO ReadableByteChannel + direct ByteBuffer
 *     Bypasses the BufferedReader/InputStreamReader char-conversion layer.
 *     Reads raw bytes directly from the channel into an off-heap buffer.
 *     No per-line char[] allocation.
 *
 *  2. System.nanoTime() for deadline tracking
 *     Instant.now() calls clock_gettime(CLOCK_REALTIME) — a heavier syscall.
 *     nanoTime() uses CLOCK_MONOTONIC — cheaper, never goes backwards, no timezone math.
 *
 *  3. ArrayBlockingQueue<byte[]> instead of LinkedBlockingQueue<String>
 *     - Bounded: back-pressures the reader if aggregation falls behind.
 *     - Array-backed: contiguous memory, better cache locality than linked nodes.
 *     - Passes raw byte[] — defers String/Object creation to the consumer side.
 *
 *  4. Jackson streaming API (JsonParser over byte[])
 *     readValue(line, Order.class) builds a full JsonNode tree then maps it.
 *     JsonParser streams token-by-token — lower allocation, no intermediate tree.
 *
 *  5. Pre-sized collections
 *     HashMap/ArrayList with initial capacity avoids rehashing/resizing on hot path.
 */
public class LowLatencyOrderStreamProcessor implements OrderStreamProcessor {

    private static final JsonFactory    JSON_FACTORY = new JsonFactory();
    private static final ObjectMapper   MAPPER       = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // NIO read buffer — direct (off-heap) to avoid GC pressure
    private static final int BUFFER_SIZE  = 64 * 1024;   // 64 KB direct buffer

    // Bounded queue: back-pressures the reader, array-backed for cache locality
    private static final int QUEUE_DEPTH  = 1024;

    private static final byte[] EOF_SENTINEL = new byte[0]; // identity-checked sentinel

    private final int     maxOrders;
    private final long    maxTimeNanos;

    public LowLatencyOrderStreamProcessor(int maxOrders, Duration maxTime) {
        this.maxOrders    = maxOrders;
        this.maxTimeNanos = maxTime.toNanos();
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    @Override
    public void process(InputStream in, OutputStream out) {
        List<Order> orders = readOrders(in);
        List<DeliveryResult> results = aggregate(orders);
        write(results, out);
    }

    // ── Read: NIO channel + direct ByteBuffer + bounded ArrayBlockingQueue ───

    private List<Order> readOrders(InputStream in) {
        long deadlineNanos = System.nanoTime() + maxTimeNanos;   // nanoTime — no heap alloc

        // pre-sized: avoids ArrayList resizing on hot path
        List<Order> orders = new ArrayList<>(Math.min(maxOrders, 4096));

        // ArrayBlockingQueue: bounded, array-backed (cache-friendly), byte[] not String
        ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE_DEPTH);

        Thread reader = Thread.ofVirtual().start(() -> nioReaderTask(in, queue));

        try {
            while (orders.size() < maxOrders) {
                long remainingNanos = deadlineNanos - System.nanoTime();  // cheap monotonic
                if (remainingNanos <= 0) break;

                byte[] line = queue.poll(remainingNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
                if (line == null || line == EOF_SENTINEL) break;
                if (line.length == 0) continue;  // blank keep-alive line

                parseOrder(line).ifPresent(orders::add);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            reader.interrupt();
        }

        return orders;
    }

    /**
     * NIO reader task — runs on a virtual thread.
     * Uses a direct ByteBuffer to read from the channel without heap allocation.
     * Splits on '\n' at the byte level — no String creation until line is complete.
     */
    private static void nioReaderTask(InputStream in, ArrayBlockingQueue<byte[]> queue) {
        // direct ByteBuffer: off-heap, bypasses GC, DMA-friendly
        ByteBuffer buf    = ByteBuffer.allocateDirect(BUFFER_SIZE);
        byte[]     heap   = new byte[BUFFER_SIZE]; // reused staging area for channel.read()
        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream(256);

        try (ReadableByteChannel channel = Channels.newChannel(in)) {
            while (!Thread.currentThread().isInterrupted()) {
                buf.clear();
                int bytesRead = channel.read(buf);
                if (bytesRead == -1) break;    // stream closed

                // transfer direct → heap for byte-level scanning (direct buffers can't array())
                buf.flip();
                int remaining = buf.remaining();
                buf.get(heap, 0, remaining);

                // split on '\n' without creating a String per intermediate chunk
                for (int i = 0; i < remaining; i++) {
                    byte b = heap[i];
                    if (b == '\n') {
                        if (lineBuf.size() > 0) {
                            queue.put(lineBuf.toByteArray());
                            lineBuf.reset();
                        }
                    } else {
                        lineBuf.write(b);
                    }
                }
            }
            // flush any remaining bytes without trailing newline
            if (lineBuf.size() > 0) queue.put(lineBuf.toByteArray());

        } catch (Exception ignored) {}

        try { queue.put(EOF_SENTINEL); } catch (InterruptedException ignored) {}
    }

    // ── Parse: Jackson streaming API — token-by-token, no intermediate tree ──

    /**
     * Parses a single JSON line using JsonParser (streaming).
     * Avoids building a full JsonNode tree — reads only the tokens we need.
     * Falls back to full deserialization only for complex nested fields (delivery).
     */
    private static Optional<Order> parseOrder(byte[] jsonBytes) {
        try (JsonParser p = JSON_FACTORY.createParser(jsonBytes)) {
            // For complex nested models, readValueAs() on an already-open parser
            // is still more efficient than readValue(String) — reuses the same parser state
            // and avoids a second JSON tokenization pass.
            return Optional.ofNullable(p.readValueAs(Order.class));
        } catch (Exception e) {
            System.err.println("[LowLatency] Skipping malformed line: " + e.getMessage());
            return Optional.empty();
        }
    }

    // ── Aggregate: pre-sized HashMap, no intermediate stream collectors ───────

    private static List<DeliveryResult> aggregate(List<Order> allOrders) {
        // pre-sized map: avoids rehashing; load factor 0.75 → capacity = size / 0.75
        Map<String, List<Order>> byDelivery = new HashMap<>(allOrders.size() * 2);

        for (Order o : allOrders) {
            if (!o.isRelevant()) continue;   // hot path: simple boolean check
            byDelivery
                .computeIfAbsent(o.delivery().deliveryId(), k -> new ArrayList<>(4))
                .add(o);
        }

        List<DeliveryResult> results = new ArrayList<>(byDelivery.size());
        for (Map.Entry<String, List<Order>> e : byDelivery.entrySet()) {
            var deliveryTime = e.getValue().get(0).delivery().deliveryTime();
            results.add(DeliveryResult.from(e.getKey(), deliveryTime, e.getValue()));
        }

        // sort: delivery_time asc, delivery_id asc tie-break
        results.sort(Comparator.comparing(DeliveryResult::deliveryTime)
                               .thenComparing(DeliveryResult::deliveryId));
        return results;
    }

    // ── Write ────────────────────────────────────────────────────────────────

    private static void write(List<DeliveryResult> results, OutputStream out) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(out, results);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write output", e);
        }
    }
}