package com.ankur.design.lld.tddkata.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class OrderStreamPrcessorImpl implements OrderStreamProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final OrderProcessingService orderProcessingService;
    private final int maxOrders;
    private final Duration maxTime;

    public OrderStreamPrcessorImpl(OrderProcessingService orderProcessingService,
                                   int maxOrders, Duration maxTime) {
        this.orderProcessingService = orderProcessingService;
        this.maxOrders = maxOrders;
        this.maxTime   = maxTime;
    }

    @Override
    public void process(InputStream input, OutputStream output) {
        List<Order> orders = readOrders(input);
        List<DeliveryGroup> result = orderProcessingService.processOrders(orders);
        write(result, output);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    private static final String EOF = "__EOF__";

    private List<Order> readOrders(InputStream in) {
        Instant deadline = Instant.now().plus(maxTime);
        List<Order> orders = new ArrayList<>();
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        Thread reader = new Thread(() -> {
            try (var br = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = br.readLine()) != null) queue.put(line);
            } catch (Exception ignored) {}
            try { queue.put(EOF); } catch (InterruptedException ignored) {}
        });
        reader.setDaemon(true);
        reader.start();

        try {
            while (orders.size() < maxOrders) {
                long remaining = Duration.between(Instant.now(), deadline).toMillis();
                if (remaining <= 0) break;

                String line = queue.poll(remaining, TimeUnit.MILLISECONDS);
                if (line == null || EOF.equals(line)) break;
                if (line.isBlank()) continue;                   // keep-alive

                try {
                    orders.add(MAPPER.readValue(line, Order.class)); // created also counted
                } catch (Exception e) {
                    System.err.println("Skipping malformed line: " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            reader.interrupt();
        }

        return orders;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    private void write(List<DeliveryGroup> result, OutputStream out) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(out, result);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write output", e);
        }
    }
}