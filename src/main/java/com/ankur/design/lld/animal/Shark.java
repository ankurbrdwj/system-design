package com.ankur.design.lld.animal;

/**
 * Shark IS-A Animal (not a Mammal) but still implements Predator.
 *
 * This proves the design is correct:
 *   Predator as interface lets completely different class hierarchies share the role.
 *   If Predator were an abstract class, Shark couldn't extend both Animal and Predator.
 */
public class Shark extends Animal implements Predator {

    public Shark(String name) {
        super(name);
    }

    @Override
    public void makeSound() {
        System.out.println(name + ": ...(silence)");
    }

    @Override
    public void hunt() {
        System.out.println(name + " is ambushing from below");
    }

    // Shark stalks differently — overrides the default method
    @Override
    public void stalk() {
        System.out.println(name + " is circling slowly, unseen");
    }
}