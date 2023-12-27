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
import com.google.solutions.jitaccess.core.Annotated;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.Entitlement;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.clients.AssetInventoryClient;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper class for performing Policy Analyzer searches.
 */
@ApplicationScoped
public class PolicyAnalyzer {
  private final Options options;
  private final AssetInventoryClient assetInventoryClient;

  public PolicyAnalyzer(
    AssetInventoryClient assetInventoryClient,
    Options options
  ) {
    Preconditions.checkNotNull(assetInventoryClient, "assetInventoryClient");
    Preconditions.checkNotNull(options, "options");

    this.assetInventoryClient = assetInventoryClient;
    this.options = options;
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

  //---------------------------------------------------------------------------
  // Publics.
  //---------------------------------------------------------------------------

  /**
   * Find projects that a user has standing, JIT-, or MPA-eligible access to.
   */
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
    var analysisResult = this.assetInventoryClient.findAccessibleResourcesByUser(
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

  /**
   * List entitlements for the given user.
   */
  public Annotated<SortedSet<Entitlement<ProjectRoleBinding>>> findEntitlements(
    UserId user,
    ProjectId projectId,
    EnumSet<ActivationType> typesToInclude,
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

    var analysisResult = this.assetInventoryClient.findAccessibleResourcesByUser(
      this.options.scope,
      user,
      Optional.empty(),
      Optional.of(projectId.getFullResourceName()),
      false);

    var warnings = Stream.ofNullable(analysisResult.getNonCriticalErrors())
      .flatMap(Collection::stream)
      .map(e -> e.getCause())
      .collect(Collectors.toSet());

    var allAvailable = new TreeSet<Entitlement<ProjectRoleBinding>>();
    if (statusesToInclude.contains(Entitlement.Status.AVAILABLE)) {

      //
      // Find all JIT-eligible role bindings. The bindings are
      // conditional and have a special condition that serves
      // as marker.
      //
      Set<Entitlement<ProjectRoleBinding>> jitEligible;
      if (typesToInclude.contains(ActivationType.JIT)) {
        jitEligible = findRoleBindings(
          analysisResult,
          condition -> JitConstraints.isJitAccessConstraint(condition),
          evalResult -> "CONDITIONAL".equalsIgnoreCase(evalResult))
          .stream()
          .map(binding -> new Entitlement<ProjectRoleBinding>(
            new ProjectRoleBinding(binding),
            binding.role(),
            ActivationType.JIT,
            Entitlement.Status.AVAILABLE))
          .collect(Collectors.toSet());
      }
      else {
        jitEligible = Set.of();
      }

      //
      // Find all MPA-eligible role bindings. The bindings are
      // conditional and have a special condition that serves
      // as marker.
      //
      Set<Entitlement<ProjectRoleBinding>> mpaEligible;
      if (typesToInclude.contains(ActivationType.MPA)) {
        mpaEligible = findRoleBindings(
          analysisResult,
          condition -> JitConstraints.isMultiPartyApprovalConstraint(condition),
          evalResult -> "CONDITIONAL".equalsIgnoreCase(evalResult))
          .stream()
          .map(binding -> new Entitlement<ProjectRoleBinding>(
            new ProjectRoleBinding(binding),
            binding.role(),
            ActivationType.MPA,
            Entitlement.Status.AVAILABLE))
          .collect(Collectors.toSet());
      }
      else {
        mpaEligible = Set.of();
      }

      //
      // Determine effective set of eligible roles. If a role is both JIT- and
      // MPA-eligible, only retain the JIT-eligible one.
      //
      // Use a list so that JIT-eligible roles go first, followed by MPA-eligible ones.
      //
      allAvailable.addAll(jitEligible);
      allAvailable.addAll(mpaEligible
        .stream()
        .filter(r -> !jitEligible.stream().anyMatch(a -> a.id().equals(r.id())))
        .collect(Collectors.toList()));
    }

    var allActive = new TreeSet<Entitlement<ProjectRoleBinding>>();
    if (statusesToInclude.contains(Entitlement.Status.ACTIVE)) {
      //
      // Find role bindings which have already been activated.
      // These bindings have a time condition that we created, and
      // the condition evaluates to true (indicating it's still
      // valid).
      //

      for (var activeBinding : findRoleBindings(
        analysisResult,
        condition -> JitConstraints.isActivated(condition),
        evalResult -> "TRUE".equalsIgnoreCase(evalResult))) {
        //
        // Find the corresponding eligible binding to determine
        // whether this is JIT or MPA-eligible.
        //
        var correspondingEligibleBinding = allAvailable
          .stream()
          .filter(ent -> ent.id().roleBinding().equals(activeBinding))
          .findFirst();
        if (correspondingEligibleBinding.isPresent()) {
          allActive.add(new Entitlement<>(
            new ProjectRoleBinding(activeBinding),
            activeBinding.role(),
            correspondingEligibleBinding.get().activationType(),
            Entitlement.Status.ACTIVE));
        }
        else {
          //
          // Active, but no longer eligible.
          //
          allActive.add(new Entitlement<>(
            new ProjectRoleBinding(activeBinding),
            activeBinding.role(),
            ActivationType.NONE,
            Entitlement.Status.ACTIVE));
        }
      }
    }

    //
    // Replace roles that have been activated already.
    //
    var availableAndActive = allAvailable
      .stream()
      .filter(r -> !allActive.stream().anyMatch(a -> a.id().equals(r.id())))
      .collect(Collectors.toCollection(TreeSet::new));
    availableAndActive.addAll(allActive);

    return new Annotated<>(availableAndActive, warnings);
  }

  /**
    * List users that can approve the activation of an eligible role binding.
    */
  public Set<UserId> findApproversForEntitlement(
    RoleBinding roleBinding
  ) throws AccessException, IOException {

    Preconditions.checkNotNull(roleBinding, "roleBinding");
    assert ProjectId.isProjectFullResourceName(roleBinding.fullResourceName());

    var analysisResult = this.assetInventoryClient.findPermissionedPrincipalsByResource(
      this.options.scope,
      roleBinding.fullResourceName(),
      roleBinding.role());

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
