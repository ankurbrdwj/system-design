package com.ankur.design.lld.tddkata.order;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OrderProcessingService {
    List<Order> filter(List<Order> orders) {
        return orders.stream()
                .filter(o -> o.orderStatus().equals("delivered")
                        || o.orderStatus().equals("cancelled"))
                .toList();

    }

    List<DeliveryGroup> processOrders(List<Order> orders){
        return group(filter(orders))
                .entrySet().stream()
                .map(e -> aggregate(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(DeliveryGroup::deliveryTime)
                        .thenComparing(DeliveryGroup::deliveryId))
                .toList();
    }

    public Map<Integer, List<Order>> group(List<Order> orders) {
        return orders.stream()
                .collect(Collectors.groupingBy(o -> o.delivery().deliveryId()));

    }
    DeliveryGroup aggregate(Integer deliveryId, List<Order> orders){
        boolean isDelivered = orders.stream()
                .anyMatch(o -> o.orderStatus().equals("delivered"));

        Long totalAmount = orders.stream()
                .filter(o -> o.orderStatus().equals("delivered"))
                .mapToLong(o -> o.amount() != null ? o.amount() : 0L)
                .sum();
        List<OrderResult> sortedOrders = orders.stream()
                .sorted(Comparator.comparing(Order::orderId).reversed())
                .map(o -> new OrderResult(o.orderId(), o.amount() != null ? o.amount().longValue() : null))
                .toList();
        return new DeliveryGroup(
                deliveryId,
                orders.get(0).delivery().deliveryTime(), // all orders share the same deliveryTime
                isDelivered ? "delivered" : "cancelled",
                sortedOrders,
                isDelivered ? totalAmount : null
        );

    }
}
