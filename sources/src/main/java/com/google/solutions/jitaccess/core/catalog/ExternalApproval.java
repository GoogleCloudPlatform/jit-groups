package com.google.solutions.jitaccess.core.catalog;

public class ExternalApproval implements ActivationType {

  private final String baseName = "EXTERNAL_APPROVAL";
  private final String topic;

  public ExternalApproval(String topic) {
    this.topic = topic;
  }

  @Override
  public String name() {
    return this.baseName + "(" + this.topic + ")";
  }

  @Override
  public boolean isParentTypeOf(ActivationType other) {
    if (!(other instanceof ExternalApproval)) {
      return false;
    }

    if (this.topic.equals("")) {
      return true;
    }

    return this.name().equals(other.name());
  }

}
