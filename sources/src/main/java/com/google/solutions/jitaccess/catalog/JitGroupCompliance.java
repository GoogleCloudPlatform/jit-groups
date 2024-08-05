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

import com.google.solutions.jitaccess.catalog.auth.GroupId;
import com.google.solutions.jitaccess.catalog.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.policy.JitGroupPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Compliance information about a JIT Group.
 */
public class JitGroupCompliance {
  private final @NotNull JitGroupId groupId;
  private final @NotNull GroupId cloudIdentityGroupId;

  private final @Nullable JitGroupPolicy policy;
  private final @Nullable Exception exception;

  public JitGroupCompliance(
    @NotNull JitGroupId groupId,
    @Nullable GroupId cloudIdentityGroupId,
    @Nullable JitGroupPolicy policy,
    @Nullable Exception exception
  ) {
    this.groupId = groupId;
    this.cloudIdentityGroupId = cloudIdentityGroupId;
    this.policy = policy;
    this.exception = exception;
  }

  /**
   * ID of group whose compliance is being reported.
   */
  public @NotNull JitGroupId groupId() {
    return groupId;
  }

  /**
   * Email address of the Cloud Identity group that backs this
   * JIT group.
   */
  public @NotNull Optional<GroupId> cloudIdentityGroupId() {
    return Optional.ofNullable(cloudIdentityGroupId);
  }

  /**
   * Indicates whether this group is found to be compliant.
   */
  public boolean isCompliant() {
    return this.exception == null && this.policy != null;
  }

  /**
   * Indicates whether this group is orphaned, i.e. the group
   * exists in Cloud Identity, but there's no policy for it..
   */
  public boolean isOrphaned() {
    return this.exception == null && this.policy == null;
  }

  /**
   * Exception encountered during reconciliation.
   */
  public @NotNull Optional<Exception> exception() {
    return Optional.ofNullable(exception);
  }
}
