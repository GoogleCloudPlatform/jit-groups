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
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.EntitlementId;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.html.Option;
import java.util.Optional;

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
  public @NotNull String id() { //TODO: test, consider condition
    return String.format("%s:%s", this.projectId, this.role);
  }

  //---------------------------------------------------------------------------
  // Factory methods.
  //---------------------------------------------------------------------------

  /**
   * Try to create a ProjectRole from a JIT-eligible role binding.
   *
   * @param binding
   * @return ProjectRole or empty if the binding doesn't represent a
   *         JIT-eligible role binding.
   */
  public static Optional<ProjectRole> fromJitEligibleRoleBinding( // TODO: test
    @NotNull ProjectId projectId,
    @NotNull Binding binding
  ) {
    if (JitConstraints.isJitAccessConstraint(binding.getCondition())) {
      return Optional.of(new ProjectRole(projectId, binding.getRole()));
    }
    else {
      return Optional.empty();
    }
  }

  /**
   * Try to create a ProjectRole from an MPA-eligible role binding.
   *
   * @param binding
   * @return ProjectRole or empty if the binding doesn't represent an
   *         MPA-eligible role binding.
   */
  public static Optional<ProjectRole> fromMpaEligibleRoleBinding(
    @NotNull ProjectId projectId,
    @NotNull Binding binding
  ) {
    if (JitConstraints.isMultiPartyApprovalConstraint(binding.getCondition())) {
      return Optional.of(new ProjectRole(projectId, binding.getRole()));
    }
    else {
      return Optional.empty();
    }
  }

  /**
   * Try to create a ProjectRole from an activation binding.
   *
   * @param binding
   * @return ProjectRole or empty if the binding doesn't represent an
   *         activation binding.
   */
  public static Optional<ProjectRole> fromActivationRoleBinding(
    @NotNull ProjectId projectId,
    @NotNull Binding binding
  ) {
    if (JitConstraints.isActivated(binding.getCondition())) {
      return Optional.of(new ProjectRole(projectId, binding.getRole()));
    }
    else {
      return Optional.empty();
    }
  }
}
