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
import com.google.solutions.jitaccess.core.catalog.EntitlementId;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.util.Base64Escape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Identifies a project role binding as an entitlement.
 */
public class ProjectRole extends EntitlementId {
  static final String CATALOG = "iam";

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
  // Overrides.
  //---------------------------------------------------------------------------

  @Override
  public @NotNull String catalog() {
    return CATALOG;
  }

  @Override
  public @NotNull String id() {
    if (this.resourceCondition != null) {
      //
      // Include resource condition in ID so that we can distinguish
      // it from another entitlement for the same role that might
      // have no (or a different) resource condition.
      //
      // NB. The base64 escaping is just to prevent encoding issues
      // because the condition might contain newlines, braces, and
      // other special characters.
      //
      return String.format(
        "%s:%s:%s",
        this.projectId,
        this.role,
        Base64Escape.escape(this.resourceCondition));
    }
    else {
      return String.format("%s:%s", this.projectId, this.role);
    }
  }

  @Override
  public String toString() {
    if (this.resourceCondition != null) {
      return String.format(
        "[%s] %s on %s (condition: %s)",
        this.catalog(),
        this.role,
        this.projectId,
        this.resourceCondition);
    }
    else {
      return String.format(
        "[%s] %s on %s",
        this.catalog(),
        this.role,
        this.projectId);
    }
  }

  //---------------------------------------------------------------------------
  // Factory methods.
  //---------------------------------------------------------------------------

  /**
   * Try to create a ProjectRole from its ID.
   */
  public static ProjectRole fromId(@NotNull String id) {
    var parts = id.split(":");
    if (parts.length < 2 ||
      parts.length > 3 ||
      parts[0].isBlank() ||
      parts[1].isBlank()) {
      throw new IllegalArgumentException("Invalid ProjectRole ID");
    }
    else if (parts.length == 3) {
      return new ProjectRole(
        new ProjectId(parts[0]),
        parts[1],
        Base64Escape.unescape(parts[2]));
    }
    else {
      return new ProjectRole(
        new ProjectId(parts[0]),
        parts[1]);
    }
  }

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
        new ProjectRole(projectId, binding.getRole(), c.resourceCondition()),
        c.toActivation()));
  }

  record ActivatedProjectRole(
    @NotNull ProjectRole projectRole,
    @NotNull Activation activation) {}


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
      var resourceCondition = resourceConditionClauses.isEmpty()
        ? null
        : IamCondition.and(resourceConditionClauses).reformat().toString();

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

  /**
   * Condition that marks a role binding as activated.
   */
  static class ActivationCondition extends Condition {
    /** Condition title for activated role bindings */
    public static final String TITLE = "JIT access activation";

    private final @NotNull TimeSpan validity;

    private ActivationCondition(
      @NotNull String expression,
      @NotNull TimeSpan validity,
      @Nullable String resourceCondition) {
      super(expression, resourceCondition);
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
    static Optional<ActivationCondition> parse(@Nullable Expr bindingCondition) {
      if (bindingCondition == null ||
        Strings.isNullOrEmpty(bindingCondition.getExpression()) ||
        bindingCondition.getExpression().isBlank() ||
        !TITLE.equals(bindingCondition.getTitle())) {
        return Optional.empty();
      }

      //
      // Break the condition into clauses. One of them should be a temporary condition,
      // any remaining clauses make up the resource condition.
      //
      var clauses = new IamCondition(bindingCondition.getExpression()).splitAnd();
      var temporaryCondition = clauses
        .stream()
        .filter(c -> TemporaryIamCondition.isTemporaryAccessCondition(c.toString()))
        .findFirst();
      if (!temporaryCondition.isPresent()) {
        return Optional.empty();
      }

      var resourceConditionClauses = clauses
        .stream()
        .filter(c -> !TemporaryIamCondition.isTemporaryAccessCondition(c.toString()))
        .collect(Collectors.toList());
      var resourceCondition = resourceConditionClauses.isEmpty()
        ? null
        : IamCondition.and(resourceConditionClauses).reformat().toString();

      return Optional.of(new ActivationCondition(
        bindingCondition.getExpression(),
        new TemporaryIamCondition(temporaryCondition.get().toString()).getValidity(),
        resourceCondition));
    }
  }
}
