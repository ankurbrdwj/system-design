package com.ankur.design.training.java8.collection.streams;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public class Reading {
    int year;
    int month;
    int day;
    double value;

    public Reading(int year, int month, int day, double value) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.value = value;
    }

    public static void main(String[] args) {
        List<Reading> readings = Arrays.asList(new Reading(2019, 10, 1, 405.99),
                new Reading(2019, 10, 1, 405.99),
                new Reading(2019, 10, 1, 403.99),
                new Reading(2019, 10, 1, 404.99),
                new Reading(2019, 10, 1, 400.99),
                new Reading(2019, 10, 1, 399.99),
                new Reading(2019, 10, 1, 398.22)
        );
        OptionalDouble optionalDouble = readings.stream()
                .mapToDouble(r -> r.value)
                .filter(v -> v > 397 && v < 406.5)
                .average();
        System.out.println(optionalDouble);

    }
}
