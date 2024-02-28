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

package com.google.solutions.jitaccess.catalog.auth;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * A principal that can be used in access checks.
 *
 * @param id unique principal Id.
 * @param expiry optional expiry date.
 */
public record Principal(
  @NotNull PrincipalId id,
  @Nullable Instant expiry
  ) {
  public Principal {
    Preconditions.checkArgument(
      expiry == null || !id.type().equals(UserId.TYPE),
      "User principals cannot expire");
  }

  public Principal(@NotNull PrincipalId id) {
    this(id, null);
  }

  public boolean isPermanent() {
    return this.expiry == null;
  }

  public boolean isValid() {
    return this.expiry == null || Instant.now().isBefore(this.expiry);
  }
}
