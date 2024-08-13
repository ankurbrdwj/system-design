package com.ankur.design.training.java8.collection.streams;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Application {
    public static void main(String[] args) {
        int[] array = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int sum = Arrays.stream(array)
                .reduce(0, (n1, n2) -> n1 + n2);
        try (Stream<String> stream = Files.lines(Paths.get("names.txt"))) {
            List<String> list = stream
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        List<Duck> list = Arrays.asList(
                new Duck("Jerry", "yellow", 3),
                new Duck("George", "brown", 4),
                new Duck("Kramer", "mottled", 6),
                new Duck("Elaine", "white", 2),
                new Duck("Huey", "mottled", 2),
                new Duck("Louie", "white", 4),
                new Duck("Dewey", "brown", 4),
                new Duck("Terry", "orange", 6)
        );

        OptionalDouble average= list.stream()
                .mapToInt(d->d.getAge())
                .average();
        long mottledDucks = list.stream()
                .filter(d->d.getColor().equals("mottled"))
                .count();

        list.stream()
                .collect(Collectors.groupingBy(d->d.getColor()))
                .forEach((c,dl)->{
                    System.out.println("Ducks who are "+ c+": ");
                    dl.forEach(d-> System.out.println(d.getName()+" "));
                    System.out.println();
                });
    }
}
