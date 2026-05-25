package com.ankur.design.lld.picnic.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record Delivery(
        @JsonProperty("delivery_id")   String deliveryId,
        @JsonProperty("delivery_time") Instant deliveryTime
) {}