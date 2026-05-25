package com.ankur.design.training.java17.di.sample;

import com.ankur.design.training.java17.di.annotations.Component;

@Component
public class OrderRepository {
    public String findById(String id) {
        return "Order#" + id;
    }
}