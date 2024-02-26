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

package com.google.solutions.jitaccess.core.auth;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Primary email address and unique ID of a group.
 */
public class GroupId extends UserEmail {
  private static final String GROUPS_PREFIX = "groups/";

  public final transient @NotNull String id;

  public GroupId(@NotNull String id, String email) {
    super(email);

    Preconditions.checkNotNull(id, "id");

    if (id.startsWith(GROUPS_PREFIX)) {
      id = id.substring(GROUPS_PREFIX.length());
    }

    this.id = id;
  }

  public GroupId(@NotNull String id, @NotNull GroupEmail email) {
    this(id, email.email);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    if (!super.equals(o)) {
      return false;
    }

    GroupId GroupId = (GroupId) o;
    return this.id.equals(GroupId.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), id);
  }

  /**
   * @return ID in groups/ID format.
   */
  @Override
  public String toString() {
    return String.format("%s%s", GROUPS_PREFIX, this.id);
  }
}
