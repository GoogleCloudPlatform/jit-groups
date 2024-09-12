package com.google.solutions.jitaccess.provisioning;

import com.google.solutions.jitaccess.catalog.Provisioner;
import com.google.solutions.jitaccess.catalog.policy.*;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;
import java.util.function.Supplier;


/**
 * Environment that can be provisioned to.
 */
public interface Environment {
  /**
   * Name of the policy.
   */
  @NotNull String name();

  /**
   * Description of the environment.
   */
  @NotNull String description();

  /**
   * Policy for this environment, can be delay-loaded.
   */
  @NotNull Supplier<EnvironmentPolicy> policy();

  /**
   * Provisioner for managing access to this environment.
   */
  @NotNull Provisioner provisioner();
}