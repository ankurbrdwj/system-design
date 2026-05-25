package com.ankur.design.lld.tddkata.order;

import com.ankur.design.lld.picnic.model.Order;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Output: a single order within a DeliveryResult.
 * Only order_id and amount are included in output.
 */
public record OrderResult(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("amount")   Long amount
) {

    public static OrderResult from(Order order) {
        return new OrderResult(order.orderId(), order.amount());
    }
}