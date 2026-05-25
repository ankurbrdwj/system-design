package com.ankur.design.lld.animal;

/**
 * Abstract class — Mammal IS-A Animal.
 * Adds shared mammal state and behaviour.
 * Still abstract because you never instantiate a "Mammal" directly.
 */
public abstract class Mammal extends Animal {

    // shared state — every mammal has this
    private final boolean warmBlooded = true;

    public Mammal(String name) {
        super(name);
    }

    // shared mammal behaviour
    public void regulateTemp() {
        System.out.println(name + " is regulating body temperature (warm-blooded)");
    }

    public boolean isWarmBlooded() {
        return warmBlooded;
    }
}