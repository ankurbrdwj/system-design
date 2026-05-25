package com.ankur.design.lld.tddkata.order;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
record DeliveryGroup (
    Integer deliveryId,
    Instant deliveryTime,
    String deliveryStatus,
    List<OrderResult> orders,
    Long totalAmount ){}
