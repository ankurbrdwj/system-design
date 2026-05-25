package com.ankur.design.hft.tuning;

/**
 * TOPIC: Array of Structures (AoS) vs Structure of Arrays (SoA) — data layout.
 *
 * This is one of the most impactful optimizations in HFT and game engines.
 *
 * CACHE LINE = 64 bytes (the unit the CPU loads from RAM into cache).
 *
 * AoS (Array of Structures) — the OOP-natural layout:
 *   [ Particle{x,y,z,mass} | Particle{x,y,z,mass} | Particle{x,y,z,mass} | ... ]
 *   Each Particle = 4 doubles = 32 bytes. Two particles per 64-byte cache line.
 *   If you only need 'mass': you load x, y, z into cache too — WASTED.
 *   Useful bytes per cache line: 8 (mass) out of 64 → 12.5% utilization.
 *
 * SoA (Structure of Arrays) — the data-oriented layout:
 *   x[]    = [ x0 | x1 | x2 | ... ]  — 8 doubles per 64-byte cache line
 *   y[]    = [ y0 | y1 | y2 | ... ]
 *   z[]    = [ z0 | z1 | z2 | ... ]
 *   mass[] = [ m0 | m1 | m2 | ... ]  — 8 masses per 64-byte cache line
 *   If you only need 'mass': you load ONLY mass[] — 8 values per cache line.
 *   Useful bytes per cache line: 64 (all mass) out of 64 → 100% utilization.
 *
 * Real-world use:
 *   - Risk engine: sum all positions (only need 'quantity', not full Trade object)
 *   - Market data: scan all bid prices (only need 'bid', not full Quote object)
 *   - Physics engine: update all positions (only need x, y, z — not mass)
 */
public class DataLayoutDemo {

    static final int N = 1_000_000; // number of particles

    // -------------------------------------------------------------------------
    // BAD: Array of Objects (AoS)
    // -------------------------------------------------------------------------

    // BAD: Each Particle object is a heap object with a header (~16 bytes) plus fields.
    // Object header: 8 bytes mark word + 4 bytes class pointer = 12 bytes (compressed oops).
    // Data: x(8) + y(8) + z(8) + mass(8) = 32 bytes per Particle.
    // Total per object: ~48 bytes (with alignment padding).
    //
    // Array of Particle = array of REFERENCES (pointers).
    // Each reference dereference = pointer chase = potential cache miss.
    // Even if Particle objects are allocated sequentially, GC may scatter them later.
    static class Particle {
        double x;
        double y;
        double z;
        double mass;

        Particle(double x, double y, double z, double mass) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.mass = mass;
        }
    }

    // BAD: Sum only the mass field, but the CPU must load the ENTIRE Particle object
    // (including x, y, z) into cache on each access.
    //
    // Memory accessed per particle: 32 bytes (4 doubles) + pointer dereference
    // Useful data per cache line: mass (8 bytes) out of 64 bytes loaded = 12.5% efficiency
    static double sumMassAoS(Particle[] particles) {
        double totalMass = 0;
        for (int i = 0; i < particles.length; i++) {
            totalMass += particles[i].mass; // BAD: also loads x, y, z into cache — unused
        }
        return totalMass;
    }

    // BAD: Even updating all fields suffers from pointer indirection
    static void updatePositionsAoS(Particle[] particles, double dt) {
        for (int i = 0; i < particles.length; i++) {
            // BAD: each particles[i] is a pointer chase
            particles[i].x += particles[i].x * dt;
            particles[i].y += particles[i].y * dt;
        }
    }

    // -------------------------------------------------------------------------
    // GOOD: Structure of Arrays (SoA)
    // -------------------------------------------------------------------------

    // GOOD: Separate primitive arrays for each field.
    // No object headers, no pointer chasing, perfect spatial locality.
    //
    // mass[] = [ m0, m1, m2, m3, m4, m5, m6, m7, m8, ... ]
    //           ^------- one 64-byte cache line -------^
    //           8 doubles = 64 bytes = 1 cache line
    //
    // Accessing mass[i] loads 8 consecutive masses into cache for free.
    // Useful data per cache line: 64 bytes (8 masses) = 100% efficiency.
    static class ParticleSystem {
        final double[] x;
        final double[] y;
        final double[] z;
        final double[] mass;
        final int size;

        ParticleSystem(int size) {
            this.size = size;
            this.x = new double[size];
            this.y = new double[size];
            this.z = new double[size];
            this.mass = new double[size];
        }
    }

    // GOOD: Sum only mass[] — touches ONLY the mass array.
    // 8 values loaded per cache miss (vs 1 useful value per cache miss in AoS).
    static double sumMassSoA(ParticleSystem ps) {
        double totalMass = 0;
        double[] mass = ps.mass; // cache array reference in local variable
        for (int i = 0; i < ps.size; i++) {
            totalMass += mass[i]; // GOOD: sequential access, 8 masses per cache line, no indirection
        }
        return totalMass;
    }

    // GOOD: Update x and y — touches only x[] and y[].
    // z[] and mass[] are NOT loaded into cache — they don't need to be.
    static void updatePositionsSoA(ParticleSystem ps, double dt) {
        double[] x = ps.x;
        double[] y = ps.y;
        for (int i = 0; i < ps.size; i++) {
            // GOOD: only x[] and y[] touched — mass[] and z[] stay out of cache
            x[i] += x[i] * dt;
            y[i] += y[i] * dt;
        }
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    static Particle[] buildAoS(int n) {
        Particle[] particles = new Particle[n];
        for (int i = 0; i < n; i++) {
            particles[i] = new Particle(i * 0.1, i * 0.2, i * 0.3, i * 1.5 + 1.0);
        }
        return particles;
    }

    static ParticleSystem buildSoA(int n) {
        ParticleSystem ps = new ParticleSystem(n);
        for (int i = 0; i < n; i++) {
            ps.x[i]    = i * 0.1;
            ps.y[i]    = i * 0.2;
            ps.z[i]    = i * 0.3;
            ps.mass[i] = i * 1.5 + 1.0;
        }
        return ps;
    }

    public static void main(String[] args) {
        System.out.println("=== DataLayoutDemo: AoS vs SoA ===");
        System.out.println("Cache line = 64 bytes = 8 doubles");
        System.out.printf("Particles: %,d%n%n", N);

        System.out.println("Building data structures...");
        Particle[] aos = buildAoS(N);
        ParticleSystem soa = buildSoA(N);

        // Warmup
        System.out.println("Warming up JIT...");
        for (int w = 0; w < 5; w++) {
            sumMassAoS(aos);
            sumMassSoA(soa);
            updatePositionsAoS(aos, 0.001);
            updatePositionsSoA(soa, 0.001);
        }

        final int REPS = 5;

        // --- Benchmark: sum mass only ---
        System.out.println();
        System.out.println("--- Benchmark 1: Sum mass field only ---");
        System.out.printf("  AoS: loads all 4 fields (x,y,z,mass) per object — 12.5%% cache efficiency%n");
        System.out.printf("  SoA: loads only mass[] — 100%% cache efficiency%n%n");

        long aosTotal = 0; double aosResult = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            aosResult = sumMassAoS(aos);
            aosTotal += System.nanoTime() - t0;
        }

        long soaTotal = 0; double soaResult = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            soaResult = sumMassSoA(soa);
            soaTotal += System.nanoTime() - t0;
        }

        long aosTime = aosTotal / REPS;
        long soaTime = soaTotal / REPS;

        System.out.printf("BAD  (AoS, sum mass): %,6d ms  result=%.0f%n", aosTime / 1_000_000, aosResult);
        System.out.printf("GOOD (SoA, sum mass): %,6d ms  result=%.0f%n", soaTime / 1_000_000, soaResult);
        if (soaTime > 0) {
            System.out.printf("Speedup: %.1fx%n", (double) aosTime / soaTime);
        }

        // --- Benchmark: update x, y positions ---
        System.out.println();
        System.out.println("--- Benchmark 2: Update x, y positions (ignore z, mass) ---");
        System.out.printf("  AoS: loads all fields per object even though z/mass not needed%n");
        System.out.printf("  SoA: loads only x[] and y[] — z[] and mass[] stay in their own arrays%n%n");

        long aosUpdateTotal = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            updatePositionsAoS(aos, 0.001);
            aosUpdateTotal += System.nanoTime() - t0;
        }

        long soaUpdateTotal = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            updatePositionsSoA(soa, 0.001);
            soaUpdateTotal += System.nanoTime() - t0;
        }

        long aosUpdateTime = aosUpdateTotal / REPS;
        long soaUpdateTime = soaUpdateTotal / REPS;

        System.out.printf("BAD  (AoS, update x,y): %,6d ms%n", aosUpdateTime / 1_000_000);
        System.out.printf("GOOD (SoA, update x,y): %,6d ms%n", soaUpdateTime / 1_000_000);
        if (soaUpdateTime > 0) {
            System.out.printf("Speedup: %.1fx%n", (double) aosUpdateTime / soaUpdateTime);
        }

        System.out.println();
        System.out.println("Memory layout comparison:");
        System.out.println("  AoS: [x0,y0,z0,m0][x1,y1,z1,m1][x2,y2,z2,m2]...");
        System.out.println("       1 cache line = 2 Particles = 2 useful mass values (for mass-only ops)");
        System.out.println("  SoA: [m0,m1,m2,m3,m4,m5,m6,m7][m8,m9,...]");
        System.out.println("       1 cache line = 8 useful mass values (for mass-only ops)");
        System.out.println();
        System.out.println("HFT applications:");
        System.out.println("  - Order book: store bids[] and asks[] separately, not as Order objects");
        System.out.println("  - Risk engine: columnar position storage for fast summation");
        System.out.println("  - Market data: price[], size[], time[] arrays — not Quote objects");
    }
}