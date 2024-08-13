package com.ankur.design.training.java8.methodreferences;

public interface MultipleArguments {
    String a = "My Name";
    String b = "is";
    String c = "Anthony Gonzalez";

    String addMany(String a, String b, String c);
}

class Sample implements MultipleArguments {

    private static String addMany2(String a, String b, String c) {
        return a + b + c;
    }

    @Override
    public String addMany(String a, String b, String c) {
        return a + b + c;
    }

    public static void main(String[] args) {
        Combine combine = new Combine();
       String res = combine.combine(Sample::addMany2);
        System.out.println(res);
    }
}

class Combine {
    String combine(MultipleArguments ma) {
        return ma.addMany(ma.a, ma.b, ma.c);
    }
}
