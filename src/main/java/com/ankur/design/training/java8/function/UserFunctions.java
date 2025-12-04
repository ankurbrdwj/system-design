package com.ankur.design.training.java8.function;

import java.util.function.Function;

public class UserFunctions {
    public static final Function<User, User> NORMALIZE = User::normalize;
    public static final Function<User, User> VALIDATE = User::validate;
    public static final Function<User, User> PERSIST  = User::persist;

    public UserFunctions() {
    }
}
