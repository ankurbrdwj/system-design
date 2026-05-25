package com.ankur.design.multithreaded.correctness;


public class Counter {
    private int value = 0;

    public void increment() {
        value++;
    }

    public long get() {
        return value;
    }
}