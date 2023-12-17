package com.google.solutions.jitaccess.core.activation.project;

import com.google.solutions.jitaccess.core.AnnotatedResult;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.activation.Entitlement;
import com.google.solutions.jitaccess.core.activation.EntitlementCatalog;

import java.util.Collection;

/**
 * Catalog for project-level role bindings.
 */
public abstract class ProjectRoleCatalog implements EntitlementCatalog<ProjectRoleId> {
  public abstract AnnotatedResult<Entitlement<ProjectRoleId>> listEntitlements(
    UserId user,
    ProjectId projectId
  );

  public abstract AnnotatedResult<UserId> listReviewers(
    UserId requestingUser,
    ProjectRoleId entitlement
  );
}
