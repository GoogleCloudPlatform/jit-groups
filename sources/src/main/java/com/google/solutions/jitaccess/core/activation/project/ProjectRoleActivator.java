
package com.google.solutions.jitaccess.core.activation.project;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.activation.*;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;

/**
 * Activator for project roles.
 */
@ApplicationScoped
public class ProjectRoleActivator extends EntitlementActivator<ProjectRoleId> {
  private final ResourceManagerClient resourceManagerClient;
  private final Options options;

  public ProjectRoleActivator(
    EntitlementCatalog<ProjectRoleId> catalog,
    ResourceManagerClient resourceManagerClient,
    JustificationPolicy policy,
    Options options
  ) {
    super(catalog, policy);

    Preconditions.checkNotNull(resourceManagerClient, "resourceManagerClient");
    Preconditions.checkNotNull(options, "options");

    this.resourceManagerClient = resourceManagerClient;
    this.options = options;
  }

  // -------------------------------------------------------------------------
  // Overrides.
  // -------------------------------------------------------------------------

  @Override
  protected Activation<ProjectRoleId> provisionAccess(
    ActivationRequest<ProjectRoleId> request
  ) throws AccessException {
    //TODO: implement
    throw new RuntimeException("NIY");
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public record Options (
   Duration maxActivationTimeout,
   int minNumberOfReviewersPerActivationRequest,
   int maxNumberOfReviewersPerActivationRequest
  ) {
    static final int MIN_ACTIVATION_TIMEOUT_MINUTES = 5;

    public Options {
      Preconditions.checkArgument(
        maxActivationTimeout.toMinutes() >= MIN_ACTIVATION_TIMEOUT_MINUTES,
        "Activation timeout must be at least 5 minutes");
      Preconditions.checkArgument(
        minNumberOfReviewersPerActivationRequest > 0,
        "The minimum number of reviewers cannot be 0");
      Preconditions.checkArgument(
        minNumberOfReviewersPerActivationRequest <= maxNumberOfReviewersPerActivationRequest,
        "The minimum number of reviewers must not exceed the maximum");
    }
  }
}
