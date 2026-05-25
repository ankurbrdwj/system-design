package com.ankur.design.lld.tddkata.order;


import java.time.Duration;

public interface OrderStreamProcessorFactory {
    OrderStreamProcessor createProcessor(int maxOrders, Duration maxTime);

}
