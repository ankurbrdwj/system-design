package com.ankur.design.training.java8.function;

import java.util.function.Function;

public class FunctionUtil {
    public static void main(String[] args) {
        Function<Integer,String> intToString = s-> String.valueOf(s);
        Function<String,String> qoute= s-> "'"+s+"'";
        Function<Integer,String> getQoutedString= qoute.compose(intToString);
        System.out.println(getQoutedString.apply(5));
    }
}
