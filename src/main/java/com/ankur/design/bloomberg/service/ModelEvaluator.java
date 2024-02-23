package com.ankur.design.bloomberg.service;

import com.ankur.design.bloomberg.model.Model;
import com.ankur.design.bloomberg.model.StatisticalData;
import java.math.BigDecimal;

public interface ModelEvaluator {
 BigDecimal modelEvaluation(Model model, StatisticalData statisticalData);
}
