package com.ankur.design.lld.animal;

/**
 * Abstract class — use when:
 *  - subclasses share STATE (fields)
 *  - you want partial implementation
 *  - IS-A relationship holds (a Mammal IS-A Animal)
 */
public abstract class Animal {

    protected String name;

    public Animal(String name) {
        this.name = name;
    }

    // subclass MUST implement — every animal sounds different
    public abstract void makeSound();

    // shared behavior — all animals breathe
    public void breathe() {
        System.out.println(name + " is breathing");
    }

    public String getName() {
        return name;
    }
}