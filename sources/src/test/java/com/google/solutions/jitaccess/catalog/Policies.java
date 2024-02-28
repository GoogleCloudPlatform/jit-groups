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

package com.google.solutions.jitaccess.catalog;

import com.google.solutions.jitaccess.catalog.policy.*;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Policies {

  public static @NotNull JitGroupPolicy createJitGroupPolicy(
    @NotNull String name,
    @NotNull AccessControlList acl,
    @NotNull Map<Policy.ConstraintClass, Collection<Constraint>> constraints,
    @NotNull List<Privilege> privileges
  ) {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new com.google.solutions.jitaccess.catalog.policy.Policy.Metadata("test", Instant.EPOCH));
    var system = new SystemPolicy("sys-1", "");
    var group = new JitGroupPolicy(name, "", acl, constraints, privileges);

    environment.add(system);
    system.add(group);

    return group;
  }

  public static @NotNull JitGroupPolicy createJitGroupPolicy(
    @NotNull String name,
    @NotNull AccessControlList acl,
    @NotNull Map<Policy.ConstraintClass, Collection<Constraint>> constraints
  ) {
    return createJitGroupPolicy(name, acl, constraints, List.of());
  }
}
