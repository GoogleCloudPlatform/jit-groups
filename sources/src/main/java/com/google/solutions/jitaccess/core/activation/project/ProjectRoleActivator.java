
package com.google.solutions.jitaccess.core.activation.project;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.activation.*;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

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

  /**
   * Verify and apply a request to grant a project role.
   */
  @Override
  protected Activation<ProjectRoleId> applyRequestCore(
    ActivationRequest<ProjectRoleId> request
  ) throws AccessException {
    //TODO: implement
    throw new RuntimeException("NIY");
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  static class JitRequest extends JitActivationRequest<ProjectRoleId> {
    public JitRequest(
      UserId requestingUser,
      Collection<ProjectRoleId> entitlements,
      String justification,
      Instant startTime,
      Instant endTime
    ) {
      super(requestingUser, entitlements, justification, startTime, endTime);
    }
  }

  static class MpaRequest extends MpaActivationRequest<ProjectRoleId> {
    public MpaRequest(
      UserId requestingUser,
      Collection<ProjectRoleId> entitlements,
      Collection<UserId> reviewers,
      String justification,
      Instant startTime,
      Instant endTime
    ) {
      super(requestingUser, entitlements, reviewers, justification, startTime, endTime);

      if (entitlements.size() != 1) {
        throw new IllegalArgumentException("Only one entitlement can be activated at a time");
      }
    }
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
