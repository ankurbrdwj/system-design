package com.ankur.design.training.java8.methodreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class MethodReferences {

    public static void main(String[] args) {
        Predicate<A> methodREfA = new Predicate<A>() {
            @Override
            public boolean test(A a) {
                return a.applyRule();
            }
        };
        Predicate<B> methodREfB = B::applyRule;
        Predicate<A> secondRefA=A::isEmpty;
        Supplier<ArrayList> arrayLstRefB = ArrayList::new;
        Supplier<A> secondRefB=B::new;
        Consumer<A> consumerA = A::applyRule;
        System.out.println(methodREfB);
        System.out.println(secondRefA);
        System.out.println(arrayLstRefB);
        System.out.println(secondRefB);
        System.out.println(consumerA);

        List<String> list = new ArrayList<>();
        list.add("Magician");
         list.add("Assistant");
         System.out.println(list); // [Magician, Assistant]
         list.removeIf(s -> s.startsWith("A"));
         System.out.println(list);
    }
}

class A implements MyRules {
    public boolean isEmpty() {
        System.out.println("will return true");
        return true;
    }

    public String returnSubString() {
        return "half";
    }

    @Override
    public boolean applyRule() {
        System.out.println("will return false");
        return false;
    }
}

class B extends A{
    public B() {
        System.out.println("Default Constructor in class B");
    }

    @Override
    public boolean applyRule() {
        System.out.println("will return true");
        return true;
    }
}
