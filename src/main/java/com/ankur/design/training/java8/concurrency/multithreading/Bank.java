package com.ankur.design.training.java8.concurrency.multithreading;

public class Bank {
    public static void main(String[] args) {
        BankAccount account = new BankAccount(100);
        int slaveCount = 4;
        Thread[] slaves = new Thread[slaveCount];
        for (int i = 0; i < slaveCount; i++) {
            if (i % 2 == 0) {
                slaves[i] = new Producer(account);
            } else {
                slaves[i] = new Consumer(account);
            }
        }
        for (int i = 0; i < slaveCount; i++) {
            slaves[i].start();
        }
        for (int i = 0; i < slaveCount; i++) {
            try {
                slaves[i].join();
            } catch (InterruptedException ie) {
                System.err.println(ie.getMessage());
            } finally {
                System.out.println("slave " + i + " has died");
            }
        }
        System.out.print("Closing balance = ");
        System.out.println("$" + account.getBalance());
    }
}
