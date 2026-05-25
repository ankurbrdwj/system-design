package com.ankur.design.lld.animal;

/**
 * Lion IS-A Mammal (extends) — inherits warm-blooded state, regulateTemp()
 * Lion CAN-DO hunting (implements Predator) — role/capability
 *
 * This is why Predator MUST be an interface:
 *   Lion already extends Mammal — it cannot also extend a Predator class (single inheritance).
 */
public class Lion extends Mammal implements Predator {

    public Lion(String name) {
        super(name);
    }

    @Override
    public void makeSound() {
        System.out.println(name + ": Roar!");
    }

    @Override
    public void hunt() {
        System.out.println(name + " is hunting in a pride");
    }

    // stalk() inherited from Predator default — no override needed
}