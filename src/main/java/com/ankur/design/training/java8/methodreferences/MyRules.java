package com.ankur.design.training.java8.methodreferences;


@FunctionalInterface
public interface MyRules {
    public static void printStatic() {
        System.out.println("Inside interface static method");
    }

    default void printDefault() {
        System.out.println("Inside Interface Default method");
    }

    public boolean applyRule();
}
