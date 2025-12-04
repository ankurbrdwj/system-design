package com.ankur.design.training.java8.function;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;
@Slf4j
public class FunctionUtil {
    public static void main(String[] args) {
        Function<Integer,String> intToString = s-> String.valueOf(s);
        Function<String,String> qoute= s-> "'"+s+"'";
        Function<Integer,String> getQoutedString= qoute.compose(intToString);
        log.info(getQoutedString.apply(5));

        // Step 1: A function that doubles a number
        Function<Integer, Integer> times2 = x -> x * 2;

        // Step 2: A function that converts the number to a string
        Function<Integer, String> toString = x -> "Result: " + x;

        // Step 3: Compose them using andThen
        Function<Integer, String> pipeline = times2.andThen(toString);

        // Step 4: Apply the composed function
        String result = pipeline.apply(5);

        log.info(result);


    }
}
