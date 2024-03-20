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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.EntitlementSet;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.SortedSet;

/**
 * Repository for ProjectRoleBinding-based entitlements.
 */
public abstract class ProjectRoleRepository {

  /**
   * Find projects that a user has standing, JIT-, or MPA-eligible access to.
   */
  abstract @NotNull SortedSet<ProjectId> findProjectsWithEntitlements(
    @NotNull UserId user
  ) throws AccessException, IOException;

  /**
   * List entitlements for the given user.
   */
  abstract @NotNull EntitlementSet<ProjectRole> findEntitlements(
    @NotNull UserId user,
    @NotNull ProjectId projectId,
    @NotNull EnumSet<ActivationType> typesToInclude
  ) throws AccessException, IOException;

  /**
   * List users that hold an eligible role binding.
   */
  abstract @NotNull Set<UserId> findEntitlementHolders(
    @NotNull ProjectRole roleBinding,
    @NotNull ActivationType activationType
  ) throws AccessException, IOException;
}
