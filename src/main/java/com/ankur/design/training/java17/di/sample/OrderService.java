package com.ankur.design.training.java17.di.sample;

import com.ankur.design.training.java17.di.annotations.Component;
import com.ankur.design.training.java17.di.annotations.Inject;

@Component
public class OrderService {

    @Inject
    private OrderRepository orderRepository;   // ← scanner will find this

    @Inject
    private NotificationService notificationService;  // ← and this

    private String internalState = "not injected";    // ← no @Inject, scanner ignores it

    public void process(String orderId) {
        String order = orderRepository.findById(orderId);
        notificationService.notify("processed: " + order);
    }
}