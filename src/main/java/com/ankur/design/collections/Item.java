package com.ankur.design.collections;

import lombok.Data;

@Data
public class Item {
    private String productId;
    private int quantity;
    private double price;

    public Item(String p001, int i, double v) {
        this.productId=p001;
        this.quantity=i;
        this.price=v;
    }

    // getters + setters
}