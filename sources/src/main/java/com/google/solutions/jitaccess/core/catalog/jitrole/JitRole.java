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

package com.google.solutions.jitaccess.core.catalog.jitrole;

import com.google.solutions.jitaccess.core.catalog.EntitlementId;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies JIT role.
 *
 * A JIT role represents a 'bundle' of permissions that a user needs to
 * perform a certain job function. It contains:
 *
 * - a fixed set of permissions on, granted on
 * - a fixed set of resources.
 *
 * The resources might be spread over multiple projects.
 */
public class JitRole extends EntitlementId {
  static final String CATALOG = "jit-role";

  private final @NotNull String policyName;

  private final @NotNull String roleName;

  public JitRole(@NotNull String policyName, @NotNull String roleName) {
    this.policyName = policyName;
    this.roleName = roleName;
  }

  public @NotNull String policyName() {
    return this.policyName;
  }

  public @NotNull String name() {
    return this.roleName;
  }

  @Override
  public @NotNull String catalog() {
    return CATALOG;
  }

  @Override
  public @NotNull String id() {
    return String.format("%s/%s", this.policyName, this.roleName);
  }
}
