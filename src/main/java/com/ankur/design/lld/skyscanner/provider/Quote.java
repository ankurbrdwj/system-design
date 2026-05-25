package com.ankur.design.lld.skyscanner.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single price quote from a provider.
 */
public record Quote(
        String providerId,
        ProviderType type,
        String origin,
        String destination,
        LocalDate departureDate,
        BigDecimal price,
        String currency
) {}