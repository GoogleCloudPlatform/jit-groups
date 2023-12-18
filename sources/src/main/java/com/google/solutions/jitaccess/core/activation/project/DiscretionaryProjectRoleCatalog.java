package com.google.solutions.jitaccess.core.activation.project;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AnnotatedResult;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.activation.*;
import com.google.solutions.jitaccess.core.clients.AssetInventoryClient;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.core.entitlements.RoleDiscoveryService;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
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
  private final RoleDiscoveryService.Options options;

  public DiscretionaryProjectRoleCatalog(
    AssetInventoryClient assetInventoryClient,
    ResourceManagerClient resourceManagerClient,
    RoleDiscoveryService.Options options
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
    EnumSet<Entitlement.Requirement> requirementsToInclude
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(user, "user");
    Preconditions.checkNotNull(projectId, "projectId");

    throw new RuntimeException("NIY");
  }

  private void verifyAccess(
    UserId user,
    Collection<ProjectRoleId> entitlements,
    Entitlement.Requirement requirement
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
  public void verifyAccess(
    ActivationRequest<ProjectRoleId> request
  ) throws AccessException {
    verifyAccess(
      request.requestingUser(),
      request.entitlements(),
      request instanceof MpaActivationRequest<ProjectRoleId>
        ? Entitlement.Requirement.MPA
        : Entitlement.Requirement.JIT);
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
      EnumSet.of(Entitlement.Requirement.JIT, Entitlement.Requirement.MPA));
  }

  @Override
  public AnnotatedResult<UserId> listReviewers(
    UserId requestingUser,
    ProjectRoleId entitlement
  ) {
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
   */
  public record Options(
    String scope,
    String availableProjectsQuery
  ) {
    public Options {
      Preconditions.checkNotNull(scope, "scope");
    }
  }
}
