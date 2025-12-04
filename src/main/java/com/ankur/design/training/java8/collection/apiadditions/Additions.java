package com.ankur.design.training.java8.collection.apiadditions;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Additions {
    public static void main(String[] args) {
        Map<String, String> favorites = new HashMap<>();
        favorites.put("Jenny", "Bus Tour");
        favorites.put("Tom", null);
        favorites.putIfAbsent("Jenny", "Tram");
        favorites.putIfAbsent("Sam", "Tram");
        favorites.putIfAbsent("Tom", "Tram");
        System.out.println(favorites); // {Tom=Tram, Jenny=Bus Tour, Sam=Tram}

        BiFunction<String, String, String> mapper = (v1, v2)
                -> v1.length() > v2.length() ? v1: v2;
        favorites.put("Jenny", "Bus Tour");
        favorites.put("Tom", "Tram");
        String jenny = favorites.merge("Jenny", "Skyride", mapper);
        String tom = favorites.merge("Tom", "Skyride", mapper);
        System.out.println(favorites); // {Tom=Skyride, Jenny=Bus Tour}
        System.out.println(jenny); // Bus Tour
        System.out.println(tom); // Skyride

        BiFunction<String, String, String> mapper2 = (v1, v2) -> v1.length() >
                v2.length() ? v1 : v2;
        favorites.put("Sam", null);
        favorites.merge("Tom", "Skyride", mapper2);
        favorites.merge("Sam", "Skyride", mapper2);
        System.out.println(favorites); // {Tom=Skyride, Sam=Skyride}

        BiFunction<String, String, String> mapper3 = (v1, v2) -> null;
        favorites.put("Jenny", "Bus Tour");
        favorites.put("Tom", "Bus Tour");
        favorites.merge("Jenny", "Skyride", mapper3);
        favorites.merge("Sam", "Skyride", mapper3);
        System.out.println(favorites); // {Tom=Bus Tour, Sam=Skyride}
        Map<String, Integer> counts = new HashMap<>();
        counts.put("Jenny", 1);
        BiFunction<String, Integer, Integer> mapper4 = (k, v) -> v + 1;
        Integer jenny1 = counts.computeIfPresent("Jenny", mapper4);
        Integer sam = counts.computeIfPresent("Sam", mapper4);
        System.out.println(counts); // {Jenny=2}
        System.out.println(jenny1); // 2
        System.out.println(sam); // null
        Map<String, Integer> count2 = new HashMap<>();
        counts.put("Jenny", 15);
        counts.put("Tom", null);
        Function<String, Integer> mapper5 = (k) -> 1;
        Integer jenny2 = count2.computeIfAbsent("Jenny", mapper5); // 15
        Integer sam1 = count2.computeIfAbsent("Sam", mapper5); // 1
        Integer tom1 = count2.computeIfAbsent("Tom", mapper5); // 1e
        System.out.println(jenny2); // 2
        System.out.println(sam1); // null
        System.out.println(tom1); // 2
        System.out.println(count2); // {Tom=1, Jenny=15, Sam=1}
    }

    private static <T, U, R> List<R> listCombiner(
            List<T> list1, List<U> list2, BiFunction<T, U, R> combiner) {
        List<R> result = new ArrayList<>();
        for (int i = 0; i < list1.size(); i++) {
            result.add(combiner.apply(list1.get(i), list2.get(i)));
        }
        return result;
    }
}
