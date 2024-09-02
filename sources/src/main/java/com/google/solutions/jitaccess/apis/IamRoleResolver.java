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

package com.google.solutions.jitaccess.apis;

import com.google.solutions.jitaccess.apis.clients.IamClient;
import com.google.solutions.jitaccess.util.Lazy;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * Validates IAM roles.
 */
@Singleton
public class IamRoleResolver {
  private final @NotNull IamClient iamClient;
  private final @NotNull Lazy<Set<IamRole>> predefinedRoles;

  public IamRoleResolver(
    @NotNull IamClient iamClient
  ) {
    this.iamClient = iamClient;

    //
    // Load list of predefined roles from IAM API, but do so
    // on first access only.
    //
    this.predefinedRoles = Lazy.initializeOpportunistically(
      () -> new HashSet<>(iamClient.listPredefinedRoles()));
  }

  /**
   * Check if a given role exists.
   */
  public boolean exists(@NotNull IamRole role) {
    if (role.isPredefined()) {
      return this.predefinedRoles
        .get()
        .contains(role);
    }
    else {
      //
      // We currently can't validate those, assume it's valid.
      //
      return true;
    }
  }

  /**
   * Lint an IAM role binding.
   *
   * @return List of issues, or empty if the binding is ok.
   */
  public @NotNull Collection<LintingIssue> lintRoleBinding(
    @NotNull ResourceId resourceId,
    @NotNull IamRole role,
    @Nullable String condition
  ) throws IOException {
    var issues = new LinkedList<LintingIssue>();
    if (!exists(role)) {
      issues.add(new LintingIssue(String.format("The role '%s' does not exist", role)));
    }

    this.iamClient
      .lintIamCondition(resourceId, condition)
      .stream()
      .map(r -> new LintingIssue(r.getDebugMessage()))
      .forEach(issues::add);

    return issues;
  }

  public record LintingIssue(
    @NotNull String details
  ) {}
}
