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

import com.google.cloud.asset.v1.AnalyzeIamPolicyResponse;
import com.google.cloud.asset.v1.ConditionEvaluation;
import com.google.cloud.resourcemanager.v3.ProjectName;
import com.google.common.base.Preconditions;
import com.google.iam.v1.Binding;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AlreadyExistsException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.adapters.AssetInventoryAdapter;
import com.google.solutions.jitaccess.core.adapters.IamConditions;
import com.google.solutions.jitaccess.core.adapters.ResourceManagerAdapter;
import com.google.solutions.jitaccess.core.adapters.UserId;
import com.google.type.Expr;

import javax.enterprise.context.RequestScoped;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Service that contains the logic required to find eligible roles, and to activate them. */
@RequestScoped
public class ElevationService {
  private static final String PROJECT_RESOURCE_NAME_PREFIX =
      "//cloudresourcemanager.googleapis.com/projects/";
  public static final String ELEVATION_CONDITION_TITLE = "Temporary elevation";
  private static final Pattern ELEVATION_CONDITION_PATTERN =
      Pattern.compile("^\\s*resource\\.service\\s*==\\s*['\"](.*)['\"]\\s*$");

  private final AssetInventoryAdapter assetInventoryAdapter;
  private final ResourceManagerAdapter resourceManagerAdapter;

  private final Options options;

  public ElevationService(
      AssetInventoryAdapter assetInventoryAdapter,
      ResourceManagerAdapter resourceManagerAdapter,
      Options configuration) {
    Preconditions.checkNotNull(assetInventoryAdapter, "assetInventoryAdapter");
    Preconditions.checkNotNull(resourceManagerAdapter, "resourceManagerAdapter");
    Preconditions.checkNotNull(configuration, "configuration");

    this.assetInventoryAdapter = assetInventoryAdapter;
    this.resourceManagerAdapter = resourceManagerAdapter;
    this.options = configuration;
  }

  public boolean isConditionIndicatorForEligibility(Expr iamCondition) {
    if (iamCondition == null) {
      return false;
    }

    var match = ELEVATION_CONDITION_PATTERN.matcher(iamCondition.getExpression());
    return match.find() && match.group(1).equals(this.options.getEligibilityServiceName());
  }

  private boolean isSupportedResource(String fullResourceName) {
    return fullResourceName.startsWith(PROJECT_RESOURCE_NAME_PREFIX)
        && fullResourceName.indexOf('/', PROJECT_RESOURCE_NAME_PREFIX.length()) == -1;
  }

  private String resourceNameFromFullResourceName(String fullResourceName) {
    return fullResourceName.substring(PROJECT_RESOURCE_NAME_PREFIX.length());
  }

  private List<RoleBinding> findRoleBindings(
      AnalyzeIamPolicyResponse.IamPolicyAnalysis analysisResult,
      Predicate<Expr> conditionPredicate,
      Predicate<ConditionEvaluation> conditionEvaluationPredicate,
      RoleBinding.RoleBindingStatus status) {
    //
    // NB. We don't really care which resource a policy is attached to
    // (indicated by AttachedResourceFullName). Instead, we care about
    // which resources it applies to (including descendent resources).
    //
    return analysisResult.getAnalysisResultsList().stream()
        // Narrow down to IAM bindings with a specific IAM condition.
        .filter(i -> conditionPredicate.test(i.getIamBinding().getCondition()))
        .flatMap(
            i -> i.getAccessControlListsList().stream()
                // Narrow down to ACLs with a specific IAM condition evaluation result.
                .filter(
                    acl -> acl.getConditionEvaluation() != null
                            && conditionEvaluationPredicate.test(acl.getConditionEvaluation()))

                // Collect all (supported) resources covered by these bindings/ACLs.
                .flatMap(
                    acl -> acl.getResourcesList().stream()
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
    // (i.e., the Cloud Run service account) has the 'Groups Reader'
    // admin role.
    //

    var analysisResult =
        this.assetInventoryAdapter.analyzeResourcesAccessibleByUser(
            this.options.getScope(), user, true); // Expand resources.

    //
    // Find role bindings which have already been activated.
    // These bindings have a time condition that we created, and
    // the condition evaluates to true (indicating it's still
    // valid).
    //
    var activatedRoles =
        findRoleBindings(
            analysisResult,
            condition -> condition.getTitle().equals(ELEVATION_CONDITION_TITLE),
            evalResult ->
                evalResult.getEvaluationValue() == ConditionEvaluation.EvaluationValue.TRUE,
            RoleBinding.RoleBindingStatus.ACTIVATED);

    //
    // Find all eligible role bindings. The bindings are
    // conditional and have a special condition that serves
    // as marker.
    //
    var eligibleRoles =
        findRoleBindings(
            analysisResult,
            expr -> isConditionIndicatorForEligibility(expr),
            evalResult ->
                evalResult.getEvaluationValue() == ConditionEvaluation.EvaluationValue.CONDITIONAL,
            RoleBinding.RoleBindingStatus.ELIGIBLE);

    //
    // Merge the two lists.
    //
    // NB. We can't use
    //  !activatedRoles.contains(...)
    // because of the different binding statuses.
    //
    var consolidatedRoles =
        eligibleRoles.stream()
            .filter(r -> !activatedRoles.stream()
                  .anyMatch(a -> a.getFullResourceName().equals(r.getFullResourceName())
                                 && a.getRole().equals(r.getRole())))
            .collect(Collectors.toList());
    consolidatedRoles.addAll(activatedRoles);

    return new EligibleRoleBindings(
        consolidatedRoles.stream()
            .sorted((r1, r2) -> r1.getResourceName().compareTo(r2.getResourceName()))
            .collect(Collectors.toList()),
        analysisResult.getNonCriticalErrorsList().stream()
            .map(e -> e.getCause())
            .collect(Collectors.toList()));
  }

  public OffsetDateTime activateEligibleRoleBinding(
      UserId userId,
      RoleBinding role,
      String justification)
      throws AccessException, AlreadyExistsException, IOException {
    Preconditions.checkNotNull(userId, "userId");
    Preconditions.checkNotNull(role, "role");
    Preconditions.checkNotNull(justification, "justification");

    assert (isSupportedResource(role.getFullResourceName()));

    //
    // Double-check that the user is really allowed to activate
    // this role. This is to avoid us from being tricked to grant
    // access to a role that they aren't eligible for.
    //
    var eligibleRoles = listEligibleRoleBindings(userId);
    if (!eligibleRoles.getRoleBindings().contains(role)) {
      throw new AccessDeniedException(
          String.format("Your user %s is not eligible to activate this role", userId));
    }

    if (!this.options.getJustificationPattern().matcher(justification).matches()) {
      throw new AccessDeniedException(
          String.format(
              "Justification does not meet criteria: %s", this.options.getJustificationHint()));
    }

    //
    // Add time-bound IAM binding.
    //
    // Replace existing bindings for same user and role to avoid
    // accumulating junk, and to prevent hitting the binding limit.
    //
    var elevationStartTime = OffsetDateTime.now();
    var elevationEndTime = elevationStartTime.plus(this.options.getActivationDuration());

    this.resourceManagerAdapter.addIamBinding(
        ProjectName.of(role.getResourceName()),
        Binding.newBuilder()
            .addMembers("user:" + userId)
            .setRole(role.getRole())
            .setCondition(
                Expr.newBuilder()
                    .setTitle(ELEVATION_CONDITION_TITLE)
                    .setDescription("User-provided justification: " + justification)
                    .setExpression(
                        IamConditions.createTemporaryConditionClause(
                            elevationStartTime, elevationEndTime))
                    .build())
            .build(),
        EnumSet.of(
            ResourceManagerAdapter.IamBindingOptions.REPLACE_BINDINGS_FOR_SAME_PRINCIPAL_AND_ROLE),
        justification);

    return elevationEndTime;
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public static class Options {
    private final String scope;
    private final String eligibilityServiceName;
    private final Duration activationDuration;
    private final String justificationHint;
    private final Pattern justificationPattern;

    public Options(
        String scope,
        String eligibilityServiceName,
        String justificationHint,
        Pattern justificationPattern,
        Duration activationDuration) {
      this.scope = scope;
      this.eligibilityServiceName = eligibilityServiceName;
      this.activationDuration = activationDuration;
      this.justificationHint = justificationHint;
      this.justificationPattern = justificationPattern;
    }

    /** Scope, can be: - organization/ID - folder/ID - project/ID */
    public String getScope() {
      return this.scope;
    }

    /** Resource name that is used in IAM conditions to indicate eligibility. */
    public String getEligibilityServiceName() {
      return this.eligibilityServiceName;
    }

    /** Duration for an elevation. */
    public Duration getActivationDuration() {
      return this.activationDuration;
    }

    /** Hint for justification pattern. */
    public String getJustificationHint() {
      return this.justificationHint;
    }

    /** Pattern to validate justifications. */
    public Pattern getJustificationPattern() {
      return this.justificationPattern;
    }
  }
}
