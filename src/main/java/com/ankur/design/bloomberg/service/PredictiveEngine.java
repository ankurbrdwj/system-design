package com.ankur.design.bloomberg.service;

import com.ankur.design.bloomberg.model.GLOOM;
import com.ankur.design.bloomberg.model.Model;
import com.ankur.design.bloomberg.model.StatisticalData;
import java.util.List;

public interface PredictiveEngine {
  List<GLOOM> getGLOOMObjects(List<Model> model, StatisticalData statisticalData);
}
