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

package com.google.solutions.jitaccess.catalog.legacy;

import com.google.api.services.cloudasset.v1.model.Binding;
import com.google.api.services.cloudasset.v1.model.Expr;
import com.google.common.base.Strings;
import com.google.solutions.jitaccess.apis.ProjectId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Identifies a project role binding as an entitlement.
 */
class ProjectRole {
  private final @NotNull ProjectId projectId;
  private final @NotNull String role;
  private final @Nullable String resourceCondition;

  public ProjectRole(
    @NotNull ProjectId projectId,
    @NotNull String role,
    @Nullable String resourceCondition
  ) {
    this.projectId = projectId;
    this.role = role;
    this.resourceCondition = Strings.emptyToNull(resourceCondition);
  }

  public ProjectRole(@NotNull ProjectId projectId, @NotNull String role) {
    this(projectId, role, null);
  }

  /**
   * @return Project that the role grants access to.
   */
  public @NotNull ProjectId projectId() {
    return this.projectId;
  }

  /**
   * @return Predefined or custom role (roles/*)
   */
  public @NotNull String role() {
    return this.role;
  }

  /**
   * @return CEL condition that constrains the set of resources
   * that this project role grants access to.
   */
  public @Nullable String resourceCondition() {
    return this.resourceCondition;
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
      .map(c -> new ProjectRole(projectId, binding.getRole(), c.resourceCondition()));
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
      .map(c -> new ProjectRole(projectId, binding.getRole(), c.resourceCondition()));
  }

  //---------------------------------------------------------------------------
  // Conditions.
  //---------------------------------------------------------------------------

  static abstract class Condition extends IamCondition {
    private final @Nullable String resourceCondition;

    protected Condition(
      @NotNull String expression,
      @Nullable String resourceCondition) {
      super(expression);
      this.resourceCondition = resourceCondition;
    }

    public @Nullable String resourceCondition() {
      return this.resourceCondition;
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
      ActivationType activationType,
      @Nullable String resourceCondition
    ) {
      super(expression, resourceCondition);
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
    static Optional<EligibilityCondition> parse(@Nullable Expr bindingCondition) {
      if (bindingCondition == null ||
        Strings.isNullOrEmpty(bindingCondition.getExpression()) ||
        bindingCondition.getExpression().isBlank()) {
        return Optional.empty();
      }

      //
      // Break the condition into clauses and check if one the clauses
      // marks this as an eligible role.
      //
      // Any remaining clauses make up the resource condition.
      //
      var clauses = new IamCondition(bindingCondition.getExpression()).splitAnd();
      var jitEligible = clauses
        .stream()
        .anyMatch(c -> matches(c.toString(), JIT_CONDITION_PATTERN));
      var mpaEligible = clauses
        .stream()
        .anyMatch(c -> matches(c.toString(), MPA_CONDITION_PATTERN));
      var resourceConditionClauses = clauses
        .stream()
        .filter(c -> !matches(c.toString(), JIT_CONDITION_PATTERN))
        .filter(c -> !matches(c.toString(), MPA_CONDITION_PATTERN))
        .collect(Collectors.toList());

      String resourceCondition;
      try {
        resourceCondition = resourceConditionClauses.isEmpty()
          ? null
          : IamCondition.and(resourceConditionClauses).reformat().toString();
      }
      catch (IllegalArgumentException invalidCel) {
        return Optional.empty();
      }

      if (jitEligible) {
        return Optional.of(new EligibilityCondition(
          bindingCondition.getExpression(),
          ActivationType.JIT,
          resourceCondition));
      }
      else if (mpaEligible) {
        return Optional.of(new EligibilityCondition(
          bindingCondition.getExpression(),
          ActivationType.MPA,
          resourceCondition));
      }
      else {
        return Optional.empty();
      }
    }
  }

  enum ActivationType {
    /** Entitlement can be activated using self-approval */
    JIT,

    /** Entitlement can be activated using multi-party approval.  */
    MPA,
  }
}