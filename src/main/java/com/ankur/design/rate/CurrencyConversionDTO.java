package com.ankur.design.rate;

import lombok.Value;

@Value
public class CurrencyConversionDTO {

  String fromCurrency;

  String toCurrency;

  double rate;

  long ttl;

}
