package com.ankur.design.rate;

// No need to implement this interface!
interface RateApi {

    /**
     * Fetches the exchange rate by making a REST call to a 3rd party provider
     *
     * @param fromCurrency - source currency
     * @param toCurrency   - target currency
     * @return the exchange rate from source currency to target currency
     */
    public double fetchRateFromProvider(String fromCurrency, String toCurrency);

}
