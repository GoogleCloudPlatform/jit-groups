package com.google.solutions.jitaccess.core.activation.project;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AnnotatedResult;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.activation.ActivationRequest;
import com.google.solutions.jitaccess.core.activation.Entitlement;
import com.google.solutions.jitaccess.core.clients.AssetInventoryClient;

import java.util.Collection;

/**
 * Catalog for project-level role bindings that have been
 * granted in a discretionary way by a project owner or admin.
 */
public class DiscretionaryProjectRoleCatalog extends ProjectRoleCatalog {
  private final AssetInventoryClient assetInventoryClient;

  public DiscretionaryProjectRoleCatalog(
    AssetInventoryClient assetInventoryClient
  ) {
    Preconditions.checkNotNull(assetInventoryClient, "assetInventoryClient");
    this.assetInventoryClient = assetInventoryClient;
  }

  @Override
  public void verifyAccess(
    ActivationRequest<ProjectRoleId> request
  ) throws AccessException {
    throw new RuntimeException("NIY");
  }

  @Override
  public AnnotatedResult<Entitlement<ProjectRoleId>> listEntitlements(
    UserId user,
    ProjectId projectId
  ) {
    throw new RuntimeException("NIY");
  }

  @Override
  public AnnotatedResult<UserId> listReviewers(
    UserId requestingUser,
    ProjectRoleId entitlement
  ) {
    throw new RuntimeException("NIY");
  }
}
