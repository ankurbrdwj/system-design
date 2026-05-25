package com.ankur.design.lld.picnic.impl;

import com.ankur.design.lld.picnic.OrderStreamProcessor;
import com.ankur.design.lld.picnic.OrderStreamProcessorFactory;

import java.time.Duration;

public class PicnicOrderStreamProcessorFactory implements OrderStreamProcessorFactory {

    @Override
    public OrderStreamProcessor createProcessor(int maxOrders, Duration maxTime) {
        return new PicnicOrderStreamProcessor(maxOrders, maxTime);
    }
}