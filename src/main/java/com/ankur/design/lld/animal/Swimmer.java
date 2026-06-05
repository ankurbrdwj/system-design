package com.ankur.design.lld.animal;

// CAN-DO role — Shark, Platypus, Whale are from different hierarchies but all swim
public interface Swimmer {

    void swim();

    // most swimmers can dive; override if behaviour differs
    default void dive() {
        System.out.println("Diving underwater...");
    }
}