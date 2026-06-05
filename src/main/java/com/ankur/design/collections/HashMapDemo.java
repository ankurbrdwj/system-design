package com.ankur.design.collections;

import java.util.*;

/**
 * HashMap method reference — interview use cases.
 *
 * HashMap:  O(1) get/put avg, NOT thread-safe, allows null key/value, NO order.
 * Use when: fast lookup by key, no concurrency, order doesn't matter.
 */
public class HashMapDemo {

    public static void main(String[] args) {
        basicOps();
        mergeAndCompute();
        iterationPatterns();
        frequencyCounter();
        groupByPattern();
        twoSumPattern();
        cachePattern();
    }

    // ── 1. BASIC OPS ─────────────────────────────────────────────────────────

    static void basicOps() {
        System.out.println("=== 1. Basic Ops ===");

        Map<String, Integer> scores = new HashMap<>();

        // put — O(1) avg
        scores.put("Alice", 90);
        scores.put("Bob", 85);
        scores.put("Charlie", 92);

        // get — returns null if missing (trap: auto-unbox NPE)
        System.out.println(scores.get("Alice"));       // 90
        System.out.println(scores.get("Dave"));        // null

        // getOrDefault — safe, avoids null check
        System.out.println(scores.getOrDefault("Dave", 0)); // 0

        // containsKey / containsValue
        System.out.println(scores.containsKey("Bob"));       // true
        System.out.println(scores.containsValue(999));       // false

        // putIfAbsent — only inserts if key not present
        scores.putIfAbsent("Alice", 0);   // ignored — Alice exists
        scores.putIfAbsent("Dave", 70);   // inserted
        System.out.println(scores.get("Alice")); // still 90
        System.out.println(scores.get("Dave"));  // 70

        // remove by key, or key+value (conditional remove)
        scores.remove("Dave");
        scores.remove("Bob", 999); // no-op — value doesn't match
        System.out.println(scores.containsKey("Bob")); // true — not removed

        // size, isEmpty
        System.out.println(scores.size());    // 3
        System.out.println(scores.isEmpty()); // false
    }

    // ── 2. MERGE AND COMPUTE ─────────────────────────────────────────────────

    static void mergeAndCompute() {
        System.out.println("\n=== 2. merge / compute / replace ===");

        Map<String, Integer> map = new HashMap<>();

        // compute — applies function whether key exists or not
        map.compute("x", (k, v) -> v == null ? 1 : v + 1); // x=1
        map.compute("x", (k, v) -> v == null ? 1 : v + 1); // x=2
        System.out.println(map.get("x")); // 2

        // computeIfAbsent — only runs if key missing; great for lazy init
        map.computeIfAbsent("y", k -> k.length()); // y=1
        map.computeIfAbsent("y", k -> 999);         // ignored — y already set
        System.out.println(map.get("y")); // 1

        // computeIfPresent — only runs if key exists
        map.computeIfPresent("x", (k, v) -> v * 10); // x=20
        map.computeIfPresent("z", (k, v) -> 999);     // no-op — z missing
        System.out.println(map.get("x")); // 20

        // merge — if key absent: use value; if present: apply function
        // use case: accumulate/sum without checking null
        map.merge("score", 5, Integer::sum); // score=5
        map.merge("score", 3, Integer::sum); // score=8
        System.out.println(map.get("score")); // 8

        // replace — only replaces if key exists
        map.replace("score", 100);
        System.out.println(map.get("score")); // 100

        // replace(key, oldVal, newVal) — CAS-style conditional replace
        map.replace("score", 100, 200);
        System.out.println(map.get("score")); // 200
    }

    // ── 3. ITERATION PATTERNS ────────────────────────────────────────────────

    static void iterationPatterns() {
        System.out.println("\n=== 3. Iteration ===");

        Map<String, Integer> map = Map.of("a", 1, "b", 2, "c", 3); // immutable

        // entrySet — most efficient, gives key+value together
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            System.out.println(e.getKey() + " -> " + e.getValue());
        }

        // forEach — cleaner lambda version
        map.forEach((k, v) -> System.out.println(k + "=" + v));

        // keySet — when you only need keys
        map.keySet().forEach(System.out::println);

        // values — when you only need values
        int sum = map.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("sum=" + sum); // 6
    }

    // ── 4. FREQUENCY COUNTER ─────────────────────────────────────────────────
    // use case: "count char frequency", "find duplicates", "most common element"

    static void frequencyCounter() {
        System.out.println("\n=== 4. Frequency Counter ===");

        String[] words = {"apple", "banana", "apple", "cherry", "banana", "apple"};

        Map<String, Integer> freq = new HashMap<>();
        for (String w : words) {
            freq.merge(w, 1, Integer::sum);         // cleanest idiom
            // equivalent: freq.put(w, freq.getOrDefault(w, 0) + 1);
        }
        System.out.println(freq); // {apple=3, banana=2, cherry=1}

        // find most frequent
        String top = Collections.max(freq.entrySet(),
                Map.Entry.comparingByValue()).getKey();
        System.out.println("top: " + top); // apple
    }

    // ── 5. GROUP BY ───────────────────────────────────────────────────────────
    // use case: group employees by department, orders by status

    static void groupByPattern() {
        System.out.println("\n=== 5. Group By ===");

        List<String> names = List.of("Alice", "Bob", "Anna", "Brian", "Carol");

        Map<Character, List<String>> byLetter = new HashMap<>();
        for (String name : names) {
            // computeIfAbsent: create list only on first encounter
            byLetter.computeIfAbsent(name.charAt(0), k -> new ArrayList<>()).add(name);
        }
        System.out.println(byLetter); // {A=[Alice, Anna], B=[Bob, Brian], C=[Carol]}

        // same thing with streams (cleaner for production)
        // Map<Character, List<String>> grouped =
        //     names.stream().collect(Collectors.groupingBy(n -> n.charAt(0)));
    }

    // ── 6. TWO SUM (classic interview pattern) ───────────────────────────────
    // use case: O(n) lookup instead of O(n²) nested loop

    static void twoSumPattern() {
        System.out.println("\n=== 6. Two Sum ===");

        int[] nums = {2, 7, 11, 15};
        int target = 9;

        Map<Integer, Integer> seen = new HashMap<>(); // value -> index
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (seen.containsKey(complement)) {
                System.out.println("Indices: " + seen.get(complement) + ", " + i); // 0, 1
                break;
            }
            seen.put(nums[i], i);
        }
    }

    // ── 7. SIMPLE CACHE (memoization) ────────────────────────────────────────
    // use case: avoid recomputing expensive results

    static void cachePattern() {
        System.out.println("\n=== 7. Memoization Cache ===");

        Map<Integer, Long> memo = new HashMap<>();

        System.out.println(fib(10, memo)); // 55
        System.out.println(fib(10, memo)); // 55, served from cache
        System.out.println("cache size: " + memo.size()); // 10 entries
    }

    static long fib(int n, Map<Integer, Long> memo) {
        if (n <= 1) return n;
        // computeIfAbsent: only computes if not cached
        return memo.computeIfAbsent(n, k -> fib(k - 1, memo) + fib(k - 2, memo));
    }
}