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

import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.policy.PolicyAnalysis;
import com.google.solutions.jitaccess.catalog.policy.PolicyPermission;
import com.google.solutions.jitaccess.catalog.policy.SystemPolicy;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;

/**
 * System in the context of a specific subject.
 */
public class SystemContext {
  private final @NotNull SystemPolicy policy;
  private final @NotNull Subject subject;
  private final @NotNull Provisioner provisioner;

  SystemContext(
    @NotNull SystemPolicy policy,
    @NotNull Subject subject,
    @NotNull Provisioner provisioner
  ) {
    this.policy = policy;
    this.subject = subject;
    this.provisioner = provisioner;
  }

  /**
   * Get system policy.
   */
  public @NotNull SystemPolicy policy() {
    return this.policy;
  }

  /**
   * List JIT groups for which the subject has VIEW access.
   */
  public @NotNull Collection<JitGroupContext> groups() {
    return this.policy
      .groups()
      .stream()
      .filter(grp -> grp
        .analyze(this.subject, EnumSet.of(PolicyPermission.VIEW))
        .execute()
        .isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT))
      .map(grp -> new JitGroupContext(grp, this.subject, this.provisioner))
      .sorted(Comparator.comparing(g -> g.policy().id()))
      .toList();
  }

  /**
   * Get details for a JIT group. Requires VIEW access.
   *
   * @return group details
   */
  public @NotNull Optional<JitGroupContext> group(@NotNull String name) {
    return this.policy
      .group(name)
      .filter(grp -> grp
        .analyze(this.subject, EnumSet.of(PolicyPermission.VIEW))
        .execute()
        .isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT))
      .map(grp -> new JitGroupContext(grp, this.subject, this.provisioner));
  }
}
