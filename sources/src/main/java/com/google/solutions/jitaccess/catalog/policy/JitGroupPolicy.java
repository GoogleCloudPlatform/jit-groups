//
// Copyright 2024 Google LLC
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

package com.google.solutions.jitaccess.catalog.policy;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.auth.JitGroupId;
import com.google.solutions.jitaccess.auth.Subject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Policy for a JIT group.
 */
public class JitGroupPolicy extends AbstractPolicy {
  static final String NAME_PATTERN = "^[a-zA-Z0-9\\-]+$";
  static final int NAME_MAX_LENGTH = 24;
  private final @NotNull List<Privilege> privileges;

  protected JitGroupPolicy(
    @NotNull String name,
    @NotNull String description,
    @Nullable AccessControlList acl,
    @NotNull Map<ConstraintClass, Collection<Constraint>> constraints,
    @NotNull List<Privilege> privileges,
    int maxNameLength
  ) {
    super(name, description, acl, constraints);

    Preconditions.checkNotNull(name, "Name must not be null");
    Preconditions.checkArgument(
      name.matches(NAME_PATTERN),
      "JIT group names must consist of letters, numbers, and hyphens");
    Preconditions.checkArgument(
      name.length() <= maxNameLength,
      "JIT group names must not exceed " + maxNameLength + " characters");
    Preconditions.checkNotNull(privileges, "Privileges must not be null");

    this.privileges = privileges;
  }

  public JitGroupPolicy(
    @NotNull String name,
    @NotNull String description,
    @Nullable AccessControlList acl,
    @NotNull Map<ConstraintClass, Collection<Constraint>> constraints,
    @NotNull List<Privilege> privileges
  ) {
    this(name, description, acl, constraints, privileges, NAME_MAX_LENGTH);
  }

  public JitGroupPolicy(
    @NotNull String name,
    @NotNull String description,
    @Nullable AccessControlList acl
  ) {
    this(name, description, acl, Map.of(), List.of());
  }

  public JitGroupPolicy(
    @NotNull String name,
    @NotNull String description
  ) {
    this(name, description, null, Map.of(), List.of());
  }

  /**
   * Get the unique ID of the group.
   */
  public @NotNull JitGroupId id() {
    return new JitGroupId(
      this.system().environment().name(),
      this.system().name(),
      this.name());
  }

  /**
   * Get the parent policy.
   */
  public @NotNull SystemPolicy system() {
    Preconditions.checkNotNull(this.parent().isPresent(), "Parent must be set");
    return (SystemPolicy)this.parent().get();
  }

  /**
   * Get the list of privileges that a membership of this group grants.
   */
  public @NotNull Collection<Privilege> privileges() {
    return this.privileges;
  }

  /**
   * Analyze access for a subject.
   */
  public @NotNull PolicyAnalysis analyze(
    @NotNull Subject subject,
    @NotNull EnumSet<PolicyPermission> requiredRights
  ) {
    return new PolicyAnalysis(
      this,
      subject,
      id(),
      requiredRights);
  }
}
