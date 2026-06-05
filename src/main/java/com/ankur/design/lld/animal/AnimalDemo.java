package com.ankur.design.lld.animal;

public class AnimalDemo {

    public static void main(String[] args) {

        Lion lion = new Lion("Simba");
        Shark shark = new Shark("Bruce");
        DuckbilledPlatypus platypus = new DuckbilledPlatypus("Perry", true);

        System.out.println("=== IS-A (inheritance) ===");
        lion.breathe();           // Animal
        lion.regulateTemp();      // Mammal
        lion.nurseYoung();        // Mammal
        lion.makeSound();         // Lion

        System.out.println("\n=== CAN-DO (Predator interface) ===");
        lion.stalk();             // Predator default
        lion.hunt();              // Lion

        System.out.println("\n=== Shark — Predator + Swimmer ===");
        shark.breathe();
        shark.makeSound();
        shark.stalk();            // overrides Predator default
        shark.hunt();
        shark.swim();             // Swimmer
        shark.dive();             // Swimmer override

        System.out.println("\n=== Platypus — THE INTERVIEW TRAP ===");
        platypus.breathe();       // Animal — IS-A
        platypus.regulateTemp();  // Mammal — IS-A
        platypus.nurseYoung();    // Mammal — IS-A (nurses AFTER hatching)
        platypus.makeSound();
        platypus.layEgg();        // EggLayer — CAN-DO (unique among mammals)
        platypus.swim();          // Swimmer — CAN-DO
        platypus.dive();          // Swimmer — overridden (electroreception)
        platypus.deliverVenom();  // Venomous — CAN-DO (male only)

        System.out.println("\n=== Polymorphism — Swimmer role ===");
        Swimmer[] swimmers = { shark, platypus };
        for (Swimmer s : swimmers) {
            s.swim();
        }

        System.out.println("\n=== Polymorphism — EggLayer role ===");
        // only platypus here, but Bird/Reptile classes would fit in without touching Animal
        EggLayer[] egglayers = { platypus };
        for (EggLayer e : egglayers) {
            e.layEgg();
        }
    }
}