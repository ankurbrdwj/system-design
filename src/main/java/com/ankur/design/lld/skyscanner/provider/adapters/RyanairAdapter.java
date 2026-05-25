package com.ankur.design.lld.skyscanner.provider.adapters;

import com.ankur.design.lld.skyscanner.provider.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Airline provider adapter — simulates a real HTTP call to Ryanair's API.
 * In production: use WebClient (non-blocking) or HttpClient (Java 11+).
 * Maps Ryanair's response schema → canonical Quote.
 */
public class RyanairAdapter implements ThirdPartyProvider {

    @Override public String id()         { return "RYANAIR"; }
    @Override public ProviderType type() { return ProviderType.AIRLINE; }

    @Override
    public ProviderResponse fetch(SearchRequest request) throws Exception {
        long start = System.currentTimeMillis();

        // simulate HTTP call latency
        Thread.sleep(200 + (long)(Math.random() * 300));

        // simulate occasional failures (10% chance)
        if (Math.random() < 0.1) throw new RuntimeException("Ryanair API 503");

        List<Quote> quotes = List.of(
                new Quote(id(), type(), request.origin(), request.destination(),
                        request.departureDate(), BigDecimal.valueOf(49.99), "GBP"),
                new Quote(id(), type(), request.origin(), request.destination(),
                        request.departureDate(), BigDecimal.valueOf(59.99), "GBP")
        );

        return new ProviderResponse(id(), type(), quotes,
                System.currentTimeMillis() - start, java.time.Instant.now());
    }
}