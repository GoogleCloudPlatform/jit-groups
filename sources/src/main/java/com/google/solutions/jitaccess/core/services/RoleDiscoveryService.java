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
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.adapters.AssetInventoryAdapter;
import com.google.solutions.jitaccess.core.adapters.UserId;

import javax.enterprise.context.RequestScoped;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service that contains the logic required to find eligible roles, and to activate them.
 */
@RequestScoped
public class RoleDiscoveryService {
  private static final String PROJECT_RESOURCE_NAME_PREFIX =
    "//cloudresourcemanager.googleapis.com/projects/";

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

  protected static boolean isSupportedResource(String fullResourceName) {
    return fullResourceName.startsWith(PROJECT_RESOURCE_NAME_PREFIX)
      && fullResourceName.indexOf('/', PROJECT_RESOURCE_NAME_PREFIX.length()) == -1;
  }

  private static String resourceNameFromFullResourceName(String fullResourceName) {
    return fullResourceName.substring(PROJECT_RESOURCE_NAME_PREFIX.length());
  }

  private List<RoleBinding> findRoleBindings(
    IamPolicyAnalysis analysisResult,
    Predicate<Expr> conditionPredicate,
    Predicate<ConditionEvaluation> conditionEvaluationPredicate,
    RoleBinding.RoleBindingStatus status) {
    //
    // NB. We don't really care which resource a policy is attached to
    // (indicated by AttachedResourceFullName). Instead, we care about
    // which resources it applies to (including descendent resources).
    //
    return Stream.ofNullable(analysisResult.getAnalysisResults())
      .flatMap(Collection::stream)
      // Narrow down to IAM bindings with a specific IAM condition.
      .filter(i -> conditionPredicate.test(i.getIamBinding() != null ? i.getIamBinding().getCondition() : null))
      .flatMap(
        i -> i.getAccessControlLists().stream()
          // Narrow down to ACLs with a specific IAM condition evaluation result.
          .filter(
            acl -> acl.getConditionEvaluation() != null
              && conditionEvaluationPredicate.test(acl.getConditionEvaluation()))

          // Collect all (supported) resources covered by these bindings/ACLs.
          .flatMap(
            acl -> acl.getResources().stream()
              .filter(res -> isSupportedResource(res.getFullResourceName()))
              .map(res -> new RoleBinding(
                resourceNameFromFullResourceName(res.getFullResourceName()),
                res.getFullResourceName(),
                i.getIamBinding().getRole(),
                status))))
      .collect(Collectors.toList());
  }

  public Options getOptions() {
    return options;
  }

  public EligibleRoleBindings listEligibleRoleBindings(UserId user)
    throws AccessException, IOException {
    Preconditions.checkNotNull(user, "user");

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

    var analysisResult =
      this.assetInventoryAdapter.analyzeResourcesAccessibleByUser(
        this.options.getScope(),
        user,
        this.options.isIncludeInheritedBindings());

    //
    // Find role bindings which have already been activated.
    // These bindings have a time condition that we created, and
    // the condition evaluates to true (indicating it's still
    // valid).
    //
    var activatedRoles =
      findRoleBindings(
        analysisResult,
        condition -> JitConstraints.isActivated(condition),
        evalResult -> "TRUE".equalsIgnoreCase(evalResult.getEvaluationValue()),
        RoleBinding.RoleBindingStatus.ACTIVATED);

    //
    // Find all eligible role bindings. The bindings are
    // conditional and have a special condition that serves
    // as marker.
    //
    var eligibleRoles =
      findRoleBindings(
        analysisResult,
        condition -> JitConstraints.isJitAccessConstraint(condition),
        evalResult -> "CONDITIONAL".equalsIgnoreCase(evalResult.getEvaluationValue()),
        RoleBinding.RoleBindingStatus.ELIGIBLE);

    //
    // Merge the two lists.
    //
    // NB. We can't use
    //  !activatedRoles.contains(...)
    // because of the different binding statuses.
    //
    var consolidatedRoles = eligibleRoles.stream()
      .filter(r -> !activatedRoles
        .stream()
        .anyMatch(a -> a.getFullResourceName().equals(r.getFullResourceName())
          && a.getRole().equals(r.getRole())))
      .collect(Collectors.toList());
    consolidatedRoles.addAll(activatedRoles);

    return new EligibleRoleBindings(
      consolidatedRoles.stream()
        .sorted((r1, r2) -> r1.getResourceName().compareTo(r2.getResourceName()))
        .collect(Collectors.toList()),
      Stream.ofNullable(analysisResult.getNonCriticalErrors())
        .flatMap(Collection::stream)
        .map(e -> e.getCause())
        .collect(Collectors.toList()));
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public static class Options {
    private final String scope;
    private final boolean includeInheritedBindings;

    public Options(
      String scope,
      boolean includeInheritedBindings) {
      this.scope = scope;
      this.includeInheritedBindings = includeInheritedBindings;
    }

    /**
     * Scope, can be: - organization/ID - folder/ID - project/ID
     */
    public String getScope() {
      return this.scope;
    }

    /**
     * Search inherited IAM policies
     */
    public boolean isIncludeInheritedBindings() {
      return includeInheritedBindings;
    }
  }
}
