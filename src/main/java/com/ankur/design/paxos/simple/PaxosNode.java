package com.ankur.design.paxos.simple;

import java.util.List;
import java.util.ArrayList;
import java.util.List;

class PaxosNode {
  private int id;
  private int currentProposalNumber = 0;
  private int highestProposalNumber = 0;
  private int acceptedProposalNumber = 0;
  private Object acceptedValue = null;

  public PaxosNode(int id) {
    this.id = id;
  }

  public void proposeValue(Object value, List<PaxosNode> nodes) {
    currentProposalNumber++;
    highestProposalNumber = currentProposalNumber;
    System.out.println("Node " + id + " proposes value: " + value + " with proposal number: " + currentProposalNumber);

    // Simulate sending prepare messages to all nodes
    for (PaxosNode node : nodes) {
      node.receivePrepare(this, currentProposalNumber, value, nodes);
    }
  }

  public void receivePrepare(PaxosNode sender, int proposalNumber, Object value, List<PaxosNode> nodes) {
    if (proposalNumber > highestProposalNumber) {
      highestProposalNumber = proposalNumber;
    }
    System.out.println("Node " + id + " receives prepare message from Node " + sender.id + " with proposal number: " + proposalNumber);

    // Simulate sending promise message back to sender
    sender.receivePromise(this, highestProposalNumber, acceptedProposalNumber, acceptedValue, nodes);
  }

  public void receivePromise(PaxosNode sender, int highestProposal, int acceptedProposal, Object acceptedValue, List<PaxosNode> nodes) {
    System.out.println("Node " + id + " receives promise message from Node " + sender.id + " with highest proposal number: " + highestProposal);

    if (highestProposal > currentProposalNumber) {
      currentProposalNumber = highestProposal;
      System.out.println("Node " + id + " updates its proposal number to: " + currentProposalNumber);
    }

    // Simulate sending accept message if the proposal is still valid
    if (acceptedProposalNumber == 0 || highestProposal == currentProposalNumber) {
      acceptedProposalNumber = currentProposalNumber;
      this.acceptedValue = acceptedValue;
      System.out.println("Node " + id + " sends accept message with proposal number: " + currentProposalNumber);
      for (PaxosNode node : nodes) {
        node.receiveAccept(this, currentProposalNumber, acceptedValue, nodes);
      }
    }
  }

  public void receiveAccept(PaxosNode sender, int proposalNumber, Object value, List<PaxosNode> nodes) {
    System.out.println("Node " + id + " receives accept message from Node " + sender.id + " with proposal number: " + proposalNumber);
    // Simulate sending accepted message to all nodes
    for (PaxosNode node : nodes) {
      node.receiveAccepted(this, proposalNumber, value, nodes);
    }
  }

  public void receiveAccepted(PaxosNode sender, int proposalNumber, Object value, List<PaxosNode> nodes) {
    System.out.println("Node " + id + " receives accepted message from Node " + sender.id + " with proposal number: " + proposalNumber + " and value: " + value);
  }
}
