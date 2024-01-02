package com.google.solutions.jitaccess.core.catalog.project;

import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.Entitlement;

import java.io.IOException;
import java.util.*;

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
  Annotated<SortedSet<Entitlement<ProjectRoleBinding>>> findEntitlements(
    UserId user,
    ProjectId projectId,
    EnumSet<ActivationType> typesToInclude,
    EnumSet<Entitlement.Status> statusesToInclude
  ) throws AccessException, IOException;

  /**
   * List users that hold an eligible role binding.
   */
  Set<UserId> findEntitlementHolders(
    ProjectRoleBinding roleBinding
  ) throws AccessException, IOException;
}
