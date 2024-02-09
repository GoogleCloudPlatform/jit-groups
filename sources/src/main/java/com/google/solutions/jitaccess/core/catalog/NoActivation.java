package com.google.solutions.jitaccess.core.catalog;

public class NoActivation implements ActivationType {

  private final String name = "NONE";

  @Override
  public String name() {
    return this.name;
  }

  @Override
  public boolean isParentTypeOf(ActivationType other) {
    return this.name().equals(other.name());
  }
}
