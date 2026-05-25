package com.ankur.design.lld.tddkata.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderStreamTest {

    // ── OrderProcessingService unit tests ────────────────────────────────────

    OrderProcessingService orderProcessingService;

    @BeforeEach
    void setup() {
        orderProcessingService = new OrderProcessingService();
    }

    @Test
    void filterRemovesCreatedOrders() {
        Delivery d = new Delivery(1, Instant.now());
        List<Order> orders = List.of(
                new Order("1", "delivered", d, 100),
                new Order("2", "created",   d, 200),
                new Order("3", "cancelled", d, 300)
        );
        List<Order> result = orderProcessingService.filter(orders);
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(o -> o.orderStatus().equals("created")));
    }

    @Test
    void groupByDeliveryId() {
        Delivery d = new Delivery(1, Instant.now());
        List<Order> orders = List.of(
                new Order("1", "delivered", d, 100),
                new Order("2", "cancelled", d, 300)
        );
        Map<Integer, List<Order>> result = orderProcessingService.group(orders);
        assertTrue(result.containsKey(1));
        assertEquals(2, result.get(1).size());
    }

    @Test
    void aggregateDeliveredStatusWhenAtLeastOneDelivered() {
        Delivery d = new Delivery(1, Instant.now());
        List<Order> orders = List.of(
                new Order("1", "delivered", d, 100),
                new Order("2", "cancelled", d, 300)
        );
        DeliveryGroup result = orderProcessingService.aggregate(1, orders);
        assertEquals("delivered", result.deliveryStatus());
        assertEquals(100L, result.totalAmount());
    }

    // ── OrderStreamPrcessor integration tests ────────────────────────────────
    // Input: one JSON object per line (not a JSON array)
    // Factory wires maxOrders + maxTime

    private final OrderStreamProcessorFactoryImpl factory = new OrderStreamProcessorFactoryImpl();

    /** Happy path: 2 deliveries, sorted by deliveryTime ascending. */
    @Test
    void happyPath() {
        String input = """
                {"orderId":"o3","orderStatus":"delivered","delivery":{"deliveryId":1,"deliveryTime":"2022-05-20T11:50:48Z"},"amount":6477}
                {"orderId":"o1","orderStatus":"cancelled","delivery":{"deliveryId":1,"deliveryTime":"2022-05-20T11:50:48Z"},"amount":null}
                {"orderId":"o2","orderStatus":"delivered","delivery":{"deliveryId":2,"deliveryTime":"2022-05-21T09:00:00Z"},"amount":1000}
                """;

        String output = run(input, 100, Duration.ofSeconds(5));

        // d1 before d2 (deliveryTime ascending)
        assertTrue(output.indexOf("\"deliveryId\" : 1") < output.indexOf("\"deliveryId\" : 2"));
        // within d1: orders sorted by orderId desc — o3 before o1
        assertTrue(output.indexOf("o3") < output.indexOf("o1"));
        assertTrue(output.contains("\"deliveryStatus\" : \"delivered\""));
        assertTrue(output.contains("\"totalAmount\" : 6477"));
    }

    /** 'created' orders count toward maxOrders but are excluded from output. */
    @Test
    void createdOrdersCountButAreFiltered() {
        String input = """
                {"orderId":"o1","orderStatus":"created","delivery":{"deliveryId":1,"deliveryTime":"2022-01-01T00:00:00Z"},"amount":100}
                {"orderId":"o2","orderStatus":"delivered","delivery":{"deliveryId":1,"deliveryTime":"2022-01-01T00:00:00Z"},"amount":200}
                """;

        // maxOrders=1 — o1 (created) fills the limit, o2 is never read
        String output = run(input, 1, Duration.ofSeconds(5));
        assertEquals("[ ]", output.replaceAll("\\s+", " ").trim());
    }

    /** Blank keep-alive lines are skipped and don't count toward maxOrders. */
    @Test
    void keepAliveBlankLinesSkipped() {
        String input = "\n\n" +
                "{\"orderId\":\"o1\",\"orderStatus\":\"delivered\",\"delivery\":{\"deliveryId\":1,\"deliveryTime\":\"2022-01-01T00:00:00Z\"},\"amount\":500}\n";

        String output = run(input, 10, Duration.ofSeconds(5));
        assertTrue(output.contains("\"order_id\" : \"o1\""));
    }

    /** All-cancelled delivery → status=cancelled, totalAmount absent. */
    @Test
    void allCancelledDelivery() {
        String input = """
                {"orderId":"o1","orderStatus":"cancelled","delivery":{"deliveryId":1,"deliveryTime":"2022-01-01T00:00:00Z"},"amount":null}
                """;

        String output = run(input, 10, Duration.ofSeconds(5));
        assertTrue(output.contains("\"deliveryStatus\" : \"cancelled\""));
        assertFalse(output.contains("totalAmount"));
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private String run(String input, int maxOrders, Duration maxTime) {
        OrderStreamProcessor processor = factory.createProcessor(maxOrders, maxTime);
        var in  = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        var out = new ByteArrayOutputStream();
        processor.process(in, out);
        return out.toString(StandardCharsets.UTF_8);
    }
}