package com.google.solutions.jitaccess.core.activation.project;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AnnotatedResult;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.activation.ActivationType;
import com.google.solutions.jitaccess.core.activation.Entitlement;
import com.google.solutions.jitaccess.core.entitlements.RoleBinding;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.SortedSet;

public class PolicyAnalyzer {

  /**
   * Find projects that a user has standing, JIT-, or MPA-eligible access to.
   */
  public SortedSet<ProjectId> listAvailableProjects( // TODO: findProjectsWithRoleBindings
    UserId user
  ) throws AccessException, IOException {

    Preconditions.checkNotNull(user, "user");

    throw new RuntimeException("NIY");
  }

  /**
   * List entitlements for the given user.
   */
  public AnnotatedResult<Entitlement<ProjectRoleId>> listEligibleProjectRoles(//TODO: rename to findEligibleRoleBindings
    UserId user,
    ProjectId projectId,
    EnumSet<Entitlement.Status> statusesToInclude
  ) throws AccessException, IOException {

    Preconditions.checkNotNull(user, "user");
    Preconditions.checkNotNull(projectId, "projectId");

    throw new RuntimeException("NIY");
  }

  /**
    * List users that can approve the activation of an eligible role binding.
    */
  public Set<UserId> listEligibleUsersForProjectRole(//TODO: findUsersEligibleForRoleBinding
    RoleBinding roleBinding,
    ActivationType activationType
  ) throws AccessException, IOException {

    Preconditions.checkNotNull(roleBinding, "roleBinding");

    throw new RuntimeException("NIY");
  }
}
