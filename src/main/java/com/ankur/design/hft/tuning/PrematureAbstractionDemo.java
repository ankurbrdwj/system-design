package com.ankur.design.hft.tuning;

/**
 * TOPIC: Premature Abstraction — abstracting before you have a justified reason.
 *
 * "Premature optimization is the root of all evil" — Knuth
 * But: "Premature abstraction is the root of all complexity" — common saying
 *
 * Abstraction has a COST:
 *   - Runtime: virtual dispatch (vtable lookup), interface overhead
 *   - Cognitive: readers must trace through multiple layers to understand simple logic
 *   - Compile time and binary size
 *   - Testability: more seams to mock, more moving parts
 *
 * Abstraction is JUSTIFIED when:
 *   - You have 2+ concrete implementations that need to be swapped
 *   - You need to test in isolation with a mock/stub
 *   - The abstraction boundary is stable even as implementations change
 *
 * Abstraction is NOT justified when:
 *   - You "might need it later" (YAGNI — You Ain't Gonna Need It)
 *   - There is only one implementation now and no concrete plan for more
 *   - The added complexity exceeds the benefit
 *
 * SECOND EXAMPLE: Deep inheritance for code reuse.
 * Inheritance is the WRONG tool for code reuse when behaviour varies independently.
 * Use COMPOSITION instead: small, focused behaviour objects composed into a class.
 */
public class PrematureAbstractionDemo {

    // =========================================================================
    // EXAMPLE 1: Over-engineered addition
    // =========================================================================

    // -------------------------------------------------------------------------
    // BAD: 4 classes, 1 interface, 1 factory — to add two numbers
    // -------------------------------------------------------------------------

    // BAD: An interface for something that will never vary
    interface Addable {
        int add(int a, int b);
    }

    // BAD: Abstract class adds no value here — there is only one implementation
    static abstract class AbstractAdder implements Addable {
        // Imagine this template method exists for "future flexibility"
        protected abstract int doAdd(int a, int b);

        @Override
        public int add(int a, int b) {
            return doAdd(a, b);  // BAD: delegates to subclass for no reason
        }
    }

    // BAD: The only concrete implementation — adding two ints
    static class ConcreteAdder extends AbstractAdder {
        @Override
        protected int doAdd(int a, int b) {
            return a + b;  // this is all it ever does
        }
    }

    // BAD: A factory to create the adder — more indirection for zero benefit
    static class AdderFactory {
        public static Addable createAdder() {
            return new ConcreteAdder();  // BAD: returns what could be written inline
        }
    }

    // BAD: Calling code must navigate interface + factory + abstract + concrete
    static int badAdd(int a, int b) {
        Addable adder = AdderFactory.createAdder();  // BAD: object creation for simple math
        return adder.add(a, b);
    }

    // -------------------------------------------------------------------------
    // GOOD: Just add the numbers
    // -------------------------------------------------------------------------

    // GOOD: No interface, no class, no factory. Just the operation.
    // If you later need polymorphism (different Adder strategies), THEN add the interface.
    static int goodAdd(int a, int b) {
        return a + b;  // GOOD: simple, readable, zero overhead, one line
    }

    // =========================================================================
    // EXAMPLE 2: Deep inheritance hierarchy for code reuse
    // =========================================================================

    // -------------------------------------------------------------------------
    // BAD: Deep class hierarchy — inheritance used for code reuse
    // -------------------------------------------------------------------------

    // BAD: A deep hierarchy just to share eat() and move() behaviour.
    // Problems:
    //   - Rigid: every subclass inherits ALL superclass methods, wanted or not
    //   - Fragile base class: change Animal.eat() → all subclasses affected
    //   - Cannot mix behaviours: a flying fish can't inherit from both Bird and Fish
    //   - Adding a new behaviour (swimming) requires inserting a new class into the hierarchy

    static class Animal {
        protected String name;
        Animal(String name) { this.name = name; }

        // BAD: every subclass gets this, even if it should eat differently
        public void eat() {
            System.out.println(name + " eats generic food");
        }
    }

    static class Mammal extends Animal {
        Mammal(String name) { super(name); }

        // BAD: must override to change behaviour — tight coupling to parent
        @Override
        public void eat() {
            System.out.println(name + " eats mammals food");
        }

        public void breathe() {
            System.out.println(name + " breathes air");
        }
    }

    static class Pet extends Mammal {
        Pet(String name) { super(name); }

        public void beOwned() {
            System.out.println(name + " is owned by a human");
        }
    }

    static class Dog extends Pet {
        Dog(String name) { super(name); }

        public void bark() {
            System.out.println(name + " says Woof!");
        }
    }

    // BAD: GoldenRetriever is 5 levels deep just to add one behaviour
    static class GoldenRetriever extends Dog {
        GoldenRetriever(String name) { super(name); }

        // GoldenRetriever inherits everything: eat, breathe, beOwned, bark
        // But we can't easily change just the eat() without touching the hierarchy
        public void fetch() {
            System.out.println(name + " fetches the ball!");
        }
    }

    // -------------------------------------------------------------------------
    // GOOD: Flat class with composed behaviours (Strategy / Composition pattern)
    // -------------------------------------------------------------------------

    // GOOD: Define behaviours as separate, composable interfaces
    @FunctionalInterface
    interface FeedingBehaviour {
        void eat(String animalName);
    }

    @FunctionalInterface
    interface MovementBehaviour {
        void move(String animalName);
    }

    @FunctionalInterface
    interface SoundBehaviour {
        void makeSound(String animalName);
    }

    // GOOD: A flat animal class that COMPOSES behaviours rather than inheriting them.
    // Behaviours are provided at construction time — easily swappable, mockable, testable.
    // No class hierarchy needed. Adding a new behaviour = add a new field, not a new superclass.
    static class ComposedAnimal {
        private final String name;
        private final FeedingBehaviour feedingBehaviour;
        private final MovementBehaviour movementBehaviour;
        private final SoundBehaviour soundBehaviour;

        ComposedAnimal(String name,
                       FeedingBehaviour feeding,
                       MovementBehaviour movement,
                       SoundBehaviour sound) {
            this.name = name;
            this.feedingBehaviour = feeding;
            this.movementBehaviour = movement;
            this.soundBehaviour = sound;
        }

        // GOOD: delegates to injected behaviour — no inheritance needed
        public void eat()       { feedingBehaviour.eat(name); }
        public void move()      { movementBehaviour.move(name); }
        public void makeSound() { soundBehaviour.makeSound(name); }
    }

    // GOOD: Pre-defined behaviour implementations (can be shared across animals)
    static final FeedingBehaviour CARNIVORE_FEEDING  = name -> System.out.println(name + " eats meat");
    static final FeedingBehaviour HERBIVORE_FEEDING  = name -> System.out.println(name + " eats grass");
    static final MovementBehaviour RUNS              = name -> System.out.println(name + " runs on four legs");
    static final MovementBehaviour FLIES             = name -> System.out.println(name + " flies through air");
    static final SoundBehaviour BARKS               = name -> System.out.println(name + " says Woof!");
    static final SoundBehaviour MEOWS               = name -> System.out.println(name + " says Meow!");
    static final SoundBehaviour ROARS               = name -> System.out.println(name + " ROARS!");

    // GOOD: Create a golden retriever by composing behaviours — no hierarchy
    static ComposedAnimal createGoldenRetriever(String name) {
        return new ComposedAnimal(name, HERBIVORE_FEEDING, RUNS, BARKS);
        // GOOD: easy to create a "FlyingDog" by swapping RUNS for FLIES — no class change needed
    }

    // GOOD: Create a cat — completely different behaviour set, same class
    static ComposedAnimal createCat(String name) {
        return new ComposedAnimal(name, CARNIVORE_FEEDING, RUNS, MEOWS);
    }

    // GOOD: Create a lion — mix of behaviours, no new class needed
    static ComposedAnimal createLion(String name) {
        return new ComposedAnimal(name, CARNIVORE_FEEDING, RUNS, ROARS);
    }

    // -------------------------------------------------------------------------
    // Demo
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println("=== PrematureAbstractionDemo ===");
        System.out.println();

        // ---- Example 1: Addition ----
        System.out.println("--- Example 1: Adding two numbers ---");
        System.out.println();

        int a = 5, b = 3;

        System.out.println("BAD: 4 classes to add two numbers:");
        System.out.println("  Addable -> AbstractAdder -> ConcreteAdder + AdderFactory");
        long t0 = System.nanoTime();
        int badResult = badAdd(a, b);
        long badTime = System.nanoTime() - t0;
        System.out.printf("  Result: %d  (time: %dns — but it's also 3 object allocations)%n", badResult, badTime);

        System.out.println();
        System.out.println("GOOD: One line of code:");
        t0 = System.nanoTime();
        int goodResult = goodAdd(a, b);
        long goodTime = System.nanoTime() - t0;
        System.out.printf("  Result: %d  (time: %dns)%n", goodResult, goodTime);

        System.out.println();
        System.out.println("  YAGNI: You Ain't Gonna Need It.");
        System.out.println("  Add abstraction when you have 2+ real implementations.");
        System.out.println("  Not when you 'might need it someday'.");

        // ---- Example 2: Inheritance vs Composition ----
        System.out.println();
        System.out.println("--- Example 2: Deep Inheritance vs Composition ---");
        System.out.println();

        System.out.println("BAD: GoldenRetriever is 5 levels deep (Animal->Mammal->Pet->Dog->GoldenRetriever):");
        GoldenRetriever rex = new GoldenRetriever("Rex");
        rex.eat();   // from Mammal (overrides Animal)
        rex.bark();  // from Dog
        rex.fetch(); // from GoldenRetriever
        System.out.println("  Problem: To change Rex's diet, you must touch the class hierarchy.");
        System.out.println("  Problem: Cannot have a GoldenRetriever that also swims (without another layer).");

        System.out.println();
        System.out.println("GOOD: ComposedAnimal — flat class, behaviour injected at construction:");
        ComposedAnimal buddy = createGoldenRetriever("Buddy");
        buddy.eat();
        buddy.move();
        buddy.makeSound();

        System.out.println();
        ComposedAnimal whiskers = createCat("Whiskers");
        whiskers.eat();
        whiskers.move();
        whiskers.makeSound();

        System.out.println();
        System.out.println("  GOOD: Create a lion without any new class:");
        ComposedAnimal simba = createLion("Simba");
        simba.eat();
        simba.makeSound();

        // Create a "flying dog" — impossible with inheritance without adding a class
        System.out.println();
        System.out.println("  GOOD: Create a flying dog (impossible via inheritance without new class):");
        ComposedAnimal flyingDog = new ComposedAnimal("AirBud", HERBIVORE_FEEDING, FLIES, BARKS);
        flyingDog.eat();
        flyingDog.move();
        flyingDog.makeSound();

        System.out.println();
        System.out.println("Composition over Inheritance:");
        System.out.println("  - Flat class hierarchy — no fragile base class problem");
        System.out.println("  - Mix behaviours freely — a flying dog is one constructor call, not a new class");
        System.out.println("  - Test behaviours in isolation — mock FeedingBehaviour without subclassing");
        System.out.println("  - Open/Closed: add new behaviours without changing existing classes");
    }
}