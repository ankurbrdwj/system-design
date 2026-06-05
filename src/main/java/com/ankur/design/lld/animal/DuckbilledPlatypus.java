package com.ankur.design.lld.animal;

/**
 * THE OOP INTERVIEW TRAP:
 *
 * IS-A:  Platypus IS-A Mammal (warm-blooded, has fur, nurses young with milk)
 * CAN-DO: EggLayer  — lays eggs (monotreme, unlike 99% of mammals)
 *         Swimmer   — semi-aquatic, uses bill electroreception underwater
 *         Venomous  — males have a venom spur on their hind leg
 *
 * Why this design is correct:
 *  - If EggLayer were on Mammal, every mammal would be forced to lay eggs — wrong.
 *  - If Swimmer were on Mammal, Lion would need to implement swim() — wrong.
 *  - Interfaces model CAPABILITIES; abstract classes model SHARED STATE.
 */
public class DuckbilledPlatypus extends Mammal implements EggLayer, Swimmer, Venomous {

    private final boolean isMale;

    public DuckbilledPlatypus(String name, boolean isMale) {
        super(name);
        this.isMale = isMale;
    }

    @Override
    public void makeSound() {
        System.out.println(name + ": Growl...(soft low-pitched growl)");
    }

    @Override
    public void layEgg() {
        System.out.println(name + " lays a leathery egg in a burrow");
    }

    @Override
    public void swim() {
        System.out.println(name + " paddles with webbed feet, bill sensing electric fields");
    }

    @Override
    public void dive() {
        System.out.println(name + " closes eyes and ears underwater — navigates by electroreception alone");
    }

    @Override
    public void deliverVenom() {
        if (isMale) {
            System.out.println(name + " drives venomous hind-leg spur into attacker");
        } else {
            System.out.println(name + " has a spur but no active venom gland (female)");
        }
    }
}