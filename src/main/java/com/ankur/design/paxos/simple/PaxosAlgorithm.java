package com.ankur.design.paxos.simple;

import java.util.ArrayList;
import java.util.List;

public class PaxosAlgorithm {
  public static void main(String[] args) {
    // Create Paxos nodes
    PaxosNode node1 = new PaxosNode(1);
    PaxosNode node2 = new PaxosNode(2);
    PaxosNode node3 = new PaxosNode(3);

    List<PaxosNode> nodes = new ArrayList<>();
    nodes.add(node1);
    nodes.add(node2);
    nodes.add(node3);

    // Simulate proposing a value to the Paxos cluster
    Object value = "Hello, Paxos!";
    node1.proposeValue(value, nodes);
  }
}
