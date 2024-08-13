package com.ankur.design.training.java8.concurrency;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class UsingBlockingQueue {
    public static void main(String[] args) {
        BlockingQueue<Item> queue = new ArrayBlockingQueue<>(10);

        final Runnable producer=() -> {
            while(true){
                try {
                    queue.put(createItem());
                    System.out.printf("Inside Producer added item now queue size %d \n", queue.size());
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        final Runnable consumer = ()->{
            while(true){
                try {
                    Item item = queue.take();
                    System.out.printf("Inside Consumer took item now queue size %d \n", queue.size());
                    processItem(item);
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        new Thread(producer).start();
        new Thread(consumer).start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private static void processItem(Item item) {
        System.out.printf("processing item %d name %s \n", item.getItemId(),item.getItemName());
    }

    private static Item createItem() {
        Random random =new Random(1);
        long id = random.nextLong();
        String name = "New Item_"+id;
        System.out.printf("Creating item %d name %s \n", id,name);
        return new Item(id,name);
    }
}
