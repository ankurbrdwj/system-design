package com.ankur.design.lld.picnic;

import java.time.Duration;

public interface OrderStreamProcessorFactory {
    /**
     * @param maxOrders stop after reading this many orders (regardless of status)
     * @param maxTime   stop after this duration has elapsed
     */
    OrderStreamProcessor createProcessor(int maxOrders, Duration maxTime);
}