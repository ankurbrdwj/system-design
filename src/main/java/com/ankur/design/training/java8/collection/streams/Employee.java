package com.ankur.design.training.java8.collection.streams;

public class Employee {
    private String name;
    private Integer age;
    private Double salary;
    private Department department;
    public Employee(String name, Integer age, Double salary, Department department) {
        this.name = name;
        this.age = age;
        this.salary = salary;
        this.department = department;
    }

    public String toString(){
        return "Employee Name:"+this.name;
    }
}
