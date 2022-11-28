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

import com.google.api.services.cloudasset.v1.model.ConditionEvaluation;
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

import javax.enterprise.context.RequestScoped;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service that contains the logic required to find eligible roles, and to activate them.
 */
@RequestScoped
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
   * List projects available to the given user. The user may or may not have
   * eligible roles in these projects.
   */
  public Set<ProjectId> listAvailableProjects(
    UserId user
  ) throws AccessException, IOException {
    //
    // Use Asset API to search projects on which the user has been
    // granted the 'resourcemanager.projects.get' permission.
    //
    // Always expand resources.
    //
    var analysisResult = this.assetInventoryAdapter.findAccessibleResourcesByUser(
      this.options.scope,
      user,
      Optional.of("resourcemanager.projects.get"),
      Optional.empty(),
      true);

    // Consider permanent and eligible bindings.
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
    Preconditions.checkNotNull(user, "user");
    Preconditions.checkNotNull(projectId, "projectId");

    //
    // Use Asset API to search for resources that the user **could**
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
    var activatedRoles = findRoleBindings(
      analysisResult,
      condition -> JitConstraints.isActivated(condition),
      evalResult -> "TRUE".equalsIgnoreCase(evalResult))
      .stream()
      .map(binding -> new ProjectRole(binding, ProjectRole.Status.ACTIVATED))
      .collect(Collectors.toList());

    //
    // Find all JIT-eligible role bindings. The bindings are
    // conditional and have a special condition that serves
    // as marker.
    //
    var jitEligibleRoles = findRoleBindings(
      analysisResult,
      condition -> JitConstraints.isJitAccessConstraint(condition),
      evalResult -> "CONDITIONAL".equalsIgnoreCase(evalResult))
      .stream()
      .map(binding -> new ProjectRole(binding, ProjectRole.Status.ELIGIBLE_FOR_JIT))
      .collect(Collectors.toList());

    //
    // Find all MPA-eligible role bindings. The bindings are
    // conditional and have a special condition that serves
    // as marker.
    //
    var mpaEligibleRoles = findRoleBindings(
      analysisResult,
      condition -> JitConstraints.isMultiPartyApprovalConstraint(condition),
      evalResult -> "CONDITIONAL".equalsIgnoreCase(evalResult))
      .stream()
      .map(binding -> new ProjectRole(binding, ProjectRole.Status.ELIGIBLE_FOR_MPA))
      .collect(Collectors.toList());

    //
    // Merge the three lists.
    //
    // NB. We can't use !activatedRoles.contains(...)
    // because of the different binding statuses.
    //
    var allEligibleRoles = Stream.concat(jitEligibleRoles.stream(), mpaEligibleRoles.stream());
    var consolidatedRoles = allEligibleRoles
      .filter(r -> !activatedRoles
        .stream()
        .anyMatch(a -> a.roleBinding.equals(r.roleBinding)))
      .collect(Collectors.toList());
    consolidatedRoles.addAll(activatedRoles);

    return new Result<ProjectRole>(
      consolidatedRoles.stream()
        .sorted((r1, r2) -> r1.roleBinding.fullResourceName.compareTo(r2.roleBinding.fullResourceName))
        .collect(Collectors.toList()),
      Stream.ofNullable(analysisResult.getNonCriticalErrors())
        .flatMap(Collection::stream)
        .map(e -> e.getCause())
        .collect(Collectors.toList()));
  }

  /**
   * List users that can approve the activation of an eligible role binding
   */
  public Collection<UserId> listApproversForProjectRole(
    UserId callerUserId,
    RoleBinding roleBinding
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(callerUserId, "callerUserId");
    Preconditions.checkNotNull(roleBinding, "roleBinding");

    assert (ProjectId.isProjectFullResourceName(roleBinding.fullResourceName));

    //
    // Check that the (calling) user is really allowed to request approval
    // this role.
    //
    var projectId = ProjectId.fromFullResourceName(roleBinding.fullResourceName);

    var eligibleRoles = listEligibleProjectRoles(callerUserId, projectId);
    if (!eligibleRoles
      .getItems()
      .stream()
      .filter(pr -> pr.roleBinding.equals(roleBinding))
      .filter(pr -> pr.status == ProjectRole.Status.ELIGIBLE_FOR_MPA)
      .findAny()
      .isPresent()) {
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

    var approvers = Stream.ofNullable(analysisResult.getAnalysisResults())
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
      .collect(Collectors.toList());

    return approvers;
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
