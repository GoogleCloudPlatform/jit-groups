package com.google.solutions.jitaccess.core.catalog;

public class PeerApproval extends ActivationType {

  private final String name = "PEER_APPROVAL";
  private final String topic;

  public PeerApproval(String topic) {
    this.topic = topic;
  }

  @Override
  public String name() {
    return this.name + "(" + this.topic + ")";
  }

  @Override
  public boolean contains(ActivationType other) {
    if (!(other instanceof PeerApproval)) {
      return false;
    }

    if (this.topic.equals("")) {
      return true;
    }

    return this.name().equals(other.name());
  }

}
