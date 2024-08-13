package com.ankur.design.training.java8.collection.model;

import java.util.Set;

public class Order {

  private Customer customer;
  private Set<Product> orderLine;

  public Order(Customer customer, Set<Product> orderLine) {
    this.customer = customer;
    this.orderLine = orderLine;
  }

  public Customer getCustomer() {
    return customer;
  }

  public void setCustomer(Customer customer) {
    this.customer = customer;
  }

  public Set<Product> getOrderLine() {
    return orderLine;
  }

  public void setOrderLine(Set<Product> orderLine) {
    this.orderLine = orderLine;
  }
}
