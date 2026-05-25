package com.ankur.design.lld.picnic.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Input order — maps 1:1 to the JSON line from the InputStream.
 */
public record Order(
        @JsonProperty("order_id")     String orderId,
        @JsonProperty("order_status") String orderStatus,
        @JsonProperty("delivery")     Delivery delivery,
        @JsonProperty("amount")       Long amount          // cents; nullable
) {
    public boolean isDelivered()  { return "delivered".equals(orderStatus); }
    public boolean isCancelled()  { return "cancelled".equals(orderStatus); }
    public boolean isRelevant()   { return isDelivered() || isCancelled(); }
}