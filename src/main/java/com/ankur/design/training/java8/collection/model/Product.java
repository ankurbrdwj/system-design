package com.ankur.design.training.java8.collection.model;

public class Product {

  private int quantity;
  private String name;

  public Product(String name, int quantity) {
    this.quantity = quantity;
    this.name = name;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
