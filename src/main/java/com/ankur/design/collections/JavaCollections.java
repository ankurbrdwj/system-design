package com.ankur.design.collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class JavaCollections {

    public static void main(String[] args) {
        //Q: Get orderIds of shipped orders
        System.out.println("//Q: Get orderIds of shipped orders");
        System.out.println(sampleOrders().stream()
                .filter(Order::isShipped)
                .map(order -> order.getOrderId())
                .collect(Collectors.toList()));
        //Q: Group orders by customer name
        System.out.println("//Q: Group orders by customer name");
        Map<String, List<Order>> byCustomer = sampleOrders().stream()
                .collect(Collectors.groupingBy(o -> o.getCustomer().getName()));
        byCustomer.forEach((key, value) -> System.out.println("Key: " + key + " value: " + value));
        // Q: Total revenue across all orders
        System.out.println("//Q: Total revenue across all orders");
        double revenue = sampleOrders().stream()
                .flatMap(o -> o.getItems().stream())
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();
        System.out.println("Revenue:  " + revenue);
        System.out.println("//Q: Order with the highest number of items");
        Optional<Order> maxItems = sampleOrders().stream()
                .max(Comparator.comparing(o -> o.getItems().size()));
        maxItems.stream().forEach(order-> System.out.println(order));
        System.out.println("//Q: Count total items purchased");
        int totalItems = sampleOrders().stream()
                .flatMap(o -> o.getItems().stream())
                .map(Item::getQuantity)
                .reduce(0, Integer::sum);
        System.out.println("Q: List all unique product IDs");
        Set<String> productIds = sampleOrders().stream()
                .flatMap(o -> o.getItems().stream())
                .map(Item::getProductId)
                .collect(Collectors.toSet());
        System.out.println(productIds);
        System.out.println("Q: Split shipped and not shipped");
        Map<Boolean, List<Order>> shippedMap =
                sampleOrders().stream()
                        .collect(Collectors.partitioningBy(Order::isShipped));
        System.out.println("Q: Print comma-separated customer names");
        String customers = sampleOrders().stream()
                .map(o -> o.getCustomer().getName())
                .distinct()
                .collect(Collectors.joining(", "));
        System.out.println("Q: Map orderId → total price");
        Map<String, Double> costMap = sampleOrders().stream()
                .collect(Collectors.toMap(
                        Order::getOrderId,
                        o -> o.getItems().stream()
                                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                                .sum()
                ));
        System.out.println("10. Parallel Streams (be careful!)");
        double fastRevenue = sampleOrders().parallelStream()
                .flatMap(o -> o.getItems().stream())
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();
        System.out.println("Q: Sort orders by date");
        List<Order> sorted =
                sampleOrders().stream()
                        .sorted(Comparator.comparing(Order::getOrderDate))
                        .collect(Collectors.toList());
        System.out.println();
    }

    public static List<Order> sampleOrders() {
        return Arrays.asList(
                createOrder(
                        "O1001", "Bob Johnson",
                        "123 Maple Street", "Anytown", "CA", "90210",
                        Arrays.asList(
                                new Item("P001", 2, 999.99),
                                new Item("P003", 1, 19.99)
                        ),
                        LocalDate.of(2025, 5, 10),
                        false,
                        Arrays.asList("electronics", "urgent")
                ),
                createOrder(
                        "O1002", "Alice Brown",
                        "45 Oak Avenue", "Springfield", "IL", "62704",
                        Arrays.asList(
                                new Item("P002", 1, 49.99),
                                new Item("P004", 4, 5.49)
                        ),
                        LocalDate.of(2025, 6, 1),
                        true,
                        Arrays.asList("gift", "priority")
                ),
                createOrder(
                        "O1003", "Michael Green",
                        "78 Pine Road", "Denver", "CO", "80203",
                        Arrays.asList(
                                new Item("P005", 3, 15.99),
                                new Item("P006", 2, 129.49)
                        ),
                        LocalDate.of(2025, 6, 15),
                        false,
                        Arrays.asList("household", "newcustomer")
                ),
                createOrder(
                        "O1004", "Sarah Williams",
                        "250 Lake View", "Orlando", "FL", "32801",
                        Arrays.asList(
                                new Item("P007", 1, 499.00),
                                new Item("P008", 5, 2.99),
                                new Item("P009", 2, 10.50)
                        ),
                        LocalDate.of(2025, 7, 4),
                        true,
                        Arrays.asList("holiday-sale")
                ),
                createOrder(
                        "O1005", "David Smith",
                        "991 Elm Lane", "Dallas", "TX", "75201",
                        Arrays.asList(
                                new Item("P010", 10, 1.99)
                        ),
                        LocalDate.of(2025, 7, 30),
                        false,
                        Arrays.asList("bulk-order", "discount")
                )
        );
    }

    private static Order createOrder(
            String orderId,
            String customerName,
            String street,
            String city,
            String state,
            String zip,
            List<Item> items,
            LocalDate orderDate,
            boolean isShipped,
            List<String> tags
    ) {
        Address address = new Address();
        address.setStreet(street);
        address.setCity(city);
        address.setState(state);
        address.setZip(zip);

        Customer customer = new Customer();
        customer.setName(customerName);
        customer.setAddress(address);

        Order order = new Order();
        order.setOrderId(orderId);
        order.setCustomer(customer);
        order.setItems(items);
        order.setOrderDate(orderDate);
        order.setShipped(isShipped);
        order.setTags(tags);

        return order;
    }
}
