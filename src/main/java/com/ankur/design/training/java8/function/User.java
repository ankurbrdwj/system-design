package com.ankur.design.training.java8.function;

public class User {
    String name;
    String email ;
    boolean valid ;

    public User (String name, String email, boolean valid) {
    this.name=name;
    this.email=email;
    this.valid=valid;
    }

    public User normalize() {
        return new User(
                name.trim(),
                email.trim().toLowerCase(),
                valid
        );
    }

    public User validate() {
        boolean isValid = name.length() > 1 && email.contains("@");
        return new User(name, email, isValid);
    }

    public User persist() {
        if (valid) {
            System.out.println(Thread.currentThread().getName() + " → Saving user: " + email);
        } else {
            System.out.println(Thread.currentThread().getName() + " → Invalid user, not saved.");
        }
        return this;
    }
}
