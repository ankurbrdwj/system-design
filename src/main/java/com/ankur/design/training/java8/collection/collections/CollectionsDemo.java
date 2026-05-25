package com.ankur.design.training.java8.collection.collections;

import java.util.*;

/**
 * java.util.Collections utility class — static helper methods for List, Set, Map.
 *
 * Key groups:
 *   1. Sorting & ordering     — sort, reverse, shuffle, swap, rotate
 *   2. Searching              — binarySearch, min, max, frequency, disjoint
 *   3. Mutation helpers       — fill, copy, replaceAll, nCopies
 *   4. Unmodifiable wrappers  — unmodifiableList/Set/Map
 *   5. Synchronized wrappers  — synchronizedList/Set/Map
 *   6. Singleton / empty views — singleton, emptyList, singletonMap
 *   7. Reversal utils         — reverseOrder comparator
 */
public class CollectionsDemo {

    public static void main(String[] args) {
        sorting();
        searching();
        mutationHelpers();
        unmodifiableWrappers();
        synchronizedWrappers();
        singletonAndEmpty();
        reverseOrderComparator();
    }

    // -------------------------------------------------------------------------
    // 1. Sorting & ordering
    // -------------------------------------------------------------------------
    static void sorting() {
        System.out.println("=== Sorting & Ordering ===");

        List<Integer> nums = new ArrayList<>(Arrays.asList(5, 3, 8, 1, 9, 2));

        Collections.sort(nums);                          // natural order
        System.out.println("sort:    " + nums);          // [1, 2, 3, 5, 8, 9]

        Collections.reverse(nums);
        System.out.println("reverse: " + nums);          // [9, 8, 5, 3, 2, 1]

        Collections.shuffle(nums, new Random(42));
        System.out.println("shuffle: " + nums);          // deterministic with seed

        Collections.swap(nums, 0, nums.size() - 1);
        System.out.println("swap 0↔last: " + nums);

        List<String> words = new ArrayList<>(Arrays.asList("a", "b", "c", "d", "e"));
        Collections.rotate(words, 2);                    // right-rotate by 2
        System.out.println("rotate right 2: " + words); // [d, e, a, b, c]

        Collections.rotate(words, -2);                   // left-rotate by 2
        System.out.println("rotate left  2: " + words);  // [a, b, c, d, e]
    }

    // -------------------------------------------------------------------------
    // 2. Searching
    // -------------------------------------------------------------------------
    static void searching() {
        System.out.println("\n=== Searching ===");

        List<Integer> sorted = Arrays.asList(1, 2, 3, 5, 8, 9);

        int idx = Collections.binarySearch(sorted, 5);  // list must be sorted
        System.out.println("binarySearch(5): index " + idx); // 3

        System.out.println("min: " + Collections.min(sorted)); // 1
        System.out.println("max: " + Collections.max(sorted)); // 9

        List<String> data = Arrays.asList("a", "b", "a", "c", "a");
        System.out.println("frequency('a'): " + Collections.frequency(data, "a")); // 3

        List<String> setA = Arrays.asList("x", "y", "z");
        List<String> setB = Arrays.asList("1", "2", "3");
        List<String> setC = Arrays.asList("y", "2");
        System.out.println("disjoint(A,B): " + Collections.disjoint(setA, setB)); // true
        System.out.println("disjoint(A,C): " + Collections.disjoint(setA, setC)); // false
    }

    // -------------------------------------------------------------------------
    // 3. Mutation helpers
    // -------------------------------------------------------------------------
    static void mutationHelpers() {
        System.out.println("\n=== Mutation Helpers ===");

        // fill — overwrite every element
        List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
        Collections.fill(list, "X");
        System.out.println("fill: " + list); // [X, X, X]

        // copy — src → dst (dst must be at least as large)
        List<Integer> src = Arrays.asList(10, 20, 30);
        List<Integer> dst = new ArrayList<>(Arrays.asList(0, 0, 0, 0));
        Collections.copy(dst, src);
        System.out.println("copy: " + dst); // [10, 20, 30, 0]

        // nCopies — immutable list of n copies (useful as initial capacity filler)
        List<String> copies = Collections.nCopies(4, "hello");
        System.out.println("nCopies: " + copies); // [hello, hello, hello, hello]

        // replaceAll (List) — replace every occurrence of oldVal with newVal
        List<String> mixed = new ArrayList<>(Arrays.asList("a", "b", "a", "c"));
        Collections.replaceAll(mixed, "a", "Z");
        System.out.println("replaceAll a→Z: " + mixed); // [Z, b, Z, c]
    }

    // -------------------------------------------------------------------------
    // 4. Unmodifiable wrappers
    // -------------------------------------------------------------------------
    static void unmodifiableWrappers() {
        System.out.println("\n=== Unmodifiable Wrappers ===");

        List<String> mutable = new ArrayList<>(Arrays.asList("x", "y", "z"));
        List<String> readOnly = Collections.unmodifiableList(mutable);

        System.out.println("readOnly: " + readOnly);

        // Structural changes on backing list ARE visible through the view
        mutable.add("w");
        System.out.println("after mutable.add: " + readOnly); // [x, y, z, w]

        // Any mutation attempt on readOnly throws UnsupportedOperationException
        try {
            readOnly.add("fail");
        } catch (UnsupportedOperationException e) {
            System.out.println("readOnly.add threw UnsupportedOperationException (expected)");
        }

        // Same pattern for Set and Map
        Set<Integer> roSet = Collections.unmodifiableSet(new HashSet<>(Set.of(1, 2, 3)));
        Map<String, Integer> roMap = Collections.unmodifiableMap(new HashMap<>(Map.of("a", 1)));
        System.out.println("roSet: " + roSet + "  roMap: " + roMap);
    }

    // -------------------------------------------------------------------------
    // 5. Synchronized wrappers
    //    Wrap non-thread-safe collections for basic thread safety.
    //    Iteration must still be externally synchronized on the returned collection.
    // -------------------------------------------------------------------------
    static void synchronizedWrappers() {
        System.out.println("\n=== Synchronized Wrappers ===");

        List<String> syncList = Collections.synchronizedList(new ArrayList<>());
        syncList.add("a");
        syncList.add("b");

        // Safe iteration: lock on the wrapper itself
        synchronized (syncList) {
            for (String s : syncList) {
                System.out.print(s + " ");
            }
        }
        System.out.println();

        Map<String, Integer> syncMap = Collections.synchronizedMap(new HashMap<>());
        syncMap.put("x", 1);
        System.out.println("syncMap: " + syncMap);

        // NOTE: prefer ConcurrentHashMap / CopyOnWriteArrayList for better throughput
    }

    // -------------------------------------------------------------------------
    // 6. Singleton / empty views  (immutable, zero-allocation)
    // -------------------------------------------------------------------------
    static void singletonAndEmpty() {
        System.out.println("\n=== Singleton & Empty Views ===");

        List<String> one  = Collections.singletonList("only");
        Set<Integer> oneS = Collections.singleton(42);
        Map<String, Integer> oneM = Collections.singletonMap("key", 99);
        System.out.println("singletonList: " + one);
        System.out.println("singleton:     " + oneS);
        System.out.println("singletonMap:  " + oneM);

        List<Object>  emptyL = Collections.emptyList();
        Set<Object>   emptyS = Collections.emptySet();
        Map<?,?>      emptyM = Collections.emptyMap();
        System.out.println("emptyList: " + emptyL + "  emptySet: " + emptyS + "  emptyMap: " + emptyM);
    }

    // -------------------------------------------------------------------------
    // 7. reverseOrder() comparator
    // -------------------------------------------------------------------------
    static void reverseOrderComparator() {
        System.out.println("\n=== reverseOrder Comparator ===");

        List<Integer> nums = new ArrayList<>(Arrays.asList(3, 1, 4, 1, 5, 9, 2, 6));
        nums.sort(Collections.reverseOrder());
        System.out.println("desc sort: " + nums); // [9, 6, 5, 4, 3, 2, 1, 1]

        // Works with PriorityQueue too — turns min-heap into max-heap
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
        maxHeap.addAll(Arrays.asList(3, 1, 4, 1, 5));
        System.out.println("maxHeap poll: " + maxHeap.poll()); // 5

        // Compose with custom Comparator: reverse by string length
        List<String> words = new ArrayList<>(Arrays.asList("fig", "apple", "kiwi", "mango"));
        words.sort(Collections.reverseOrder(Comparator.comparingInt(String::length)));
        System.out.println("reverse by length: " + words); // [apple, mango, kiwi, fig]
    }
}