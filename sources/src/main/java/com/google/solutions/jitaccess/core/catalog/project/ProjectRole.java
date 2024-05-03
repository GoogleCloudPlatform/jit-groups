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
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.catalog.EntitlementId;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies a project role binding as an entitlement.
 */
public class ProjectRole extends EntitlementId {
  static final String CATALOG = "iam";

  private final @NotNull RoleBinding roleBinding;

  public ProjectRole(@NotNull RoleBinding roleBinding) {
    Preconditions.checkNotNull(roleBinding, "roleBinding");

    assert ProjectId.canParse(roleBinding.fullResourceName());

    this.roleBinding = roleBinding;
  }

  public @NotNull RoleBinding roleBinding() {
    return this.roleBinding;
  }

  /**
   * Check if the binding represents a JIT-eligible project role.
   */
  public static boolean isJitEligibleProjectRole(Binding binding) {
    return JitConstraints.isJitAccessConstraint(binding.getCondition());
  }

  /**
   * Check if the binding represents a MPA-eligible project role.
   */
  public static boolean isMpaEligibleProjectRole(Binding binding) {
    return JitConstraints.isMultiPartyApprovalConstraint(binding.getCondition());
  }

  /**
   * Check if the binding represents an activated project role.
   */
  public static boolean isActivatedProjectRole(Binding binding) {
    return JitConstraints.isActivated(binding.getCondition());
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
    return this.roleBinding.toString();
  }

  public @NotNull ProjectId projectId() {
    return ProjectId.parse(this.roleBinding.fullResourceName());
  }
}
