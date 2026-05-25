package com.ankur.design.lld.skyscanner.provider;

import java.time.LocalDate;

/**
 * Immutable search request passed to all providers.
 */
public record SearchRequest(
        String origin,
        String destination,
        LocalDate departureDate,
        int passengers
) {}