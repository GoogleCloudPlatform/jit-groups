//
// Copyright 2021 Google LLC
//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.google.solutions.jitaccess.core.services;

import com.google.api.services.cloudasset.v1.model.Expr;
import com.google.api.services.cloudasset.v1.model.IamPolicyAnalysis;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.adapters.AssetInventoryAdapter;
import com.google.solutions.jitaccess.core.data.ProjectId;
import com.google.solutions.jitaccess.core.data.ProjectRole;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserId;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for discovering eligible roles.
 */
@ApplicationScoped
public class RoleDiscoveryService {
  private final AssetInventoryAdapter assetInventoryAdapter;

  private final Options options;

  public RoleDiscoveryService(
    AssetInventoryAdapter assetInventoryAdapter,
    Options configuration) {
    Preconditions.checkNotNull(assetInventoryAdapter, "assetInventoryAdapter");
    Preconditions.checkNotNull(configuration, "configuration");

    this.assetInventoryAdapter = assetInventoryAdapter;
    this.options = configuration;
  }

  private static List<RoleBinding> findRoleBindings(
    IamPolicyAnalysis analysisResult,
    Predicate<Expr> conditionPredicate,
    Predicate<String> conditionEvaluationPredicate
  ) {
    //
    // NB. We don't really care which resource a policy is attached to
    // (indicated by AttachedResourceFullName). Instead, we care about
    // which resources it applies to.
    //
    return Stream.ofNullable(analysisResult.getAnalysisResults())
      .flatMap(Collection::stream)

      // Narrow down to IAM bindings with a specific IAM condition.
      .filter(result -> conditionPredicate.test(result.getIamBinding() != null
        ? result.getIamBinding().getCondition()
        : null))
      .flatMap(result -> result
        .getAccessControlLists()
        .stream()

        // Narrow down to ACLs with a specific IAM condition evaluation result.
        .filter(acl -> conditionEvaluationPredicate.test(acl.getConditionEvaluation() != null
          ? acl.getConditionEvaluation().getEvaluationValue()
          : null))

        // Collect all (supported) resources covered by these bindings/ACLs.
        .flatMap(acl -> acl.getResources()
          .stream()
          .filter(res -> ProjectId.isProjectFullResourceName(res.getFullResourceName()))
          .map(res -> new RoleBinding(
            res.getFullResourceName(),
            result.getIamBinding().getRole()))))
      .collect(Collectors.toList());
  }

  // ---------------------------------------------------------------------
  // Public methods.
  // ---------------------------------------------------------------------

  public Options getOptions() {
    return options;
  }

  /**
   * Find projects that a user has standing, JIT-, or MPA-eligible access to.
   */
  public Set<ProjectId> listAvailableProjects(
    UserId user
  ) throws AccessException, IOException {
    //
    // NB. To reliably find projects, we have to let the Asset API consider
    // inherited role bindings by using the "expand resources" flag. This
    // flag causes the API to return *all* resources for which an IAM binding
    // applies.
    //
    // The risk here is that the list of resources grows so large that we're hitting
    // the limits of the API, in which case it starts truncating results. To
    // mitigate this risk, filter on a permission that:
    //
    // - only applies to projects, and has no meaning on descendent resources
    // - represents the lowest level of access to a project.
    //
    var analysisResult = this.assetInventoryAdapter.findAccessibleResourcesByUser(
      this.options.scope,
      user,
      Optional.of("resourcemanager.projects.get"),
      Optional.empty(),
      true);

    //
    // Consider permanent and eligible bindings.
    //
    var roleBindings = findRoleBindings(
      analysisResult,
      condition -> condition == null ||
        JitConstraints.isJitAccessConstraint(condition) ||
        JitConstraints.isMultiPartyApprovalConstraint(condition),
      evalResult -> evalResult == null ||
        "TRUE".equalsIgnoreCase(evalResult) ||
        "CONDITIONAL".equalsIgnoreCase(evalResult));

    return roleBindings
      .stream()
      .map(b -> ProjectId.fromFullResourceName(b.fullResourceName))
      .collect(Collectors.toSet());
  }

  /**
   * List eligible role bindings for the given user.
   */
  public Result<ProjectRole> listEligibleProjectRoles(
    UserId user,
    ProjectId projectId
  ) throws AccessException, IOException {
    return listEligibleProjectRoles(
      user,
      projectId,
      EnumSet.of(
        ProjectRole.Status.ACTIVATED,
        ProjectRole.Status.ELIGIBLE_FOR_JIT,
        ProjectRole.Status.ELIGIBLE_FOR_MPA));
  }

  /**
   * List eligible role bindings for the given user.
   */
  public Result<ProjectRole> listEligibleProjectRoles(
    UserId user,
    ProjectId projectId,
    EnumSet<ProjectRole.Status> statusesToInclude
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(user, "user");
    Preconditions.checkNotNull(projectId, "projectId");

    //
    // Use Asset API to search for resources that the user could
    // access if they satisfied the eligibility condition.
    //
    // NB. The existence of an eligibility condition alone isn't
    // sufficient - it needs to be on a binding that applies to the
    // user.
    //
    // NB. The Asset API considers group membership if the caller
    // (i.e., the App Engine service account) has the 'Groups Reader'
    // admin role.
    //

    var analysisResult = this.assetInventoryAdapter.findAccessibleResourcesByUser(
      this.options.scope,
      user,
      Optional.empty(),
      Optional.of(projectId.getFullResourceName()),
      false);

    //
    // Find role bindings which have already been activated.
    // These bindings have a time condition that we created, and
    // the condition evaluates to true (indicating it's still
    // valid).
    //
    Set<ProjectRole> activatedRoles;
    if (statusesToInclude.contains(ProjectRole.Status.ACTIVATED)) {
      activatedRoles = findRoleBindings(
        analysisResult,
        condition -> JitConstraints.isActivated(condition),
        evalResult -> "TRUE".equalsIgnoreCase(evalResult))
        .stream()
        .map(binding -> new ProjectRole(binding, ProjectRole.Status.ACTIVATED))
        .collect(Collectors.toSet());
    }
    else {
      activatedRoles = Set.of();
    }

    //
    // Find all JIT-eligible role bindings. The bindings are
    // conditional and have a special condition that serves
    // as marker.
    //
    Set<ProjectRole> jitEligibleRoles;
    if (statusesToInclude.contains(ProjectRole.Status.ELIGIBLE_FOR_JIT)) {
      jitEligibleRoles = findRoleBindings(
        analysisResult,
        condition -> JitConstraints.isJitAccessConstraint(condition),
        evalResult -> "CONDITIONAL".equalsIgnoreCase(evalResult))
        .stream()
        .map(binding -> new ProjectRole(binding, ProjectRole.Status.ELIGIBLE_FOR_JIT))
        .collect(Collectors.toSet());
    }
    else {
      jitEligibleRoles = Set.of();
    }

    //
    // Find all MPA-eligible role bindings. The bindings are
    // conditional and have a special condition that serves
    // as marker.
    //
    Set<ProjectRole> mpaEligibleRoles;
    if (statusesToInclude.contains(ProjectRole.Status.ELIGIBLE_FOR_MPA)) {
      mpaEligibleRoles = findRoleBindings(
        analysisResult,
        condition -> JitConstraints.isMultiPartyApprovalConstraint(condition),
        evalResult -> "CONDITIONAL".equalsIgnoreCase(evalResult))
        .stream()
        .map(binding -> new ProjectRole(binding, ProjectRole.Status.ELIGIBLE_FOR_MPA))
        .collect(Collectors.toSet());
    }
    else {
      mpaEligibleRoles = Set.of();
    }

    //
    // Determine effective set of eligible roles. If a role is both JIT- and
    // MPA-eligible, only retain the JIT-eligible one.
    //
    // Use a list so that JIT-eligible roles go first, followed by MPA-eligible ones.
    //
    var allEligibleRoles = new ArrayList<ProjectRole>();
    allEligibleRoles.addAll(jitEligibleRoles);
    allEligibleRoles.addAll(mpaEligibleRoles
      .stream()
      .filter(r -> !jitEligibleRoles.stream().anyMatch(a -> a.roleBinding.equals(r.roleBinding)))
      .collect(Collectors.toList()));

    //
    // Replace roles that have been activated already.
    //
    // NB. We can't use !activatedRoles.contains(...)
    // because of the different binding statuses.
    //
    var consolidatedRoles = allEligibleRoles
      .stream()
      .filter(r -> !activatedRoles.stream().anyMatch(a -> a.roleBinding.equals(r.roleBinding)))
      .collect(Collectors.toList());
    consolidatedRoles.addAll(activatedRoles);

    return new Result<>(
      consolidatedRoles.stream()
        .sorted(Comparator.comparing(r -> r.roleBinding.fullResourceName))
        .collect(Collectors.toList()),
      Stream.ofNullable(analysisResult.getNonCriticalErrors())
        .flatMap(Collection::stream)
        .map(e -> e.getCause())
        .collect(Collectors.toList()));
  }

  /**
   * List users that can approve the activation of an eligible role binding.
   */
  public Set<UserId> listEligibleUsersForProjectRole(
    UserId callerUserId,
    RoleBinding roleBinding
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(callerUserId, "callerUserId");
    Preconditions.checkNotNull(roleBinding, "roleBinding");

    assert ProjectId.isProjectFullResourceName(roleBinding.fullResourceName);

    //
    // Check that the (calling) user is really allowed to request approval
    // this role.
    //
    var projectId = ProjectId.fromFullResourceName(roleBinding.fullResourceName);

    var eligibleRoles = listEligibleProjectRoles(callerUserId, projectId);
    if (eligibleRoles
      .getItems()
      .stream()
      .filter(pr -> pr.roleBinding.equals(roleBinding))
      .filter(pr -> pr.status == ProjectRole.Status.ELIGIBLE_FOR_MPA)
      .findAny()
      .isEmpty()) {
      throw new AccessDeniedException(
        String.format("The user %s is not eligible to request approval for this role", callerUserId));
    }

    //
    // Find other eligible users.
    //
    var analysisResult = this.assetInventoryAdapter.findPermissionedPrincipalsByResource(
      this.options.scope,
      roleBinding.fullResourceName,
      roleBinding.role);

    return Stream.ofNullable(analysisResult.getAnalysisResults())
      .flatMap(Collection::stream)

      // Narrow down to IAM bindings with an MPA constraint.
      .filter(result -> result.getIamBinding() != null &&
        JitConstraints.isMultiPartyApprovalConstraint(result.getIamBinding().getCondition()))

      // Collect identities (users and group members)
      .filter(result -> result.getIdentityList() != null)
      .flatMap(result -> result.getIdentityList().getIdentities().stream()
        .filter(id -> id.getName().startsWith("user:"))
        .map(id -> new UserId(id.getName().substring("user:".length()))))

      // Remove the caller.
      .filter(user -> !user.equals(callerUserId))
      .collect(Collectors.toSet());
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public static class Options {
    /**
     * Scope, organization/ID, folder/ID, or project/ID
     */
    public final String scope;

    /**
     * Search inherited IAM policies
     */
    public Options(String scope) {
      this.scope = scope;
    }
  }
}
