package com.google.solutions.jitaccess.core.catalog.project;

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.Entitlement;
import com.google.solutions.jitaccess.core.catalog.EntitlementSet;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.SortedSet;

/**
 * Repository for ProjectRoleBinding-based entitlements.
 */
public interface ProjectRoleRepository {

  /**
   * Find projects that a user has standing, JIT-, or MPA-eligible access to.
   */
  SortedSet<ProjectId> findProjectsWithEntitlements(
    UserId user
  ) throws AccessException, IOException;

  /**
   * List entitlements for the given user.
   */
  EntitlementSet<ProjectRoleBinding> findEntitlements(
    UserId user,
    ProjectId projectId,
    EnumSet<ActivationType> typesToInclude,
    EnumSet<Entitlement.Status> statusesToInclude
  ) throws AccessException, IOException;

  /**
   * List users that hold an eligible role binding.
   */
  Set<UserId> findEntitlementHolders(
    ProjectRoleBinding roleBinding,
    ActivationType activationType
  ) throws AccessException, IOException;
}
