package com.ankur.design.rate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TwentyFourHourVolumeImpl  {


  final Map<Long, Double> spent = new ConcurrentHashMap<>();

  //   4 +    6 +  4 + 4 +  8
  // 00:00 13:00 14:00 19:00 00:00
  // Called when some spend has occurred (always in EUR)
  public void onSpendCompleted(double spendAmount) {
    Long key = Instant.now().truncatedTo(ChronoUnit.MINUTES).getEpochSecond();
    spent.merge(key, spendAmount, Double::sum);
  }


  // Called by the homepage via API to show the marketing number
  public double get24HourVolume() {
    Long endKey = Instant.now().truncatedTo(ChronoUnit.MINUTES).getEpochSecond();
    Long startKey = endKey - (60 * 24);

    spent.entrySet().stream().filter(entry -> entry.getKey() >= startKey)
        .mapToDouble(Map.Entry::getValue).sum();

    return spent.entrySet().stream().filter(entry -> entry.getKey() >= startKey)
        .mapToDouble(Map.Entry::getValue).sum();
  }
}
