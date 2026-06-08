package com.ankur.design.training.java8;

import java.util.ArrayList;
import java.util.List;

// JAVA GENERICS — RULES
//
// RULE 1  Naming conventions: T=Type  E=Element  K=Key  V=Value  N=Number  R=Return
// RULE 2  Type erasure: <T> is erased to Object at runtime — no T.class, no instanceof T
// RULE 3  Cannot instantiate T directly: `new T()` is illegal
// RULE 4  Cannot create generic arrays: `new T[]` is illegal
// RULE 5  Static members cannot use the class-level type parameter
// RULE 6  Upper bound:    <T extends Animal>    T must be Animal or subtype
// RULE 7  Lower bound:    <? super Cat>         wildcard must be Cat or supertype
// RULE 8  PECS — Producer Extends, Consumer Super
//           read  from collection → use <? extends T>   (you GET values out)
//           write to  collection → use <? super T>      (you PUT values in)
// RULE 9  Invariance: List<Dog> is NOT a subtype of List<Animal> — generics are invariant
// RULE 10 Multiple bounds: <T extends Comparable<T> & Cloneable>  class first, interfaces after
// RULE 11 Generic methods declare their own <T> before return type — independent of class T
// RULE 12 Wildcard <?> unbounded — read as Object, cannot write (except null)

public class GenericsDemo {

    // ─────────────────────────────────────────────────────────────────
    // RULE 1 — naming + basic generic class
    // ─────────────────────────────────────────────────────────────────
    static class Box<T> {           // T = any Type
        private T value;

        Box(T value)      { this.value = value; }
        T get()           { return value; }
        void set(T value) { this.value = value; }
    }


    // ─────────────────────────────────────────────────────────────────
    // RULE 2 — type erasure: T becomes Object at bytecode level
    //          Box<String> and Box<Integer> are the SAME class at runtime
    // ─────────────────────────────────────────────────────────────────
    static void erasureDemo() {
        Box<String>  s = new Box<>("hello");
        Box<Integer> i = new Box<>(42);

        System.out.println(s.getClass() == i.getClass()); // true — same class
        // s instanceof Box<String>  ← compile error: cannot check erased type
    }


    // ─────────────────────────────────────────────────────────────────
    // RULE 3 & 4 — cannot instantiate T or T[]
    // ─────────────────────────────────────────────────────────────────
    static class BadContainer<T> {
        // T item   = new T();    ← compile error: cannot instantiate type parameter
        // T[] arr  = new T[10];  ← compile error: cannot create generic array
    }

    // workaround for RULE 3: pass a factory or Class<T>
    static <T> T create(Class<T> clazz) throws Exception {
        return clazz.getDeclaredConstructor().newInstance();
    }


    // ─────────────────────────────────────────────────────────────────
    // RULE 5 — static members cannot reference class-level T
    // ─────────────────────────────────────────────────────────────────
    static class Registry<T> {
        // static T instance;         ← compile error: T is per-instance, not per-class
        // static T getInstance() {}  ← compile error

        static <R> R staticHelper(R input) { return input; } // OK — own type param R
    }


    // ─────────────────────────────────────────────────────────────────
    // RULE 6 — upper bound: <T extends Animal>
    //          T can be Animal or any subclass
    // ─────────────────────────────────────────────────────────────────
    static class Animal { String name; Animal(String n) { name = n; } }
    static class Dog extends Animal { Dog(String n) { super(n); } void bark() {} }
    static class Cat extends Animal { Cat(String n) { super(n); } void meow() {} }

    static class Cage<T extends Animal> {   // T must be Animal or subtype
        private T animal;
        Cage(T a)    { this.animal = a; }
        void feed()  { System.out.println("Feeding " + animal.name); } // Animal methods available
    }

    // ─────────────────────────────────────────────────────────────────
    // RULE 9 — invariance: List<Dog> is NOT a List<Animal>
    // ─────────────────────────────────────────────────────────────────
    static void invarianceDemo() {
        List<Dog> dogs = new ArrayList<>();
        // List<Animal> animals = dogs;  ← compile error — invariant!
        // If it compiled: animals.add(new Cat(...)) would corrupt the Dog list
    }


    // ─────────────────────────────────────────────────────────────────
    // RULE 8 — PECS
    //   Producer Extends → reading values OUT of a collection
    //   Consumer Super   → writing values INTO a collection
    // ─────────────────────────────────────────────────────────────────

    // PRODUCER: reads from src — use extends (you get Animals out)
    static double totalWeight(List<? extends Animal> src) {
        return src.size() * 10.0;       // can read, cannot add to src
        // src.add(new Dog("x"));       ← compile error: type-unsafe
    }

    // CONSUMER: writes into dest — use super (dest can accept Animals)
    static void copyAnimals(List<? extends Animal> src, List<? super Animal> dest) {
        for (Animal a : src) dest.add(a); // dest accepts Animal or wider (Object)
    }

    // UNBOUNDED wildcard — only need Object methods, don't care about type
    static void printAll(List<?> list) {
        for (Object o : list) System.out.println(o);
        // list.add("x");  ← compile error: cannot write to unbounded wildcard
    }


    // ─────────────────────────────────────────────────────────────────
    // RULE 7 — lower bound: <? super Cat>
    //          accepts Cat, Animal, Object — anything Cat fits into
    // ─────────────────────────────────────────────────────────────────
    static void addCats(List<? super Cat> list) {
        list.add(new Cat("Whiskers"));  // safe: list accepts Cat or wider
        // Cat c = list.get(0);         ← compile error: get returns Object, not Cat
    }


    // ─────────────────────────────────────────────────────────────────
    // RULE 10 — multiple bounds: class first, then interfaces
    // ─────────────────────────────────────────────────────────────────
    static <T extends Animal & Comparable<T>> T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
        // T must extend Animal AND implement Comparable
    }


    // ─────────────────────────────────────────────────────────────────
    // RULE 11 — generic method: declares its own <T> before return type
    //           independent of any class-level type parameter
    // ─────────────────────────────────────────────────────────────────
    static <T> List<T> repeat(T item, int times) {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < times; i++) result.add(item);
        return result;
    }

    // generic method with bounded type — works on any Comparable
    static <T extends Comparable<T>> T min(T a, T b) {
        return a.compareTo(b) <= 0 ? a : b;
    }


    // ─────────────────────────────────────────────────────────────────
    // RULE 12 — unbounded wildcard <?> — read-only, returns Object
    // ─────────────────────────────────────────────────────────────────
    static int countNonNull(List<?> list) {
        int count = 0;
        for (Object o : list) if (o != null) count++;
        return count;
    }


    public static void main(String[] args) {
        // Rule 1 — basic box
        Box<String> name = new Box<>("Ankur");
        System.out.println(name.get());

        // Rule 6 — upper bound cage
        Cage<Dog> dogCage = new Cage<>(new Dog("Rex"));
        dogCage.feed();
        // Cage<String> bad = new Cage<>("text"); ← compile error: String not an Animal

        // Rule 8 — PECS
        List<Dog> dogs = List.of(new Dog("Rex"), new Dog("Max"));
        List<Animal> animals = new ArrayList<>();
        copyAnimals(dogs, animals);
        System.out.println("Copied: " + animals.size());

        // Rule 11 — generic method
        List<String> hellos = repeat("hello", 3);
        System.out.println(hellos);
        System.out.println(min("apple", "banana"));

        // Rule 2 — erasure
        erasureDemo();
    }
}