package com.ankur.design.training.java8.collection.streams;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class Flattening {
    public static void main(String[] args) {
        List<List<String>> nestedList = asList(
                asList("one:one"),
                asList("two:one", "two:two", "two:three"),
                asList("three:one", "three:two", "three:three", "three:four"));

        List<String> result=nestedList.stream()
                .flatMap(l->l.stream())
                .collect(Collectors.toList());
        System.out.println(result.size());
    }
//[1,2,3,[6,7],1,2,3,[7,7]]
    public List<?> flatten(List<?> source) {
        List<Object> result = source.stream()
                .filter(o-> !(o instanceof List))
                .collect(Collectors.toList());
       List<Object> result1 = source.stream()
               .filter(o-> o instanceof List)
               .collect(Collectors.toList());
       result.addAll(result1);
        return result;
    }
}


