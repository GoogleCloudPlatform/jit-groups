package com.google.solutions.jitaccess.provisioning;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface EnvironmentRegistry {
  /**
   * Get all environments managed by this provider.
   */
  @NotNull Collection<Environment> environments();
}
