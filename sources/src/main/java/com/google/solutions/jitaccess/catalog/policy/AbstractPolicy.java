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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base implementation for a policy.
 */
abstract class AbstractPolicy implements Policy {
  private final @NotNull String name;
  private final @NotNull String displayName;
  private final @NotNull String description;
  private @Nullable Policy parent;
  private final @Nullable AccessControlList acl;
  private final @NotNull Map<ConstraintClass, Collection<Constraint>> constraints;

  protected AbstractPolicy(
    @NotNull String name,
    @NotNull String description,
    @Nullable AccessControlList acl,
    @NotNull Map<ConstraintClass, Collection<Constraint>> constraints
  ) {
    Preconditions.checkArgument(
      name != null && !name.isBlank(),
      "The policy must have a name");
    this.name = name.toLowerCase();
    this.displayName = name;
    this.description = description;
    this.acl = acl;
    this.constraints = constraints;
  }

  /**
   * Name of policy.
   */
  @NotNull
  @Override
  public String name() {
    return name;
  }

  /**
   * Display name of policy.
   */
  @NotNull
  @Override
  public String displayName() {
    return displayName;
  }

  /**
   * Description of the policy, for informational purposes only.
   */
  @NotNull
  @Override
  public String description() {
    return description;
  }

  /**
   * Parent policy, if any.
   *
   * If a policy has a parent, the parent's ACL and constraints
   * are inherited, with the current policy taking precedence.
   */
  @Override
  public @NotNull Optional<Policy> parent() {
    return Optional.ofNullable(this.parent);
  }

  /**
   * Access control list.
   *
   * Subjects aren't allowed to view or use the policy unless
   * the ACL (or one of its ancestors' ACLs) grants them access.
   */
  @Override
  public @NotNull Optional<AccessControlList> accessControlList() {
    return Optional.ofNullable(acl);
  }

  /**
   * Metadata describing the source of this policy.
   */
  @Override
  public @NotNull Metadata metadata() {
    Preconditions.checkState(
      this.parent != null,
      "A policy must have a parent or provide metadata");
    return this.parent.metadata();
  }

  /**
   * Raw map of constraints.
   */
  Map<ConstraintClass, Collection<Constraint>> constraints() {
    return constraints;
  }

  /**
   * List of constraints.
   *
   * Constraints must have a unique name. If a parent and child policy
   * both contain a constraint with the same class and name, the child's
   * policy's constraint takes priority.
   */
  @Override
  public @NotNull Collection<Constraint> constraints(ConstraintClass c) {
    var constraints = this.constraints.get(c);
    return constraints != null
      ? constraints
      : List.of();
  }

  /**
   * Set the parent. This method can only be called once, and should only
   * be used during initialization.
   */
  protected void setParent(@NotNull Policy parent) {
    Preconditions.checkArgument(parent != this, "Parent must not be the same policy");
    Preconditions.checkArgument(this.parent == null, "Parent has been set already");
    this.parent = parent;
  }

  @Override
  public @NotNull String toString() {
    return this.name;
  }
}
