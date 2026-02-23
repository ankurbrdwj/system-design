package com.ankur.design.rate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
class RateServiceImpl implements RateService {

    private static final long TTL_SECONDS = 60;
    private final RateApi rateApi;
    private final Map<String, CachedRate> ratesCache = new ConcurrentHashMap<>();

    @Override
    public double getRate(String fromCurrency, String toCurrency) {
        String cacheKey = fromCurrency + "_" + toCurrency;

        // Check if we have a valid (non-expired) cached entry
        CachedRate cached = ratesCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.rate;
        }

        // If expired or absent, fetch fresh data
        CachedRate updated = ratesCache.compute(cacheKey, (key, existing) -> {
            // Double-check expiration (another thread might have updated it)
            if (existing != null && !existing.isExpired()) {
                return existing;
            }
            // Fetch fresh data
            double rate = rateApi.fetchRateFromProvider(fromCurrency, toCurrency);
            return new CachedRate(rate, System.currentTimeMillis());
        });

        return updated.rate;
    }

    private static class CachedRate {
        private final double rate;
        private final long timestamp;

        CachedRate(double rate, long timestamp) {
            this.rate = rate;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > (TTL_SECONDS * 1000);
        }
    }

}
