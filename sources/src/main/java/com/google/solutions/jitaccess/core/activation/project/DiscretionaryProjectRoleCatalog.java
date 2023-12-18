package com.google.solutions.jitaccess.core.activation.project;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AnnotatedResult;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.activation.ActivationType;
import com.google.solutions.jitaccess.core.activation.Entitlement;
import com.google.solutions.jitaccess.core.clients.AssetInventoryClient;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;

/**
 * Policy Analyzer-based catalog for project-level role bindings that
 * have been granted in a discretionary way by a project owner or admin.
 */
@ApplicationScoped
public class DiscretionaryProjectRoleCatalog extends ProjectRoleCatalog {
  private final AssetInventoryClient assetInventoryClient;
  private final ResourceManagerClient resourceManagerClient;
  private final Options options;

  public DiscretionaryProjectRoleCatalog(
    AssetInventoryClient assetInventoryClient,
    ResourceManagerClient resourceManagerClient,
    Options options
  ) {
    Preconditions.checkNotNull(assetInventoryClient, "assetInventoryClient");
    Preconditions.checkNotNull(resourceManagerClient, "resourceManagerClient");
    Preconditions.checkNotNull(options, "options");

    this.assetInventoryClient = assetInventoryClient;
    this.resourceManagerClient = resourceManagerClient;
    this.options = options;
  }

  private AnnotatedResult<Entitlement<ProjectRoleId>> listEligibleProjectRoles(
    UserId user,
    ProjectId projectId,
    EnumSet<Entitlement.Status> statusesToInclude,
    EnumSet<ActivationType> typesToInclude
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(user, "user");
    Preconditions.checkNotNull(projectId, "projectId");

    throw new RuntimeException("NIY");
  }

  private void verifyAccess(
    UserId user,
    Collection<ProjectRoleId> entitlements,
    EnumSet<ActivationType> typesToInclude
  ) throws AccessException {
    throw new RuntimeException("NIY");
  }

  //---------------------------------------------------------------------------
  // Overrides.
  //---------------------------------------------------------------------------

  @Override
  public AnnotatedResult<ProjectId> listProjects(
    UserId user
  ) throws AccessException, IOException {
    throw new RuntimeException("NIY");
  }

  @Override
  public AnnotatedResult<Entitlement<ProjectRoleId>> listEntitlements(
    UserId user,
    ProjectId projectId
  ) throws AccessException, IOException {
    return listEligibleProjectRoles(
      user,
      projectId,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE),
      EnumSet.of(ActivationType.JIT, ActivationType.MPA));
  }

  @Override
  public AnnotatedResult<UserId> listReviewers(
    UserId requestingUser,
    ProjectRoleId entitlement
  ) {
    throw new RuntimeException("NIY");
  }

  @Override
  public void canRequest(
    UserId requestingUser,
    Collection<ProjectRoleId> entitlements,
    Duration duration
  ) throws AccessException {

    Preconditions.checkNotNull(requestingUser, "requestingUser");
    Preconditions.checkNotNull(entitlements, "entitlements");
    Preconditions.checkNotNull(duration, "duration");

    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException("Duration must be positive");
    }

    if (duration.toSeconds() < this.options.minActivationDuration().toSeconds()) {
      throw new IllegalArgumentException("Duration is too short");
    }

    if (duration.toSeconds() > this.options.maxActivationDuration().toSeconds()) {
      throw new IllegalArgumentException("Duration exceeds maximum permissable duration");
    }

    throw new RuntimeException("NIY");

  }

  @Override
  public void canApprove(
    UserId approvingUser,
    Collection<ProjectRoleId> entitlements
  ) throws AccessException {
    throw new RuntimeException("NIY");

  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  /**
   * If a query is provided, the class performs a Resource Manager project
   * search instead of Policy Analyzer query to list projects. This is faster,
   * but results in non-personalized results.
   *
   * @param scope organization/ID, folder/ID, or project/ID.
   * @param availableProjectsQuery optional, search query, for example:
   *      - parent:folders/{folder_id}
   * @param maxActivationDuration maximum duration for an activation
   */
  public record Options(
    String scope,
    String availableProjectsQuery,
    Duration maxActivationDuration
  ) {
    static final int MIN_ACTIVATION_TIMEOUT_MINUTES = 5;

    public Options {
      Preconditions.checkNotNull(scope, "scope");
      Preconditions.checkNotNull(maxActivationDuration, "maxActivationDuration");
      Preconditions.checkArgument(!maxActivationDuration.isNegative());
    }

    public Duration minActivationDuration() {
      return Duration.ofMinutes(MIN_ACTIVATION_TIMEOUT_MINUTES);
    }
  }
}
