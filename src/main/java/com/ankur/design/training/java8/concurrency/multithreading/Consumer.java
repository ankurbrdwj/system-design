/**
 *
 */
package com.ankur.design.training.java8.concurrency.multithreading;

/**
 * @author ankurbrdwj
 *
 */
class Consumer extends Thread {
    private BankAccount account;

    public Consumer(BankAccount acct) {
        account = acct;
    }

    public void run() {
        for (int i = 0; i < 5; i++) {
            account.withdraw(10);
        }
    }

    @Override
    public String toString() {
        return "Consumer [account=" + account + "]";
    }

}
