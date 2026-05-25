package com.ankur.design.lld.skyscanner.provider.adapters;

import com.ankur.design.lld.skyscanner.provider.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Rental car provider adapter — simulates Enterprise's API.
 * Demonstrates a consistently slow/flaky provider to trigger circuit breaker.
 */
public class EnterpriseCarAdapter implements ThirdPartyProvider {

    @Override public String id()         { return "ENTERPRISE"; }
    @Override public ProviderType type() { return ProviderType.RENTAL_CAR; }

    @Override
    public ProviderResponse fetch(SearchRequest request) throws Exception {
        long start = System.currentTimeMillis();

        // simulate a flaky provider: 30% failure rate, high latency
        Thread.sleep(1000 + (long)(Math.random() * 2000));
        if (Math.random() < 0.3) throw new RuntimeException("Enterprise API timeout");

        List<Quote> quotes = List.of(
                new Quote(id(), type(), request.destination(), request.destination(),
                        request.departureDate(), BigDecimal.valueOf(45.00), "GBP")
        );

        return new ProviderResponse(id(), type(), quotes,
                System.currentTimeMillis() - start, java.time.Instant.now());
    }
}