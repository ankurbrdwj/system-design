package com.ankur.design.collections;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
@Data
public class Order {
    private String orderId;
    private Customer customer;
    private List<Item> items;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate orderDate;
    private boolean isShipped;
    private List<String> tags;
}
