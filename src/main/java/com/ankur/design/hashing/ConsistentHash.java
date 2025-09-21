package com.ankur.design.hashing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class ConsistentHash<T> {
    private static final Logger log = LoggerFactory.getLogger(ConsistentHash.class);

    private final SortedMap<Integer, T> circle = new TreeMap<>();

    public ConsistentHash(List<T> nodes) {
        log.info("Initializing Consistent Hash Ring...");
        for (T node : nodes) {
            int hash = node.hashCode();
            circle.put(hash, node);
            log.info("Added node [{}] with hash [{}] to the ring.", node, hash);
        }
        log.info("Ring initialization complete with {} nodes.", circle.size());
    }

    public T get(Object key) {
        if (circle.isEmpty()) {
            log.warn("No nodes in the ring! Key [{}] cannot be mapped.", key);
            return null;
        }

        int hash = key.hashCode();
        log.info("Looking up key [{}] with hash [{}]", key, hash);

        if (!circle.containsKey(hash)) {
            SortedMap<Integer, T> tail = circle.tailMap(hash);
            if (tail.isEmpty()) {
                log.info("No node found clockwise after hash [{}], wrapping to first node.", hash);
                hash = circle.firstKey();
            } else {
                hash = tail.firstKey();
                log.info("Next clockwise node found at hash [{}]", hash);
            }
        } else {
            log.info("Exact node found at hash [{}]", hash);
        }

        T node = circle.get(hash);
        log.info("Key [{}] is mapped to node [{}]", key, node);
        return node;
    }

    public void addNode(T node) {
        int hash = node.hashCode();
        circle.put(hash, node);
        log.info("Node [{}] added to ring at hash [{}]. Total nodes: {}", node, hash, circle.size());
    }

    public void removeNode(T node) {
        int hash = node.hashCode();
        circle.remove(hash);
        log.info("Node [{}] removed from ring (hash [{}]). Remaining nodes: {}", node, hash, circle.size());
    }

    public void printRing() {
        log.info("Current Hash Ring Layout:");
        for (Map.Entry<Integer, T> entry : circle.entrySet()) {
            log.info("Hash [{}] -> Node [{}]", entry.getKey(), entry.getValue());
        }
    }
}
