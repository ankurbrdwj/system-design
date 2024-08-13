package com.ankur.design.training.java8.lambdas;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class StudyGuide {
    public static void main(String[] args) {
        FToC convertor = f -> (f - 32.0) * 5.9;
/*        Predicate<String> p = s -> System.out.println(s);
        Consumer<String> c = s -> System.out.println(s);
        Supplier<String> su = s -> System.out.println(s);
        Function<String> f = s -> System.out.println(s);*/
    List<String> list= new ArrayList<String>();

    Stream<String> stream= list.stream();

    }
}

@FunctionalInterface
interface FToC {
    double convert(double d);
}
