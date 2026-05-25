package com.ankur.design.lld.tddkata.order;


import java.time.Duration;

public class OrderStreamProcessorFactoryImpl implements OrderStreamProcessorFactory {
    public OrderStreamProcessor createProcessor(int maxOrders, Duration maxTime) {
        return new OrderStreamPrcessorImpl(new OrderProcessingService(), maxOrders, maxTime);
    }
}