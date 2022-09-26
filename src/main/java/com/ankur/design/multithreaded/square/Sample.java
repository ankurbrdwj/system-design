package com.ankur.design.multithreaded.square;

import java.util.concurrent.CountDownLatch;

public class Sample {


  public static void main(String[] args) {
    final CountDownLatch doneSignal = new CountDownLatch(1);
    int[] arr = new int[]{7,3,5,1,8,3,9,2,6,4};
    for (Integer item: arr) {
      Thread first = new Thread(new Runnable() {
        @Override
        public void run() {
          System.out.println("doneSignal countDown : " + doneSignal.getCount());
          doneSignal.countDown();
          try {
            doneSignal.await();
            Thread.sleep(item* 1000);
            System.out.print(item + " ");
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      });
      first.start();
    }

  }
}
