package com.google.solutions.jitaccess.core.catalog;

public class PeerApproval implements ActivationType {

  private final String baseName = "PEER_APPROVAL";
  private final String topic;

  public PeerApproval(String topic) {
    this.topic = topic;
  }

  @Override
  public String name() {
    return this.baseName + "(" + this.topic + ")";
  }

  @Override
  public boolean isParentTypeOf(ActivationType other) {
    if (!(other instanceof PeerApproval)) {
      return false;
    }

    if (this.topic.equals("")) {
      return true;
    }

    return this.name().equals(other.name());
  }

}
