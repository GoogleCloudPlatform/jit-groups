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
import com.google.solutions.jitaccess.catalog.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.catalog.policy.PolicyHeader;
import com.google.solutions.jitaccess.catalog.policy.PolicyPermission;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Catalog of groups that a subject can access.
 * <p>
 * This class serves as the "entry point" for the API/UI to
 * lookup or join groups.
 */
public class Catalog {
  private final @NotNull Catalog.Source source;
  private final @NotNull Subject subject;

  public Catalog(
    @NotNull Subject subject,
    @NotNull Catalog.Source source
  ) {
    this.subject = subject;
    this.source = source;
  }

  /**
   * Get list of environments. Does not require any permissions
   * because checking permissions would require loading the full
   * policy. To compensate, the method only returns a bare
   * minimum of data.
   */
  public @NotNull Collection<PolicyHeader> environments() {
    return this.source.environmentPolicies();
  }

  /**
   * Get environment policy. Requires VIEW access.
   */
  public @NotNull Optional<EnvironmentContext> environment(@NotNull String name) {
    Preconditions.checkArgument(name != null, "Environment name must not be null");

    var provisioner = this.source.provisioner(this, name);

    return this.source
      .environmentPolicy(name)
      .filter(env -> env.isAccessAllowed(this.subject, EnumSet.of(PolicyPermission.VIEW)))
      .map(policy -> new EnvironmentContext(policy, this.subject, provisioner.get()));
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

  /**
   * Source for environment configuration.
   */
  public interface Source {
    /**
     * Get list of summaries for available policies.
     */
    @NotNull Collection<PolicyHeader> environmentPolicies();

    /**
     * Get policy for an environment.
     */
    @NotNull Optional<EnvironmentPolicy> environmentPolicy(
      @NotNull String environmentName
    );

    /**
     * Get provisioner for an environment
     */
    @NotNull Optional<Provisioner> provisioner(
      @NotNull Catalog catalog,
      @NotNull String environmentName
    );
  }
}
