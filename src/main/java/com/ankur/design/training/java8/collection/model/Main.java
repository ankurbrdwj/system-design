package com.ankur.design.training.java8.collection.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
  public static void main(String[] args) {
    Product p1 = new Product("Sofa", 1);
    Product p2 = new Product("Table", 4);
    Product p3 = new Product("Chair", 5);
    Product p4 = new Product("lamps", 20);
    Product p5 = new Product("Sofa", 3);
    Product p6 = new Product("Table", 2);
    Product p7 = new Product("Chair", 10);
    Product p8 = new Product("lamps", 2);
    Product p9 = new Product("Sofa", 4);
    Product p10 = new Product("Table", 10);
    Product p11 = new Product("Chair", 15);
    Product p14 = new Product("lamps", 4);
    Product p13 = new Product("Sofa", 8);
    Product p12 = new Product("Table", 6);
    Product p15 = new Product("Chair", 10);
    Product p16 = new Product("lamps", 5);
    Customer customer1 = new Customer("Mark");
    Customer customer2 = new Customer("John");
    Customer customer3 = new Customer("Frank");
    Customer customer4 = new Customer("Lily");
    Set<Product> orderLine1 = new HashSet<>(Arrays.asList(p1, p2, p3, p4));
    Set<Product> orderLine2 = new HashSet<>(Arrays.asList(p5, p6, p7, p8));
    Set<Product> orderLine3 = new HashSet<>(Arrays.asList(p9, p10, p11, p12));
    Set<Product> orderLine4 = new HashSet<>(Arrays.asList(p13, p14, p15, p16));
    Order order1 = new Order(customer1, orderLine1);
    Order order2 = new Order(customer2, orderLine2);
    Order order3 = new Order(customer3, orderLine3);
    Order order4 = new Order(customer4, orderLine4);
    List<Order> orders = new ArrayList<>(Arrays.asList(order1, order2, order3, order4));
    // find top 3 most bought items
    // top 3 will be descending order of quantity
    Map<String, Integer> coutMap =
        orders.stream()
            .map(o -> o.getOrderLine())
            .flatMap(s -> s.stream())
            .collect(
                Collectors.groupingBy(
                    Product::getName, Collectors.summingInt(Product::getQuantity)));
    System.out.println(coutMap);
    Map<String, Integer> result1 = new LinkedHashMap<>();

    // Sort a map and add to result1
    coutMap.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .forEachOrdered(e -> result1.put(e.getKey(), e.getValue()));

    System.out.println(result1);
  }
}
