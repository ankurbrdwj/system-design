package com.ankur.design.hashing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class ConsistentHashTest {

    @Test
    void testKeyAlwaysMapsToSomeNode() {
        ConsistentHash<String> ch = new ConsistentHash<>(List.of("S1", "S2", "S3"));
        String node = ch.get("myKey");
        assertNotNull(node);
        assertTrue(List.of("S1", "S2", "S3").contains(node));
    }

    @Test
    void testStabilityWhenAddingNode() {
        ConsistentHash<String> ch1 = new ConsistentHash<>(List.of("S1", "S2", "S3"));
        String nodeBefore = ch1.get("user123");

        ConsistentHash<String> ch2 = new ConsistentHash<>(List.of("S1", "S2", "S3", "S4"));
        String nodeAfter = ch2.get("user123");

        // Either stays same or moves only to S4
        assertTrue(nodeBefore.equals(nodeAfter) || nodeAfter.equals("S4"));
    }

    @Test
    void testRemoveNodeReassignsKeys() {
        ConsistentHash<String> ch = new ConsistentHash<>(List.of("S1", "S2", "S3"));
        String nodeBefore = ch.get("user456");

        ch.removeNode(nodeBefore);
        String nodeAfter = ch.get("user456");

        assertNotNull(nodeAfter);
        assertNotEquals(nodeBefore, nodeAfter);
    }
}