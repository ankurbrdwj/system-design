package com.ankur.design.lld.skyscanner.provider;

import java.time.Instant;
import java.util.List;

/**
 * Immutable response from any third-party provider.
 */
public record ProviderResponse(
        String providerId,
        ProviderType type,
        List<Quote> quotes,
        long latencyMs,
        Instant fetchedAt
) {
    public static ProviderResponse empty(String providerId, ProviderType type, long latencyMs) {
        return new ProviderResponse(providerId, type, List.of(), latencyMs, Instant.now());
    }
}