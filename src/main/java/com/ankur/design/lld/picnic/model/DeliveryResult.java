package com.ankur.design.lld.picnic.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Output: grouped delivery with sorted orders.
 *
 * Rules:
 *  - status = "delivered" iff at least one order is delivered; else "cancelled"
 *  - total_amount = sum of delivered order amounts; null if none
 *  - orders sorted by order_id descending
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryResult(
        @JsonProperty("delivery_id")     String deliveryId,
        @JsonProperty("delivery_time")   Instant deliveryTime,
        @JsonProperty("delivery_status") String deliveryStatus,
        @JsonProperty("orders")          List<OrderResult> orders,
        @JsonProperty("total_amount")    Long totalAmount
) {
    public static DeliveryResult from(String deliveryId, Instant deliveryTime, List<Order> orders) {
        boolean anyDelivered = orders.stream().anyMatch(Order::isDelivered);

        Long total = orders.stream()
                .filter(Order::isDelivered)
                .map(Order::amount)
                .filter(a -> a != null)
                .reduce(0L, Long::sum);

        List<OrderResult> sortedOrders = orders.stream()
                .sorted(Comparator.comparing(Order::orderId).reversed()) // order_id desc
                .map(OrderResult::from)
                .toList();

        return new DeliveryResult(
                deliveryId,
                deliveryTime,
                anyDelivered ? "delivered" : "cancelled",
                sortedOrders,
                anyDelivered ? (total == 0 ? null : total) : null
        );
    }
}