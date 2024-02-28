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
import com.google.api.services.cloudasset.v1.model.IamPolicyAnalysisResult;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.cel.TemporaryIamCondition;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilegeSet;
import com.google.solutions.jitaccess.core.clients.PolicyAnalyzerClient;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Repository that uses the Policy Analyzer API to find privileges.
 *
 * Privileges as used by this class are role bindings that
 * are annotated with a special IAM condition (making the binding
 * "eligible").
 */
public class PolicyAnalyzerRepository extends ProjectRoleRepository {
  private final @NotNull Options options;
  private final @NotNull PolicyAnalyzerClient policyAnalyzerClient;

  public PolicyAnalyzerRepository(
      @NotNull PolicyAnalyzerClient policyAnalyzerClient,
      @NotNull Options options) {
    Preconditions.checkNotNull(policyAnalyzerClient, "assetInventoryClient");
    Preconditions.checkNotNull(options, "options");

    this.policyAnalyzerClient = policyAnalyzerClient;
    this.options = options;
  }

  private record ConditionalRoleBinding(RoleBinding binding, Expr condition) {
  }

  static List<ConditionalRoleBinding> findRoleBindings(
      @NotNull IamPolicyAnalysis analysisResult,
      @NotNull Predicate<Expr> conditionPredicate,
      @NotNull Predicate<String> conditionEvaluationPredicate) {
    Function<IamPolicyAnalysisResult, Expr> getIamCondition = res -> res.getIamBinding() != null
        ? res.getIamBinding().getCondition()
        : null;

    //
    // NB. We don't really care which resource a policy is attached to
    // (indicated by AttachedResourceFullName). Instead, we care about
    // which resources it applies to.
    //
    return Stream.ofNullable(analysisResult.getAnalysisResults())
        .flatMap(Collection::stream)

        // Narrow down to IAM bindings with a specific IAM condition.
        .filter(result -> conditionPredicate.test(getIamCondition.apply(result)))
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
                .map(res -> new ConditionalRoleBinding(
                    new RoleBinding(
                        res.getFullResourceName(),
                        result.getIamBinding().getRole()),
                    getIamCondition.apply(result)))))
        .collect(Collectors.toList());
  }

  // ---------------------------------------------------------------------------
  // ProjectRoleRepository.
  // ---------------------------------------------------------------------------

  @Override
  public @NotNull SortedSet<ProjectId> findProjectsWithRequesterPrivileges(
      UserEmail user) throws AccessException, IOException {

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
            PrivilegeFactory
                .createRequesterPrivilege(
                    new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")),
                    condition)
                .isPresent(),
        evalResult -> evalResult == null ||
            "TRUE".equalsIgnoreCase(evalResult) ||
            "CONDITIONAL".equalsIgnoreCase(evalResult));

    return roleBindings
        .stream()
        .map(b -> ProjectId.fromFullResourceName(b.binding.fullResourceName()))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  @Override
  public @NotNull RequesterPrivilegeSet<ProjectRoleBinding> findRequesterPrivileges(
      UserEmail user,
      @NotNull ProjectId projectId,
      @NotNull Set<ActivationType> typesToInclude,
      @NotNull EnumSet<RequesterPrivilege.Status> statusesToInclude) throws AccessException, IOException {

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

    var allAvailable = new TreeSet<RequesterPrivilege<ProjectRoleBinding>>();
    if (statusesToInclude.contains(RequesterPrivilege.Status.INACTIVE)) {

      allAvailable.addAll(Stream.ofNullable(analysisResult.getAnalysisResults())
          .flatMap(Collection::stream)
          .map(result -> result.getIamBinding())
          .map(binding -> PrivilegeFactory.createRequesterPrivilege(
              new ProjectRoleBinding(new RoleBinding(projectId, binding.getRole())),
              binding.getCondition()))
          .filter(result -> result.isPresent())
          .map(result -> result.get())
          .filter(privilege -> typesToInclude.stream()
              .anyMatch(type -> type.isParentTypeOf(privilege.activationType())))
          .collect(Collectors.toSet()));
    }

    var allActive = new HashSet<ActivatedRequesterPrivilege<ProjectRoleBinding>>();
    if (statusesToInclude.contains(RequesterPrivilege.Status.ACTIVE)) {
      //
      // Find role bindings which have already been activated.
      // These bindings have a time condition that we created, and
      // the condition evaluates to TRUE (indicating it's still
      // valid).
      //

      var activeBindings = findRoleBindings(
          analysisResult,
          condition -> PrivilegeFactory.isActivated(condition),
          evalResult -> "TRUE".equalsIgnoreCase(evalResult));

      allActive.addAll(activeBindings
          .stream()
          .map(conditionalBinding -> new ActivatedRequesterPrivilege<ProjectRoleBinding>(
              new ProjectRoleBinding(conditionalBinding.binding),
              new TemporaryIamCondition(conditionalBinding.condition.getExpression()).getValidity()))
          .collect(Collectors.toSet()));
    }

    var allExpired = new HashSet<ActivatedRequesterPrivilege<ProjectRoleBinding>>();
    if (statusesToInclude.contains(RequesterPrivilege.Status.EXPIRED)) {
      //
      // Do the same for conditions that evaluated to FALSE.
      //

      var activeBindings = findRoleBindings(
          analysisResult,
          condition -> PrivilegeFactory.isActivated(condition),
          evalResult -> "FALSE".equalsIgnoreCase(evalResult));

      allExpired.addAll(activeBindings
          .stream()
          .map(conditionalBinding -> new ActivatedRequesterPrivilege<ProjectRoleBinding>(
              new ProjectRoleBinding(conditionalBinding.binding),
              new TemporaryIamCondition(conditionalBinding.condition.getExpression()).getValidity()))
          .collect(Collectors.toSet()));
    }

    var warnings = Stream.ofNullable(analysisResult.getNonCriticalErrors())
        .flatMap(Collection::stream)
        .map(e -> e.getCause())
        .collect(Collectors.toSet());

    return buildRequesterPrivilegeSet(allAvailable, allActive, allExpired, warnings);
  }

  @Override
  public @NotNull Set<UserEmail> findReviewerPrivelegeHolders(
      @NotNull ProjectRoleBinding roleBinding,
      @NotNull ActivationType activationType) throws AccessException, IOException {

    Preconditions.checkNotNull(roleBinding, "roleBinding");
    assert ProjectId.isProjectFullResourceName(roleBinding.roleBinding().fullResourceName());

    var analysisResult = this.policyAnalyzerClient.findPermissionedPrincipalsByResource(
        this.options.scope,
        roleBinding.roleBinding().fullResourceName(),
        roleBinding.roleBinding().role());

    return Stream.ofNullable(analysisResult.getAnalysisResults())
        .flatMap(Collection::stream)

        // Narrow down to IAM bindings with a privilege that allows reviewing the
        // activation type.
        .filter(result -> result.getIamBinding() != null &&
            PrivilegeFactory.createReviewerPrivilege(roleBinding, result.getIamBinding().getCondition())
                .isPresent())
        .filter(result -> result.getIamBinding() != null &&
            PrivilegeFactory.createReviewerPrivilege(roleBinding, result.getIamBinding().getCondition())
                .get().reviewableTypes().stream().anyMatch(type -> type.isParentTypeOf(activationType)))

        // Collect identities (users and group members)
        .filter(result -> result.getIdentityList() != null)
        .flatMap(result -> result.getIdentityList().getIdentities().stream()
            .filter(id -> id.getName().startsWith("user:"))
            .map(id -> new UserEmail(id.getName().substring("user:".length()))))

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
