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

import java.util.*;

/**
 * Policy for a system. A system could correspond to a Google Cloud
 * projects, but can be more generic than that. For example:
 *
 * - "Foo application backend"
 * - CI/CD system
 * - Data warehouse for the Bar app
 */
public class SystemPolicy extends AbstractPolicy {
  static final String NAME_PATTERN = "^[a-zA-Z0-9\\-]{1,16}$";

  private final @NotNull Map<String, JitGroupPolicy> groups = new TreeMap<>();

  public SystemPolicy(
    @NotNull String name,
    @NotNull String description,
    @Nullable AccessControlList acl,
    @NotNull Map<ConstraintClass, Collection<Constraint>> constraints
  ) {
    super(name, description, acl, constraints);

    Preconditions.checkNotNull(name, "Name must not be null");
    Preconditions.checkArgument(
      name.matches(NAME_PATTERN),
      "System names must consist of letters, numbers, and hyphens, and must not exceed 16 characters");
  }

  public SystemPolicy(
    @NotNull String name,
    @NotNull String description
  ) {
    this(name, description, null, Map.of());
  }

  /**
   * Add a group policy. This method should only be used during
   * initialization.
   */
  public @NotNull SystemPolicy add(@NotNull JitGroupPolicy group) {
    Preconditions.checkArgument(
      !this.groups.containsKey(group.name()),
      "A group with the same name has already been added");

    group.setParent(this);
    this.groups.put(group.name(), group);
    return this;
  }

  /**
   * Get the parent policy.
   */
  public @NotNull EnvironmentPolicy environment() {
    Preconditions.checkNotNull(this.parent().isPresent(), "Parent must be set");
    return (EnvironmentPolicy)this.parent().get();
  }

  /**
   * Get the list of group policies.
   */
  public @NotNull Collection<JitGroupPolicy> groups() {
    return Collections.unmodifiableCollection(this.groups.values());
  }

  /**
   * Lookup a group policy by name.
   */
  public Optional<JitGroupPolicy> group(@NotNull String name) {
    return Optional.ofNullable(this.groups.get(name));
  }
}
