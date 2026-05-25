package com.ankur.design.lld.picnic;

import com.ankur.design.lld.picnic.impl.PicnicOrderStreamProcessorFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class PicnicProcessorTest {

    private final OrderStreamProcessorFactory factory = new PicnicOrderStreamProcessorFactory();

    /**
     * Happy path: 3 orders across 2 deliveries.
     * - d1: one delivered (amount 6477) + one cancelled → status=delivered, total=6477
     * - d2: one delivered (amount 1000)                 → status=delivered, total=1000
     * Deliveries sorted by delivery_time ascending.
     */
    @Test
    void happyPath() {
        String input = """
                {"order_id":"order-3","order_status":"delivered","delivery":{"delivery_id":"d1","delivery_time":"2022-05-20T11:50:48Z"},"amount":6477}
                {"order_id":"order-1","order_status":"cancelled","delivery":{"delivery_id":"d1","delivery_time":"2022-05-20T11:50:48Z"},"amount":null}
                {"order_id":"order-2","order_status":"delivered","delivery":{"delivery_id":"d2","delivery_time":"2022-05-21T09:00:00Z"},"amount":1000}
                """;

        String output = run(input, 100, Duration.ofSeconds(5));

        assertTrue(output.contains("\"delivery_id\" : \"d1\""));
        assertTrue(output.contains("\"delivery_status\" : \"delivered\""));
        assertTrue(output.contains("\"total_amount\" : 6477"));
        // orders within d1 sorted by order_id desc: order-3 before order-1
        assertTrue(output.indexOf("order-3") < output.indexOf("order-1"));
        // d1 before d2 (delivery_time ascending)
        assertTrue(output.indexOf("d1") < output.indexOf("d2"));
    }

    /** 'created' orders count toward maxOrders but are excluded from output. */
    @Test
    void createdOrdersCountButAreFiltered() {
        String input = """
                {"order_id":"o1","order_status":"created","delivery":{"delivery_id":"d1","delivery_time":"2022-01-01T00:00:00Z"},"amount":100}
                {"order_id":"o2","order_status":"delivered","delivery":{"delivery_id":"d1","delivery_time":"2022-01-01T00:00:00Z"},"amount":200}
                """;

        // maxOrders=1 — "o1" (created) fills the limit, "o2" never read
        String output = run(input, 1, Duration.ofSeconds(5));
        assertEquals("[ ]", output.replaceAll("\\s+", " ").trim());
    }

    /** Keep-alive blank lines are skipped and don't count toward maxOrders. */
    @Test
    void keepAliveBlankLinesSkipped() {
        String input = "\n\n" +
                "{\"order_id\":\"o1\",\"order_status\":\"delivered\",\"delivery\":{\"delivery_id\":\"d1\",\"delivery_time\":\"2022-01-01T00:00:00Z\"},\"amount\":500}\n";

        String output = run(input, 10, Duration.ofSeconds(5));
        assertTrue(output.contains("\"order_id\" : \"o1\""));
    }

    /** All cancelled delivery → status=cancelled, total_amount=null. */
    @Test
    void allCancelledDelivery() {
        String input = """
                {"order_id":"o1","order_status":"cancelled","delivery":{"delivery_id":"d1","delivery_time":"2022-01-01T00:00:00Z"},"amount":null}
                """;

        String output = run(input, 10, Duration.ofSeconds(5));
        assertTrue(output.contains("\"delivery_status\" : \"cancelled\""));
        assertFalse(output.contains("total_amount"));
    }

    private String run(String input, int maxOrders, Duration maxTime) {
        OrderStreamProcessor processor = factory.createProcessor(maxOrders, maxTime);
        ByteArrayInputStream  in  = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        processor.process(in, out);
        return out.toString(StandardCharsets.UTF_8);
    }
}