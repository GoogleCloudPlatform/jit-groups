//
// Copyright 2023 Google LLC
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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.api.services.cloudasset.v1.model.Expr;
import com.google.api.services.cloudasset.v1.model.IamPolicyAnalysis;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.Entitlement;
import com.google.solutions.jitaccess.core.catalog.EntitlementSet;
import com.google.solutions.jitaccess.core.catalog.EntitlementType;
import com.google.solutions.jitaccess.core.clients.PolicyAnalyzerClient;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Repository that uses the Policy Analyzer API to find entitlements.
 *
 * Entitlements as used by this class are role bindings that
 * are annotated with a special IAM condition (making the binding
 * "eligible").
 */
public class PolicyAnalyzerRepository implements ProjectRoleRepository {
  private final Options options;
  private final PolicyAnalyzerClient policyAnalyzerClient;

  public PolicyAnalyzerRepository(
    PolicyAnalyzerClient policyAnalyzerClient,
    Options options
  ) {
    Preconditions.checkNotNull(policyAnalyzerClient, "assetInventoryClient");
    Preconditions.checkNotNull(options, "options");

    this.policyAnalyzerClient = policyAnalyzerClient;
    this.options = options;
  }

  static List<RoleBinding> findRoleBindings(
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

  //---------------------------------------------------------------------------
  // ProjectRoleRepository.
  //---------------------------------------------------------------------------

  @Override
  public SortedSet<ProjectId> findProjectsWithEntitlements(
    UserId user
  ) throws AccessException, IOException {

    Preconditions.checkNotNull(user, "user");

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
    // - only applies to projects, and has no meaning on descendant resources
    // - represents the lowest level of access to a project.
    //
    var analysisResult = this.policyAnalyzerClient.findAccessibleResourcesByUser(
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
      .map(b -> ProjectId.fromFullResourceName(b.fullResourceName()))
      .collect(Collectors.toCollection(TreeSet::new));
  }

  @Override
  public EntitlementSet<ProjectRoleBinding> findEntitlements(
    UserId user,
    ProjectId projectId,
    EnumSet<EntitlementType> typesToInclude,
    EnumSet<Entitlement.Status> statusesToInclude
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
    // (i.e., the app's service account) has the 'Groups Reader'
    // admin role.
    //

    var analysisResult = this.policyAnalyzerClient.findAccessibleResourcesByUser(
      this.options.scope,
      user,
      Optional.empty(),
      Optional.of(projectId.getFullResourceName()),
      false);

    var allAvailable = new TreeSet<Entitlement<ProjectRoleBinding>>();
    if (statusesToInclude.contains(Entitlement.Status.AVAILABLE)) {

      //
      // Find all self approval eligible role bindings. The bindings are
      // conditional and have a special condition that serves
      // as marker.
      //
      Set<Entitlement<ProjectRoleBinding>> selfApprovalEligible;
      if (typesToInclude.contains(EntitlementType.JIT)) {
        selfApprovalEligible = findRoleBindings(
          analysisResult,
          condition -> JitConstraints.isJitAccessConstraint(condition),
          evalResult -> "CONDITIONAL".equalsIgnoreCase(evalResult))
          .stream()
          .map(binding -> new Entitlement<ProjectRoleBinding>(
            new ProjectRoleBinding(binding),
            binding.role(),
            EntitlementType.JIT,
            Entitlement.Status.AVAILABLE))
          .collect(Collectors.toSet());
      }
      else {
        selfApprovalEligible = Set.of();
      }

      //
      // Find all peer approval eligible role bindings. The bindings are
      // conditional and have a special condition that serves
      // as marker.
      //
      Set<Entitlement<ProjectRoleBinding>> peerApprovalEligible;
      if (typesToInclude.contains(EntitlementType.PEER)) {
        peerApprovalEligible = findRoleBindings(
          analysisResult,
          condition -> JitConstraints.isMultiPartyApprovalConstraint(condition),
          evalResult -> "CONDITIONAL".equalsIgnoreCase(evalResult))
          .stream()
          .map(binding -> new Entitlement<ProjectRoleBinding>(
            new ProjectRoleBinding(binding),
            binding.role(),
            EntitlementType.PEER,
            Entitlement.Status.AVAILABLE))
          .collect(Collectors.toSet());
      }
      else {
        peerApprovalEligible = Set.of();
      }

      //
      // Determine effective set of eligible roles. If a role is both JIT- and
      // peer approval eligible, only retain the JIT-eligible one.
      //
      allAvailable.addAll(selfApprovalEligible);
      allAvailable.addAll(peerApprovalEligible
        .stream()
        .filter(r -> !selfApprovalEligible.stream().anyMatch(a -> a.id().equals(r.id())))
        .collect(Collectors.toList()));
    }

    var allActive = new HashSet<ProjectRoleBinding>();
    if (statusesToInclude.contains(Entitlement.Status.ACTIVE)) {
      //
      // Find role bindings which have already been activated.
      // These bindings have a time condition that we created, and
      // the condition evaluates to true (indicating it's still
      // valid).
      //

      var activeBindings = findRoleBindings(
        analysisResult,
        condition -> JitConstraints.isActivated(condition),
        evalResult -> "TRUE".equalsIgnoreCase(evalResult));

      allActive.addAll(activeBindings
        .stream()
        .map(b -> new ProjectRoleBinding(b))
        .collect(Collectors.toSet()));
    }

    var warnings = Stream.ofNullable(analysisResult.getNonCriticalErrors())
      .flatMap(Collection::stream)
      .map(e -> e.getCause())
      .collect(Collectors.toSet());

    return new EntitlementSet<>(allAvailable, allActive, warnings);
  }

  @Override
  public Set<UserId> findEntitlementHolders(
    ProjectRoleBinding roleBinding,
    EntitlementType entitlementType
  ) throws AccessException, IOException {

    Preconditions.checkNotNull(roleBinding, "roleBinding");
    assert ProjectId.isProjectFullResourceName(roleBinding.roleBinding().fullResourceName());

    var analysisResult = this.policyAnalyzerClient.findPermissionedPrincipalsByResource(
      this.options.scope,
      roleBinding.roleBinding().fullResourceName(),
      roleBinding.roleBinding().role());

    return Stream.ofNullable(analysisResult.getAnalysisResults())
      .flatMap(Collection::stream)

      // Narrow down to IAM bindings with an MPA constraint.
      .filter(result -> result.getIamBinding() != null &&
        JitConstraints.isApprovalConstraint(result.getIamBinding().getCondition(), entitlementType))

      // Collect identities (users and group members)
      .filter(result -> result.getIdentityList() != null)
      .flatMap(result -> result.getIdentityList().getIdentities().stream()
        .filter(id -> id.getName().startsWith("user:"))
        .map(id -> new UserId(id.getName().substring("user:".length()))))

      .collect(Collectors.toCollection(TreeSet::new));
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  /**
   * @param scope Scope to use for queries.
   */
  public record Options(
    String scope) {

    public Options {
      Preconditions.checkNotNull(scope, "scope");
    }
  }
}
