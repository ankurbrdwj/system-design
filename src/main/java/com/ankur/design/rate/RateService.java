package com.ankur.design.rate;

public interface RateService {

    /**
     * Provides the exchange rate for a given currency pair
     *
     * @param fromCurrency - source currency
     * @param toCurrency   - target currency
     * @return the exchange rate from source currency to target currency
     */
     public double getRate(String fromCurrency, String toCurrency);

}
