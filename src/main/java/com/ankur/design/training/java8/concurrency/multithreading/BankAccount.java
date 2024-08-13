package com.ankur.design.training.java8.concurrency.multithreading;

public class BankAccount {
    private double balance;

    public BankAccount(double bal) {
        balance = bal;
    }

    public BankAccount() {
        this(0);
    }

    public double getBalance() {
        return balance;
    }

    public void deposit(double amt) {
        double temp = balance;
        temp = temp + amt;
        try {
            Thread.sleep(300); // simulate production time
        } catch (InterruptedException ie) {
            System.err.println(ie.getMessage());
        }
        System.out.println("after deposit balance = $" + temp);
        balance = temp;
    }

    public void withdraw(double amt) {
        if (balance < amt) {
            System.out.println("Insufficient funds!");
            return;
        }
        double temp = balance;
        temp = temp - amt;
        try {
            Thread.sleep(200); // simulate consumption time
        } catch (InterruptedException ie) {
            System.err.println(ie.getMessage());
        }
        System.out.println("after withdrawl balance = $" + temp);
        balance = temp;
    }
}
