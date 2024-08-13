package com.ankur.design.bloomberg.version2;

import com.ankur.design.bloomberg.version2.model.MySpecificModel;
import com.ankur.design.bloomberg.version2.service.GLOOM;
import com.ankur.design.bloomberg.version2.service.IndustryModel;
import com.ankur.design.bloomberg.version2.service.PredictiveEngine;
import com.ankur.design.bloomberg.version2.service.Simulator;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

public class MainApp {
  // Spring configuration (omitted for brevity)
  public static void main() {

    IndustryModel myModel = new MySpecificModel(); // Replace with your implementation
    PredictiveEngine predictiveEngine = null;
    GLOOM gloom = predictiveEngine.generateGLOOM(); // Black box for this exercise

    AbstractBeanFactory applicationContext = null;
    Simulator simulator = applicationContext.getBean(Simulator.class);
    double grade = simulator.evaluateModel(myModel);

    System.out.println("Model grade: " + grade);

    // Predictive mode
    double predictiveGrade = simulator.evaluateModel(myModel);
    System.out.println("Predictive model grade: " + predictiveGrade);
  }
}
