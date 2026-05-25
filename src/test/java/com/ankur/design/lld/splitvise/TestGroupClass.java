package com.ankur.design.lld.splitvise;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGroupClass {
    private Group group;

    @BeforeEach
    void setUp() {
        group = new Group();
    }

    @Test
    void noExpenses_noDebts() {
        assertTrue(group.settle().isEmpty());
    }
    @Test
    void selfPayment_owesNothing() {
        group.addExpense("Alice", 100, List.of("Alice"));

        Map<String, List<Debt>> result = group.settle();

        assertTrue(result.get("Alice").isEmpty());
    }

    @Test
    void onePayerTwoBeneficiaries_eachOwesHalf() {
        group.addExpense("Alice", 100, List.of("Alice", "Bob"));

        assertTrue(group.settle().get("Alice").isEmpty());
        assertEquals(List.of(new Debt("Alice", 50.0)), group.settle().get("Bob"));
    }
    @Test
    void payerIsNotBeneficiary_allBeneficiariesOweFullShare() {
        group.addExpense("John", 100, List.of("Alice", "Bob"));

        assertEquals(List.of(new Debt("John", 50.0)), group.settle().get("Alice"));
        assertEquals(List.of(new Debt("John", 50.0)), group.settle().get("Bob"));
    }
    @Test
    void multipleExpenses_debtsAccumulate() {   // ← your actual scenario
        group.addExpense("Sarah", 400, List.of("Sarah", "John", "Alice", "Bob"));
        group.addExpense("John",  100, List.of("Alice", "Bob"));

        assertEquals(List.of(new Debt("Sarah", 100.0), new Debt("John", 50.0)), group.settle().get("Alice"));
    }
}
