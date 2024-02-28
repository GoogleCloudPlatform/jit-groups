//
// Copyright 2022 Google LLC
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

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * ID of a Google Cloud project.
 */
public record ProjectId(
  @NotNull String id
) implements Comparable<ProjectId>, ResourceId {
  static final String PREFIX = "projects/";

  public ProjectId {
    Preconditions.checkNotNull(id, "id");
    assert !id.startsWith("//");
    assert !id.contains("/");
  }

  @Override
  public String toString() {
    return this.id;
  }

  /**
   * Parse a project ID from one of the formats
   *
   * * projects/project-123
   * * project-123
   *
   * @return empty if the input string is malformed.
   */
  public static @NotNull Optional<ProjectId> parse(@Nullable String s) {
    if (s == null) {
      return Optional.empty();
    }

    s = s.trim();

    if (s.startsWith(PREFIX) &&
      s.indexOf('/', PREFIX.length()) == -1 &&
      s.length() > PREFIX.length()) {
      return Optional.of(new ProjectId(s.substring(PREFIX.length())));
    }
    else if (s.length() > 0 && s.indexOf('/') == -1 && !Character.isDigit(s.charAt(0))) {
      return Optional.of(new ProjectId(s));
    }
    else {
      return Optional.empty();
    }
  }

  // -------------------------------------------------------------------------
  // Comparable.
  // -------------------------------------------------------------------------

  @Override
  public int compareTo(@NotNull ProjectId o) {
    return this.id.compareTo(o.id);
  }

  // -------------------------------------------------------------------------
  // ResourceId.
  // -------------------------------------------------------------------------

  @Override
  public @NotNull String type() {
    return "project";
  }

  @Override
  public @NotNull String path() {
    return PREFIX + this.id;
  }
}
