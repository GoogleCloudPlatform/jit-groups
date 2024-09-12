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

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.auth.JitGroupId;
import com.google.solutions.jitaccess.auth.Subject;
import com.google.solutions.jitaccess.catalog.policy.PolicyPermission;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Catalog of groups that a subject can access.
 * <p>
 * This class serves as the "entry point" for the API/UI to
 * lookup or join groups.
 */
public class Catalog {
  private final @NotNull Map<String, Environment> environments;
  private final @NotNull Subject subject;

  public Catalog(
    @NotNull Subject subject,
    @NotNull Collection<Environment> environments
  ) {
    this.subject = subject;
    this.environments = environments
      .stream()
      .collect(Collectors.toMap(e -> e.name(), e -> e));
  }

  /**
   * Get list of environments. Does not require any permissions
   * because checking permissions would require loading the full
   * policy. To compensate, the method only returns a bare
   * minimum of data.
   */
  public @NotNull Collection<Environment> environments() {
    return this.environments.values();
  }

  /**
   * Get environment policy. Requires VIEW access.
   */
  public @NotNull Optional<EnvironmentContext> environment(@NotNull String name) {
    Preconditions.checkArgument(name != null, "Environment name must not be null");

    return Optional.ofNullable(this.environments.get(name))
      .filter(env -> env.policy().isAccessAllowed(this.subject, EnumSet.of(PolicyPermission.VIEW)))
      .map(env -> new EnvironmentContext(env.policy(), this.subject, env.provisioner()));
  }

  /**
   * Get details for a JIT group. Requires VIEW access.
   *
   * @return group details
   */
  public @NotNull Optional<JitGroupContext> group(
    @NotNull JitGroupId groupId
  ) {
    Preconditions.checkArgument(groupId != null, "Group ID must not be null");

    return environment(groupId.environment())
      .flatMap(env -> env.system(groupId.system()))
      .flatMap(sys -> sys.group(groupId.name()));
  }
}
