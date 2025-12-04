package com.ankur.design.training.java8.function;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UserPipelineAsync {

    private static final Function<User, User> PIPELINE =
/*            Function.<User>identity()
                    .andThen(UserFunctions.NORMALIZE)
                    .andThen(UserFunctions.VALIDATE)
                    .andThen(UserFunctions.PERSIST);*/
    UserFunctions.NORMALIZE
                    .andThen(UserFunctions.VALIDATE)
                    .andThen(UserFunctions.PERSIST);
    public static void main(String[] args) {
        List<User> users = Arrays.asList(
                new User("  Brian Goetz ", "  brian@openjdk.java ", false),
                new User("  Doug Lea ", "  doug@concurrency.net ", false),
                new User("  Vlad Mihalcea ", " VLAD@hibernate.org ", false)
        );

        List<CompletableFuture<Void>> futures = users.stream()
                .map(u -> CompletableFuture
                        .supplyAsync(() -> PIPELINE.apply(u))
                        .thenAccept(result -> System.out.println("✅ Done: " + result)))
                .collect(Collectors.toList());

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        System.out.println("All users processed!");
    }
}