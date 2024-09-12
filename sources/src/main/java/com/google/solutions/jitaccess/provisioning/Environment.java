package com.google.solutions.jitaccess.provisioning;

import com.google.solutions.jitaccess.catalog.Provisioner;
import com.google.solutions.jitaccess.catalog.policy.*;
import org.jetbrains.annotations.NotNull;


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
   * Policy for this environment.
   */
  @NotNull EnvironmentPolicy policy();

  /**
   * Provisioner for managing access to this environment.
   */
  @NotNull Provisioner provisioner();
}