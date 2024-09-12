package com.google.solutions.jitaccess.provisioning;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Registry for environments.
 */
public class EnvironmentRegistry {
  private final @NotNull Collection<Environment> environments;

  public EnvironmentRegistry(
    @NotNull Collection<Environment> environments
  ) {
    this.environments = environments;
  }

  public Collection<Environment> environments() {
    return environments;
  }
}
