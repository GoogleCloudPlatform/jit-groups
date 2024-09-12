package com.google.solutions.jitaccess.catalog;

import com.google.solutions.jitaccess.catalog.policy.*;
import com.google.solutions.jitaccess.common.Lazy;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Environment that can be provisioned to.
 */
public abstract class Environment implements PolicyHeader {
  private final @NotNull String name;
  private final @NotNull String description;
  private final @NotNull Lazy<EnvironmentPolicy> policy;
  private final @NotNull Provisioner provisioner;

  protected Environment(
    @NotNull String name,
    @NotNull String description,
    @NotNull Provisioner provisioner,
    @NotNull Duration policyCacheDuration
  ) {
    this.name = name;
    this.description = description;
    this.provisioner = provisioner;

    //
    // Load policy on first access only, because doing so
    // might be slow.
    //
    this.policy = Lazy
      .initializeOpportunistically(this::loadPolicy)
      .reinitializeAfter(policyCacheDuration);
  }

  /**
   * Name of the policy.
   */
  @Override
  public @NotNull String name() {
    return this.name;
  }

  /**
   * Description of the environment.
   */
  @Override
  public @NotNull String description() {
    return this.description;
  }

  /**
   * Policy for this environment, can be delay-loaded.
   */
  protected @NotNull EnvironmentPolicy policy() {
    return this.policy.get();
  }

  /**
   * Provisioner for managing access to this environment.
   */
  protected @NotNull Provisioner provisioner() {
    return this.provisioner;
  }

  /**
   * Load policy from file or backing store.
   */
  protected abstract EnvironmentPolicy loadPolicy();
}