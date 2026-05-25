package com.ankur.design.lld.animal;

/**
 * Interface — Predator is a ROLE / capability, not a type.
 *
 * Why interface and NOT abstract class:
 *  - Lion (Mammal), Shark (Fish), Eagle (Bird) are all predators
 *  - They live in different inheritance trees
 *  - Java has single-class inheritance — making Predator a class would break this
 *  - "Being a predator" is a CAN-DO, not an IS-A
 *
 * Default methods:
 *  - Provide optional shared behaviour
 *  - Implementors can override if their stalking is different (e.g. Shark)
 *  - Interfaces still cannot have instance fields — that's the key difference from abstract class
 */
public interface Predator {

    // contract — every predator must implement this
    void hunt();

    // default method — most predators stalk; override if behaviour differs
    default void stalk() {
        System.out.println("Stalking prey silently...");
    }
}