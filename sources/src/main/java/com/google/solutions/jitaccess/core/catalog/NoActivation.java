package com.google.solutions.jitaccess.core.catalog;

public class NoActivation extends ActivationType {

  private final String name = "NONE";

  @Override
  public String name() {
    return this.name;
  }

  @Override
  public boolean contains(ActivationType other) {
    return this.name().equals(other.name());
  }
}
