package com.ankur.design.training.java8.concurrency.reentrant;

/*
/Print Odd even using two threads
 */
public class PrintOddEven {

    public static void main(String[] args) {
        Printer printer = new Printer();
        Thread t1 = new Thread(new Odd(printer), "Odd");
        Thread t2 = new Thread(new Even(printer), "Even");
        t1.start();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        t2.start();
    }
}

class Odd implements Runnable {
    private Printer printer;

    public Odd(Printer printer) {
        this.printer = printer;
    }

    @Override
    public void run() {
        try {
            printer.printOdd();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class Even implements Runnable {
    private Printer printer;

    public Even(Printer printer) {
        this.printer = printer;
    }

    @Override
    public void run() {
        try {
            printer.printEven();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
