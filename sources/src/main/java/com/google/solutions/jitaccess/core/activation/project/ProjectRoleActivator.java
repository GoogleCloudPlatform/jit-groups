
package com.google.solutions.jitaccess.core.activation.project;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.activation.*;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.core.entitlements.ProjectRoleId;

import java.time.Instant;
import java.util.Collection;

/**
 * Activator for project roles.
 */
public class ProjectRoleActivator extends Activator<ProjectRoleId> {
  private final ResourceManagerClient resourceManagerClient;

  public ProjectRoleActivator(
    ResourceManagerClient resourceManagerClient,
    JustificationPolicy policy
  ) {
    super(policy);

    Preconditions.checkNotNull(resourceManagerClient, "resourceManagerClient");

    this.resourceManagerClient = resourceManagerClient;
  }

  @Override
  protected void verifyAccessCore(
    ActivationRequest<ProjectRoleId> request
  ) throws AccessException {
    //TODO: implement
  }

  /**
   * Verify and apply a request to grant a project role.
   */
  @Override
  protected void applyRequestCore(
    ActivationRequest<ProjectRoleId> request
  ) throws AccessException {
    //TODO: implement
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public static class JitRequest extends JitActivationRequest<ProjectRoleId> {
    public JitRequest(
      UserId requestingUser,
      Collection<ProjectRoleId> entitlements,
      String justification,
      Instant startTime,
      Instant endTime) {
      super(requestingUser, entitlements, justification, startTime, endTime);
    }
  }

  public static class MpaRequest extends MpaActivationRequest<ProjectRoleId> {
    public MpaRequest(
      UserId requestingUser,
      Collection<ProjectRoleId> entitlements,
      Collection<UserId> reviewers,
      String justification,
      Instant startTime,
      Instant endTime) {
      super(requestingUser, entitlements, reviewers, justification, startTime, endTime);
    }
  }
}
