package com.ankur.design.lld.skyscanner.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * High-performance provider gateway.
 *
 * Key design decisions:
 *  1. Async fan-out  — all providers called in parallel via CompletableFuture
 *  2. Per-provider timeout — slow providers don't block the response
 *  3. Circuit breaker — fail-fast for providers that are consistently down
 *  4. Best-effort aggregation — partial results returned if some providers fail
 *
 * Thread model: virtual threads (Java 21) — one per provider call, cheap to create,
 * no thread pool sizing needed for I/O-bound HTTP calls.
 */
public class ProviderGateway {

    private static final long PROVIDER_TIMEOUT_MS = 3_000;

    private final List<ThirdPartyProvider> providers;
    private final Map<String, CircuitBreaker> circuitBreakers;
    private final ExecutorService executor;

    public ProviderGateway(List<ThirdPartyProvider> providers) {
        this.providers = providers;
        // one circuit breaker per provider: open after 5 failures, reset after 60s
        this.circuitBreakers = providers.stream().collect(Collectors.toMap(
                ThirdPartyProvider::id,
                p -> new CircuitBreaker(p.id(), 5, 60_000)
        ));
        // virtual threads — ideal for I/O-bound provider HTTP calls
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Fan out to all providers in parallel, collect results within timeout.
     * Never throws — always returns whatever data arrived in time.
     */
    public List<ProviderResponse> search(SearchRequest request) {
        List<CompletableFuture<ProviderResponse>> futures = providers.stream()
                .map(p -> callProvider(p, request))
                .toList();

        // wait for ALL futures up to the global deadline, then collect
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(PROVIDER_TIMEOUT_MS + 500, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> null);  // global timeout — collect whatever is done

        List<ProviderResponse> results = new ArrayList<>();
        for (CompletableFuture<ProviderResponse> f : futures) {
            if (f.isDone() && !f.isCompletedExceptionally()) {
                try { results.add(f.get()); } catch (Exception ignored) {}
            }
        }
        return results;
    }

    private CompletableFuture<ProviderResponse> callProvider(ThirdPartyProvider provider,
                                                              SearchRequest request) {
        CircuitBreaker cb = circuitBreakers.get(provider.id());

        if (!cb.allowRequest()) {
            System.out.println("[GW] " + provider.id() + " circuit OPEN — skipping");
            return CompletableFuture.completedFuture(
                    ProviderResponse.empty(provider.id(), provider.type(), 0));
        }

        return CompletableFuture
                .supplyAsync(() -> {
                    long start = System.currentTimeMillis();
                    try {
                        ProviderResponse response = provider.fetch(request);
                        cb.recordSuccess();
                        return response;
                    } catch (Exception e) {
                        cb.recordFailure();
                        long elapsed = System.currentTimeMillis() - start;
                        System.err.println("[GW] " + provider.id() + " failed: " + e.getMessage());
                        return ProviderResponse.empty(provider.id(), provider.type(), elapsed);
                    }
                }, executor)
                .orTimeout(PROVIDER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    cb.recordFailure();
                    System.err.println("[GW] " + provider.id() + " timed out");
                    return ProviderResponse.empty(provider.id(), provider.type(), PROVIDER_TIMEOUT_MS);
                });
    }

    public void shutdown() {
        executor.shutdown();
    }
}