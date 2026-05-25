package com.ankur.design.hft.tuning;

import java.util.Arrays;
import java.util.List;

/**
 * TOPIC: Feature Envy / Coupling-Cohesion
 *
 * Feature Envy is a code smell where one class accesses the internals of another
 * class more than its own. The method "envies" the other class and should probably
 * live there instead.
 *
 * In performance terms, Feature Envy forces data to travel across object boundaries.
 * From a LOCALITY perspective: the data (fields) and the behaviour (method) that
 * uses that data should live in the same class. This improves:
 *   - Code cohesion: the class is responsible for its own representation
 *   - Encapsulation: callers don't need to know internal structure
 *   - Testability: formatting logic is tested with Order, not a separate class
 *   - Flexibility: change Order's internals without touching OrderPrinter
 *
 * Also: deep call chains like a.getB().getC().getD() violate the Law of Demeter.
 * Each dot is a dependency on the internal structure of an intermediate object.
 */
public class FeatureEnvyDemo {

    // -------------------------------------------------------------------------
    // Supporting data classes
    // -------------------------------------------------------------------------

    static class Address {
        private final String street;
        private final String city;
        private final String country;

        Address(String street, String city, String country) {
            this.street = street;
            this.city = city;
            this.country = country;
        }

        public String getStreet()  { return street; }
        public String getCity()    { return city; }
        public String getCountry() { return country; }
    }

    static class Customer {
        private final String name;
        private final Address address;

        Customer(String name, Address address) {
            this.name = name;
            this.address = address;
        }

        public String getName()       { return name; }
        public Address getAddress()   { return address; }
    }

    static class Item {
        private final String name;
        private final double price;
        private final int quantity;

        Item(String name, double price, int quantity) {
            this.name = name;
            this.price = price;
            this.quantity = quantity;
        }

        public String getName()    { return name; }
        public double getPrice()   { return price; }
        public int getQuantity()   { return quantity; }
        public double lineTotal()  { return price * quantity; }
    }

    // -------------------------------------------------------------------------
    // BAD: Order without format() — all data is exposed via getters
    // -------------------------------------------------------------------------

    // BAD ORDER: just a data bag — no behaviour, exposes all internals
    static class BadOrder {
        private final String orderId;
        private final Customer customer;
        private final List<Item> items;

        BadOrder(String orderId, Customer customer, List<Item> items) {
            this.orderId = orderId;
            this.customer = customer;
            this.items = items;
        }

        // BAD: exposes internal Customer object — caller can now reach into it
        public Customer getCustomer() { return customer; }

        // BAD: exposes internal Item list — caller can mutate or inspect internals
        public List<Item> getItems()  { return items; }

        public String getOrderId()    { return orderId; }

        public double getTotal() {
            return items.stream().mapToDouble(Item::lineTotal).sum();
        }
    }

    // BAD: OrderPrinter suffers from Feature Envy.
    // It accesses order.getCustomer().getAddress().getCity() — a chain of 3 hops.
    // This class "knows" the internal structure of Order, Customer, and Address.
    // If Address changes (e.g., city becomes cityName), this class ALSO breaks.
    static class BadOrderPrinter {

        // BAD: this entire method is feature envy — it should be inside Order
        public String format(BadOrder order) {
            // BAD: drilling into nested objects — violates Law of Demeter
            String customerName = order.getCustomer().getName();
            String city = order.getCustomer().getAddress().getCity();       // 2 hops
            String country = order.getCustomer().getAddress().getCountry(); // 2 hops

            // BAD: iterating over order's items — this behaviour belongs in Order
            StringBuilder sb = new StringBuilder();
            sb.append("=== Order ").append(order.getOrderId()).append(" ===\n");
            sb.append("Customer: ").append(customerName)
              .append(" (").append(city).append(", ").append(country).append(")\n");
            sb.append("Items:\n");
            for (Item item : order.getItems()) {
                sb.append(String.format("  %-15s x%d  @ $%.2f = $%.2f%n",
                        item.getName(), item.getQuantity(), item.getPrice(), item.lineTotal()));
            }
            sb.append(String.format("Total: $%.2f%n", order.getTotal()));
            return sb.toString();
        }
    }

    // -------------------------------------------------------------------------
    // GOOD: Order owns its own formatting behaviour
    // -------------------------------------------------------------------------

    // GOOD ORDER: data AND behaviour together — cohesive class
    static class GoodOrder {
        private final String orderId;
        private final Customer customer;
        private final List<Item> items;

        GoodOrder(String orderId, Customer customer, List<Item> items) {
            this.orderId = orderId;
            this.customer = customer;
            this.items = items;
        }

        // Internal helpers — no public exposure of Customer/Items needed
        private String customerName()    { return customer.getName(); }
        private String customerCity()    { return customer.getAddress().getCity(); }
        private String customerCountry() { return customer.getAddress().getCountry(); }

        public double getTotal() {
            return items.stream().mapToDouble(Item::lineTotal).sum();
        }

        // GOOD: format() lives inside Order — it owns the data AND the behaviour.
        // OrderPrinter doesn't need to know about Customer, Address, or Item internals.
        // If Order's internals change, only this method needs updating.
        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Order ").append(orderId).append(" ===\n");
            sb.append("Customer: ").append(customerName())
              .append(" (").append(customerCity()).append(", ")
              .append(customerCountry()).append(")\n");
            sb.append("Items:\n");
            for (Item item : items) {
                sb.append(String.format("  %-15s x%d  @ $%.2f = $%.2f%n",
                        item.getName(), item.getQuantity(), item.getPrice(), item.lineTotal()));
            }
            sb.append(String.format("Total: $%.2f%n", getTotal()));
            return sb.toString();
        }
    }

    // GOOD: OrderPrinter is now thin — it just delegates to Order.
    // It has zero knowledge of Customer, Address, or Item.
    // Adding a new field to Order doesn't require touching OrderPrinter at all.
    static class GoodOrderPrinter {

        // GOOD: no feature envy — just delegates to the object that owns the data
        public String format(GoodOrder order) {
            return order.format();  // Order knows how to describe itself
        }
    }

    // -------------------------------------------------------------------------
    // Demo
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        Address address = new Address("10 Downing St", "London", "UK");
        Customer customer = new Customer("Alice Smith", address);
        List<Item> items = Arrays.asList(
                new Item("AAPL options", 5.25, 100),
                new Item("TSLA futures", 12.00, 50),
                new Item("BTC spot",     45000.00, 2)
        );

        System.out.println("=== FeatureEnvyDemo ===");
        System.out.println();

        // BAD example
        System.out.println("--- BAD: Feature Envy (OrderPrinter drills into Order internals) ---");
        BadOrder badOrder = new BadOrder("ORD-001", customer, items);
        BadOrderPrinter badPrinter = new BadOrderPrinter();
        System.out.println(badPrinter.format(badOrder));

        System.out.println("Problems with BAD approach:");
        System.out.println("  - OrderPrinter knows about Customer, Address, Item internals");
        System.out.println("  - order.getCustomer().getAddress().getCity() = 2-hop chain (Law of Demeter violation)");
        System.out.println("  - Change Address field name? Must update OrderPrinter too.");
        System.out.println("  - Formatting logic is split from the data it formats.");
        System.out.println();

        // GOOD example
        System.out.println("--- GOOD: Behaviour co-located with data (Order.format()) ---");
        GoodOrder goodOrder = new GoodOrder("ORD-001", customer, items);
        GoodOrderPrinter goodPrinter = new GoodOrderPrinter();
        System.out.println(goodPrinter.format(goodOrder));

        System.out.println("Benefits of GOOD approach:");
        System.out.println("  - OrderPrinter.format() is a 1-line delegate — no Order internals exposed");
        System.out.println("  - Order encapsulates its own representation");
        System.out.println("  - Change Address internals? Only GoodOrder.format() needs updating.");
        System.out.println("  - Law of Demeter satisfied: talk only to your immediate friends.");
        System.out.println();
        System.out.println("Rule: if a method uses more data from class X than from its own class,");
        System.out.println("      it probably belongs in class X.");
    }
}