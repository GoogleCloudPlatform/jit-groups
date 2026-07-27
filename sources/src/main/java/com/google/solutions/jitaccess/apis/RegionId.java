package com.google.solutions.jitaccess.apis;

import org.jetbrains.annotations.NotNull;

/**
 * A Google Cloud region.
 */
public record RegionId(@NotNull String id) {
  @Override
  public String toString() {
    return this.id;
  }
}
