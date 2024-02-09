package com.google.solutions.jitaccess.core.catalog;

public class SelfApproval implements ActivationType {

  private final String base_name = "SELF_APPROVAL";

  @Override
  public String name() {
    return this.base_name;
  }

  @Override
  public boolean isParentTypeOf(ActivationType other) {
    return this.name().equals(other.name());
  }
}
