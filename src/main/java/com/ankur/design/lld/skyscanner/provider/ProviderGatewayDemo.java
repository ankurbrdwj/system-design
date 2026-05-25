package com.ankur.design.lld.skyscanner.provider;

import com.ankur.design.lld.skyscanner.provider.adapters.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Demo: fan out to 3 providers in parallel, collect results within 3s timeout.
 *
 * Run via: mainClass = "com.ankur.design.lld.skyscanner.provider.ProviderGatewayDemo"
 */
public class ProviderGatewayDemo {

    public static void main(String[] args) throws InterruptedException {
        ProviderGateway gateway = new ProviderGateway(List.of(
                new RyanairAdapter(),
                new MarriottAdapter(),
                new EnterpriseCarAdapter()
        ));

        SearchRequest request = new SearchRequest("LON", "NYC", LocalDate.now().plusDays(30), 1);

        System.out.println("=== Search 1: normal conditions ===");
        runSearch(gateway, request);

        System.out.println("\n=== Search 2-7: hammering Enterprise to trip circuit breaker ===");
        for (int i = 0; i < 6; i++) {
            runSearch(gateway, request);
            Thread.sleep(200);
        }

        System.out.println("\n=== Search 8: Enterprise circuit should be OPEN (fail-fast) ===");
        runSearch(gateway, request);

        gateway.shutdown();
    }

    private static void runSearch(ProviderGateway gateway, SearchRequest request) {
        long start = System.currentTimeMillis();
        List<ProviderResponse> results = gateway.search(request);
        long total = System.currentTimeMillis() - start;

        System.out.printf("  Total time: %dms | Providers responded: %d%n", total, results.size());
        results.forEach(r -> System.out.printf("    %-12s | %dms | %d quotes%n",
                r.providerId(), r.latencyMs(), r.quotes().size()));
    }
}