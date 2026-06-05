package com.ankur.design.lld.animal;

// CAN-DO role — Birds, Reptiles, and monotreme Mammals (Platypus) all lay eggs
// Putting this on Mammal would be wrong: most mammals don't lay eggs
public interface EggLayer {

    void layEgg();
}