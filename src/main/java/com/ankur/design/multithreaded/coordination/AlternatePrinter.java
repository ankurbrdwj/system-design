package com.ankur.design.multithreaded.coordination;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class AlternatePrinter {

    private final ReentrantLock lock = new ReentrantLock();
    Condition conditionA = lock.newCondition();
    Condition conditionB = lock.newCondition();
    private boolean isATurn = true;

    public void printA(){
            lock.lock();
            try{
                while(!isATurn){
                    conditionA.await();

                }
                System.out.println(" A ");
                isATurn=false;
                conditionB.signal();
            }
            catch (InterruptedException interruptedException){
                Thread.currentThread().interrupt();
            }finally {
                lock.unlock();
            }
    }

    public void printB(){
        lock.lock();
        try{
            while(isATurn){
                conditionB.await();
            }
            System.out.println(" B ");
            isATurn=true;
            conditionA.signal();
        }catch (InterruptedException interruptedException){
            Thread.currentThread().interrupt();
        }finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        AlternatePrinter ap= new AlternatePrinter();
        Thread t1 = new Thread(()-> {
            for (int i = 0; i < 10; i++) {
                ap.printA();
            }
        });
        Thread t2 = new Thread(()->{
            for (int i = 0; i < 10 ; i++) {
                ap.printB();
            }
        });
        t1.start();
        t2.start();
    }
}