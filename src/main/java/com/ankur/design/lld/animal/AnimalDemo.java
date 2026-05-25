package com.ankur.design.lld.animal;

/**
 * Demo — shows IS-A vs CAN-DO, abstract class vs interface, default method override.
 */
public class AnimalDemo {

    public static void main(String[] args) {

        Lion lion = new Lion("Simba");
        Shark shark = new Shark("Bruce");

        System.out.println("=== IS-A (inheritance) ===");
        lion.breathe();           // Animal.breathe()
        lion.regulateTemp();      // Mammal.regulateTemp()
        lion.makeSound();         // Lion.makeSound()

        System.out.println("\n=== CAN-DO (interface / role) ===");
        lion.stalk();             // Predator.stalk() — default method
        lion.hunt();              // Lion.hunt()

        System.out.println("\n=== Shark — different type hierarchy, same Predator role ===");
        shark.breathe();          // Animal.breathe()
        shark.makeSound();        // Shark.makeSound()
        shark.stalk();            // Shark.stalk() — overrides default
        shark.hunt();             // Shark.hunt()

        System.out.println("\n=== Polymorphism via Predator interface ===");
        Predator[] predators = { lion, shark };
        for (Predator p : predators) {
            p.stalk();
            p.hunt();
            System.out.println("---");
        }
    }
}