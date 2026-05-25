package com.ankur.design.lld.picnic.impl;

import com.ankur.design.lld.picnic.OrderStreamProcessor;
import com.ankur.design.lld.picnic.model.DeliveryResult;
import com.ankur.design.lld.picnic.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Reads newline-delimited JSON orders from InputStream, then:
 *   1. Stops at maxOrders count OR maxTime deadline — whichever comes first
 *   2. Filters: keeps only delivered/cancelled (others count toward maxOrders)
 *   3. Groups by delivery_id
 *   4. Sorts deliveries by delivery_time asc, then delivery_id asc (tie-break)
 *   5. Within each delivery: orders sorted by order_id desc
 *   6. Writes JSON array of DeliveryResult to OutputStream
 *
 * Timeout design: ONE virtual thread reads lines into a BlockingQueue.
 * Main thread polls with remaining deadline — no Future allocation per line.
 */
public class PicnicOrderStreamProcessor implements OrderStreamProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final int maxOrders;
    private final Duration maxTime;

    public PicnicOrderStreamProcessor(int maxOrders, Duration maxTime) {
        this.maxOrders = maxOrders;
        this.maxTime   = maxTime;
    }

    @Override
    public void process(InputStream in, OutputStream out) {
        List<Order> orders = readOrders(in);
        List<DeliveryResult> results = aggregate(orders);
        write(results, out);
    }

    // ── Read ────────────────────────────────────────────────────────────────

    private static final String EOF_SENTINEL = "__EOF__";

    private List<Order> readOrders(InputStream in) {
        Instant deadline = Instant.now().plus(maxTime);
        List<Order> orders = new ArrayList<>();
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        // daemon thread reads all lines — blocks on I/O, dies when main thread finishes
        Thread readerThread = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    queue.put(line);
                }
            } catch (Exception ignored) {}
            try { queue.put(EOF_SENTINEL); } catch (InterruptedException ignored) {}
        });
        readerThread.setDaemon(true);
        readerThread.start();

        try {
            while (orders.size() < maxOrders) {
                long remainingMs = Duration.between(Instant.now(), deadline).toMillis();
                if (remainingMs <= 0) break;

                String line = queue.poll(remainingMs, TimeUnit.MILLISECONDS);
                if (line == null || EOF_SENTINEL.equals(line)) break; // timeout or EOF
                if (line.isBlank()) continue;                          // keep-alive \n

                try {
                    orders.add(MAPPER.readValue(line, Order.class));   // count ALL toward limit
                } catch (Exception e) {
                    System.err.println("[Picnic] Skipping malformed line: " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            readerThread.interrupt();
        }

        return orders;
    }

    // ── Aggregate ────────────────────────────────────────────────────────────

    private List<DeliveryResult> aggregate(List<Order> allOrders) {
        // only delivered/cancelled go into output — but all counted for maxOrders above
        Map<String, List<Order>> byDelivery = allOrders.stream()
                .filter(Order::isRelevant)
                .collect(Collectors.groupingBy(o -> o.delivery().deliveryId()));

        return byDelivery.entrySet().stream()
                .map(e -> {
                    String deliveryId = e.getKey();
                    var deliveryTime  = e.getValue().get(0).delivery().deliveryTime();
                    return DeliveryResult.from(deliveryId, deliveryTime, e.getValue());
                })
                // sort by delivery_time asc, then delivery_id asc (tie-break)
                .sorted(Comparator.comparing(DeliveryResult::deliveryTime)
                        .thenComparing(DeliveryResult::deliveryId))
                .toList();
    }

    // ── Write ────────────────────────────────────────────────────────────────

    private void write(List<DeliveryResult> results, OutputStream out) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(out, results);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write output", e);
        }
    }
}