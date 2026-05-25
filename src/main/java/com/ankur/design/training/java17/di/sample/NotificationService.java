package com.ankur.design.training.java17.di.sample;

import com.ankur.design.training.java17.di.annotations.Component;

@Component
public class NotificationService {
    public void notify(String message) {
        System.out.println("[Notification] " + message);
    }
}