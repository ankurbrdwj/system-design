package com.ankur.design.training.java8.methodreferences;

public class RunRef {
    public static void main(String[] args) {
        Runnable r1 = RunRef::run;
        Thread t1 = new Thread(r1);
        t1.start();
        t1.run();
    }

    private static void run() {
        System.out.println("inside runnable r1");
    }
}
