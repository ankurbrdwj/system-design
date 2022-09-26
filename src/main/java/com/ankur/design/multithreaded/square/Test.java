package com.ankur.design.multithreaded.square;

public class Test {
    public static void main(String[] args) {
      foo(null);
      long longWithL = 1000*60*60*24*365L;
      long longWithoutL = 1000*60*60*24*365;
      System.out.println(longWithL);
      System.out.println(longWithoutL);
    }
    public static void foo(Object o) {
      System.out.println("Object argument");
    }
    public static void foo(String s) {
      System.out.println("String argument");
    }

  }

