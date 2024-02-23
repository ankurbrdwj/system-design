package com.ankur.design.multithreaded.square;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static java.time.ZoneOffset.UTC;

public class Test {
    public static void main(String[] args) throws JsonProcessingException {
      foo(null);
      long longWithL = 1000*60*60*24*365L;
      long longWithoutL = 1000*60*60*24*365;
      System.out.println(longWithL);
      System.out.println(longWithoutL);
      System.out.println(Instant.now());
      System.out.println(Instant.now().atZone(UTC));
      System.out.println(ZonedDateTime.now());
      System.out.println(LocalDateTime.now());

      Instant ins = Instant.parse("2017-09-01T10:23:47.000Z");
      System.out.println(ins);
      ObjectMapper mapper = new ObjectMapper();
      CarRequest request;
        request = new CarRequest();
        request.setBrand("Flexa");
        request.setManufacturer("");
        request.setLicensePlate("L-CS8877E");
        request.setOperationsCity("Berlin");
        request.setStatus("available");
      System.out.println(      mapper.writeValueAsString(request)
);
    }
    public static void foo(Object o) {
      System.out.println("Object argument");
    }
    public static void foo(String s) {
      System.out.println("String argument");
    }

  }
 class CarRequest {
   private String brand;
   private String licensePlate;
   private String manufacturer;
   private String operationsCity;
   private String status;

   public CarRequest() {
   }

   public String getBrand() {
     return brand;
   }

   public void setBrand(String brand) {
     this.brand = brand;
   }

   public String getLicensePlate() {
     return licensePlate;
   }

   public void setLicensePlate(String licensePlate) {
     this.licensePlate = licensePlate;
   }

   public String getManufacturer() {
     return manufacturer;
   }

   public void setManufacturer(String manufacturer) {
     this.manufacturer = manufacturer;
   }

   public String getOperationsCity() {
     return operationsCity;
   }

   public void setOperationsCity(String operationsCity) {
     this.operationsCity = operationsCity;
   }

   public String getStatus() {
     return status;
   }

   public void setStatus(String status) {
     this.status = status;
   }
 }

