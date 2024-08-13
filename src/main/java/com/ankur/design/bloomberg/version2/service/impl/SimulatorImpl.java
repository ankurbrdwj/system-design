package com.ankur.design.bloomberg.version2.service.impl;

import com.ankur.design.bloomberg.version2.service.EconomicData;
import com.ankur.design.bloomberg.version2.service.GLOOM;
import com.ankur.design.bloomberg.version2.service.IndustryModel;
import com.ankur.design.bloomberg.version2.service.Miner;
import com.ankur.design.bloomberg.version2.service.Simulator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;

public class SimulatorImpl implements Simulator {

    @Autowired
    private IndustryModel industryModel;

    @Autowired
    private Miner miner;
  private Map<ModelDataKey, Double> cache = new ConcurrentHashMap<>(); // Cache for grades

  @Override
  public double evaluateModel(IndustryModel model) {
    EconomicData data = miner.fetchCurrentData();
    ModelDataKey key = new ModelDataKey(model, data); // Key for caching
    return evaluateModelInternal(model, data, key);
  }

  // Method for predictive mode with GLOOM object
  public double evaluateModelPredictive(IndustryModel model, GLOOM gloom) {
    EconomicData currentData = miner.fetchCurrentData();
    EconomicData predictedData = gloom.getPredictedData();
    EconomicData combinedData = mergeData(currentData, predictedData);
    ModelDataKey key = new ModelDataKey(model, combinedData); // Key for caching with combined data
    return evaluateModelInternal(model, combinedData, key);
  }

  private double evaluateModelInternal(IndustryModel model, EconomicData data, ModelDataKey key) {
    double grade = cache.get(key);
    if (grade == 0.0) { // Check if not already cached
      grade = model.evaluate(data);
      cache.put(key, grade);
    }
    return grade;
  }

  // Helper method to merge current and predicted data (implementation omitted)
  private EconomicData mergeData(EconomicData currentData, EconomicData predictedData) {
    // ...
    return null;
  }

  // ModelDataKey class to uniquely identify model-data combinations for caching (implementation omitted)
  private static class ModelDataKey {
    public ModelDataKey(IndustryModel model, EconomicData combinedData) {

    }
    // ...
  }
}
