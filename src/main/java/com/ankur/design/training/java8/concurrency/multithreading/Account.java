/**
 *
 */
package com.ankur.design.training.java8.concurrency.multithreading;

/**
 * @author ankurbrdwj
 *
 */
public class Account {
    private int balance = 50;
    public int getBalance() {
        return balance;
    }
    public void withdraw(int amount) {
        balance = balance - amount;
    }
}
