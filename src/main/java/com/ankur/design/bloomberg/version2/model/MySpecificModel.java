package com.ankur.design.bloomberg.version2.model;

import com.ankur.design.bloomberg.version2.service.EconomicData;
import com.ankur.design.bloomberg.version2.service.IndustryModel;

public class MySpecificModel implements IndustryModel {
  @Override
  public double evaluate(EconomicData data) {
    return 0;
  }
}
