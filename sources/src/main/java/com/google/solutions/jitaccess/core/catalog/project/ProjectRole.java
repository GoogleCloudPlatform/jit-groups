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

import com.google.api.services.cloudasset.v1.model.Binding;
import com.google.api.services.cloudasset.v1.model.Expr;
import com.google.common.base.Strings;
import com.google.solutions.jitaccess.cel.IamCondition;
import com.google.solutions.jitaccess.cel.TemporaryIamCondition;
import com.google.solutions.jitaccess.cel.TimeSpan;
import com.google.solutions.jitaccess.core.catalog.Activation;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.EntitlementId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Identifies a project role binding as an entitlement.
 */
public class ProjectRole extends EntitlementId {
  static final String CATALOG = "iam";

  private final @NotNull ProjectId projectId;
  private final @NotNull String role;

  public ProjectRole(@NotNull ProjectId projectId, @NotNull String role) {
    this.projectId = projectId;
    this.role = role;
  }

  public @NotNull ProjectId projectId() {
    return this.projectId;
  }

  public @NotNull String role() {
    return this.role;
  }

  //---------------------------------------------------------------------------
  // Overrides.
  //---------------------------------------------------------------------------

  @Override
  public @NotNull String catalog() {
    return CATALOG;
  }

  @Override
  public @NotNull String id() {
    return String.format("%s:%s", this.projectId, this.role);
  }

  //---------------------------------------------------------------------------
  // Factory methods.
  //---------------------------------------------------------------------------

  /**
   * Try to create a ProjectRole from a JIT-eligible role binding.
   *
   * @param projectId Project
   * @param binding IAM binding
   * @return ProjectRole or empty if the binding doesn't represent a
   *         JIT-eligible role binding.
   */
  static Optional<ProjectRole> fromJitEligibleRoleBinding(
    @NotNull ProjectId projectId,
    @NotNull Binding binding
  ) {
    return EligibilityCondition.parse(binding.getCondition())
      .filter(EligibilityCondition::isJitEligible)
      .map(c -> new ProjectRole(projectId, binding.getRole()));
  }

  /**
   * Try to create a ProjectRole from an MPA-eligible role binding.
   *
   * @param projectId Project
   * @param binding IAM binding
   * @return ProjectRole or empty if the binding doesn't represent an
   *         MPA-eligible role binding.
   */
  static Optional<ProjectRole> fromMpaEligibleRoleBinding(
    @NotNull ProjectId projectId,
    @NotNull Binding binding
  ) {
    return EligibilityCondition.parse(binding.getCondition())
      .filter(EligibilityCondition::isMpaEligible)
      .map(c -> new ProjectRole(projectId, binding.getRole()));
  }

  /**
   * Try to create a ProjectRole from an activation binding.
   *
   * @param projectId Project
   * @param binding IAM binding
   * @return ProjectRole or empty if the binding doesn't represent an
   *         activation binding.
   */
  static Optional<ActivatedProjectRole> fromActivationRoleBinding(
    @NotNull ProjectId projectId,
    @NotNull Binding binding
  ) {
    return ActivationCondition.parse(binding.getCondition())
      .map(c -> new ActivatedProjectRole(
        new ProjectRole(projectId, binding.getRole()),
        c.toActivation()));
  }

  record ActivatedProjectRole(
    @NotNull ProjectRole projectRole,
    @NotNull Activation activation) {}


  //---------------------------------------------------------------------------
  // Conditions.
  //---------------------------------------------------------------------------

  static abstract class Condition extends IamCondition {
    protected Condition(@NotNull String expression) {
      super(expression);
    }

    protected static boolean matches(
      @Nullable String expression,
      @NotNull Pattern pattern
    ) {
      if (Strings.isNullOrEmpty(expression)) {
        return false;
      }

      // Strip all whitespace to simplify expression matching.
      expression = expression
        .toLowerCase()
        .replace(" ", "");

      return pattern.matcher(expression).matches();
    }
  }

  /**
   * Condition that marks a role binding as eligible.
   */
  static class EligibilityCondition extends Condition {
    /** Condition that marks a role binding as eligible for JIT access */
    private static final Pattern JIT_CONDITION_PATTERN = Pattern
      .compile("^\\s*has\\(\\s*\\{\\s*\\}.jitaccessconstraint\\s*\\)\\s*$");

    /** Condition that marks a role binding as eligible for MPA */
    private static final Pattern MPA_CONDITION_PATTERN = Pattern
      .compile("^\\s*has\\(\\s*\\{\\s*\\}.multipartyapprovalconstraint\\s*\\)\\s*$");

    private final ActivationType activationType;

    private EligibilityCondition(
      @NotNull String expression,
      ActivationType activationType
    ) {
      super(expression);
      this.activationType = activationType;
    }

    public ActivationType activationType() {
      return this.activationType;
    }

    public boolean isJitEligible() {
      return this.activationType == ActivationType.JIT;
    }

    public boolean isMpaEligible() {
      return this.activationType == ActivationType.MPA;
    }

    /**
     * Try to parse condition
     *
     * @return condition or empty if the expression doesn't represent an eligibility condition
     */
    static Optional<EligibilityCondition> parse(String expression) {
      if (matches(expression, JIT_CONDITION_PATTERN)) {
        return Optional.of(new EligibilityCondition(expression, ActivationType.JIT));
      }
      else if (matches(expression, MPA_CONDITION_PATTERN)) {
        return Optional.of(new EligibilityCondition(expression, ActivationType.MPA));
      }
      else {
        return Optional.empty();
      }
    }

    /**
     * Try to parse condition
     *
     * @return condition or empty if the expression doesn't represent an eligibility condition
     */
    static Optional<EligibilityCondition> parse(@Nullable Expr condition) {
      if (condition == null || condition.getExpression() == null) {
        return Optional.empty();
      }
      else {
        return parse(condition.getExpression());
      }
    }
  }

  /**
   * Condition that marks a role binding as activated.
   */
  static class ActivationCondition extends Condition {
    /** Condition title for activated role bindings */
    public static final String TITLE = "JIT access activation";

    private final @NotNull TimeSpan validity;

    private ActivationCondition(@NotNull String expression, @NotNull TimeSpan validity) {
      super(expression);
      this.validity = validity;
    }

    public Activation toActivation() {
      return new Activation(this.validity);
    }

    /**
     * Try to parse condition
     *
     * @return condition or empty if the expression doesn't represent an activation condition
     */
    static Optional<ActivationCondition> parse(@Nullable Expr condition) {
      if (condition != null &&
        TITLE.equals(condition.getTitle()) &&
        TemporaryIamCondition.isTemporaryAccessCondition(condition.getExpression())) {

        return Optional.of(new ActivationCondition(
          condition.getExpression(),
          new TemporaryIamCondition(condition.getExpression()).getValidity()));
      }
      else {
        return Optional.empty();
      }
    }
  }
}
