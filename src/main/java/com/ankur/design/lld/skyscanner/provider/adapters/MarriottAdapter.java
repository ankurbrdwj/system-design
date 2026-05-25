package com.ankur.design.lld.skyscanner.provider.adapters;

import com.ankur.design.lld.skyscanner.provider.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Hotel provider adapter — simulates Marriott's API.
 * Intentionally slower than airlines (typical for hotel APIs).
 */
public class MarriottAdapter implements ThirdPartyProvider {

    @Override public String id()         { return "MARRIOTT"; }
    @Override public ProviderType type() { return ProviderType.HOTEL; }

    @Override
    public ProviderResponse fetch(SearchRequest request) throws Exception {
        long start = System.currentTimeMillis();

        Thread.sleep(500 + (long)(Math.random() * 800));  // hotels are slower

        List<Quote> quotes = List.of(
                new Quote(id(), type(), request.origin(), request.destination(),
                        request.departureDate(), BigDecimal.valueOf(120.00), "GBP"),
                new Quote(id(), type(), request.origin(), request.destination(),
                        request.departureDate(), BigDecimal.valueOf(180.00), "GBP")
        );

        return new ProviderResponse(id(), type(), quotes,
                System.currentTimeMillis() - start, java.time.Instant.now());
    }
}