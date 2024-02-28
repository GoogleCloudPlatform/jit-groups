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

package com.google.solutions.jitaccess.apis.clients;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Unique group key as used by the Cloud Identity Groups API.
 *
 * Group keys are not email addresses, and also aren't GAIA IDs.
 */
public class GroupKey {
  private static final String GROUPS_PREFIX = "groups/";

  public final transient @NotNull String id;

  public GroupKey(@NotNull String id) {
    Preconditions.checkNotNull(id, "id");

    if (id.startsWith(GROUPS_PREFIX)) {
      id = id.substring(GROUPS_PREFIX.length());
    }

    this.id = id;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    GroupKey other = (GroupKey) o;
    return this.id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.id);
  }

  /**
   * @return ID in groups/ID format.
   */
  @Override
  public String toString() {
    return String.format("%s%s", GROUPS_PREFIX, this.id);
  }
}
