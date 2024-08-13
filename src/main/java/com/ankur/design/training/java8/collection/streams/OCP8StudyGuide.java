package com.ankur.design.training.java8.collection.streams;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.*;
import java.util.stream.*;

public class OCP8StudyGuide {
    public static void main(String[] args) {
        System.out.println("Ch4 Q1");
        Stream<String> stream = Stream.iterate("", (s) -> s + "1");
        System.out.println(stream.limit(2).map(x -> x + "2"));
        System.out.println("Answer: D");
        System.out.println("Ch4 Q2");
        Predicate<? super String> predicate = s -> s.startsWith("g");
        Stream<String> stream1 = Stream.generate(() -> "growl! ");
        Stream<String> stream2 = Stream.generate(() -> "growl! ");
        //boolean b1 = stream1.anyMatch(predicate);
        //boolean b2 = stream2.allMatch(predicate);
        //System.out.println(b1 + " " + b2);
        System.out.println("Answer: F");
        System.out.println("Search a stream. The findFirst() and findAny() methods return a single element from\n" +
                "a stream in an Optional. The anyMatch(), allMatch(), and noneMatch() methods return a\n" +
                "boolean. Be careful, because these three can hang if called on an infinite stream with some\n" +
                "data. All of these methods are terminal operations.");
        System.out.println("Ch4 Q3");
        Predicate<? super String> predicate3 = s -> s.length() > 3;
        Stream<String> stream3 = Stream.iterate("-", (s) -> s + s);
        boolean b1 = stream3.noneMatch(predicate3);
        //boolean b2 = stream3.anyMatch(predicate3);
        //System.out.println(b1 + " " + b2);
        System.out.println("Answer: E");
        System.out.println("Exception in thread \"main\" java.lang.IllegalStateException: stream has already been operated upon or closed\n" +
                "\tat java.util.stream.AbstractPipeline.evaluate(AbstractPipeline.java:229)\n" +
                "\tat java.util.stream.ReferencePipeline.anyMatch(ReferencePipeline.java:449)");
        System.out.println("Ch4 Q4");
        System.out.println("Answer: A, B");
        System.out.println("Ch4 Q5");
        System.out.println("Answer: A,B");
        System.out.println("Ch4 Q6");
        Stream<String> s = Stream.generate(() -> "meow");
        boolean match = s.allMatch(String::isEmpty);
        System.out.println(match);
        System.out.println("Answer: A");
        System.out.println("Ch4 Q7");
        System.out.println("Answer: F");
        System.out.println("Ch4 Q8");
        IntStream is = IntStream.empty();
        is.average();
        //is.findAny();
        //is.sum();
        System.out.println("Answer: D, E");
        System.out.println("Ch4 Q9");
        LongStream ls = LongStream.of(1, 2, 3);
        OptionalLong opt = ls.map(n -> n * 10).filter(n -> n < 5).findFirst();
        //if (opt.isPresent()) System.out.println(opt.get()); // does not compile
        if (opt.isPresent()) System.out.println(opt.getAsLong());
        //opt.ifPresent(System.out.println)
        opt.ifPresent(System.out::println);
        System.out.println("Answer: B, D");
        System.out.println("Ch4 Q10");
        Stream.generate(() -> "1")
                .limit(10)
                .peek(System.out::println)
                .filter(x -> x.length() > 1)
                .forEach(System.out::println);
        System.out.println("Answer: F");
        System.out.println("Ch4 Q11");
        System.out.println(Stream.iterate(1, x -> ++x).limit(5).map(x -> "" + x).collect(Collectors.joining()));
        System.out.println("Answer: B,C,E");
        System.out.println("Ch4 Q12");
        System.out.println("Answer: A,F,G");
        System.out.println("Ch4 Q13");
        List<Integer> l1 = Arrays.asList(1, 2, 3);
        List<Integer> l2 = Arrays.asList(4, 5, 6);
        List<Integer> l3 = Arrays.asList();
       // Stream.of(l1, l2, l3).map(x -> x + 1).flatMap(x -> x.stream()).forEach(System.out::print);
        System.out.println("Answer: F");
        System.out.println("Ch4 Q14");
        Stream<Integer> str = Stream.of(1);
         IntStream istr = str.mapToInt(x -> x);
         //DoubleStream ds = str.mapToDouble(x -> x);
        // Stream<Integer> s2 = ds.mapToInt(x -> x);
         //s2.forEach(System.out::print);
        System.out.println("Answer: D");
        System.out.println("Ch4 Q15");
        System.out.println("Answer: D,E");
        System.out.println("Ch4 Q16");
        Stream<String> s16 = Stream.empty();
        Stream<String> s2 = Stream.empty();
        Map<Boolean, List<String>> p = s16.collect(
                Collectors.partitioningBy(b -> b.startsWith("c")));
        Map<Boolean, List<String>> g = s2.collect(
                Collectors.groupingBy(b -> b.startsWith("c")));
        System.out.println(p + " " + g);
        System.out.println("Answer: C");
        System.out.println("Ch4 Q17");
        UnaryOperator<Integer> u = x -> x * x;
        Function<Integer, Integer> f = x -> x*x;
        System.out.println("Answer: E");
        System.out.println("Ch4  Q18");
        DoubleStream sd = DoubleStream.of(1.2, 2.4);
        sd.peek(System.out::println).filter(x -> x > 2).count();
        System.out.println("Answer: F");
        System.out.println("Ch4  Q19");
        System.out.println("Answer: A,C,E");
        System.out.println("Ch4  Q20");
        List<Integer> l = IntStream.range(1, 6)
                .mapToObj(i -> i).collect(Collectors.toList());
        l.forEach(System.out::println);
        System.out.println("Answer: B");
    }

}
