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

package com.google.solutions.jitaccess.core.catalog;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ID of a Google Cloud project.
 */
public record ProjectId(
  @NotNull String id
) implements Comparable<ProjectId>, ResourceId {
  static final String RELATIVE_PREFIX = "projects/";
  static final String ABSOLUTE_PREFIX = "//cloudresourcemanager.googleapis.com/projects/";

  public ProjectId {
    Preconditions.checkNotNull(id, "id");
    assert !id.startsWith("//");
    assert !id.contains("/");
  }

  @Override
  public String toString() {
    return this.id;
  }

  // -------------------------------------------------------------------------
  // Full resource name conversion.
  // -------------------------------------------------------------------------

  /**
   * Return a full resource name as used by the Asset API.
   */
  public @NotNull String getFullResourceName() {
    return ABSOLUTE_PREFIX + this.id;
  }

  /**
   * Parse a project ID from one of the following formats:
   *
   * - projects/123
   * - //cloudresourcemanager.googleapis.com/projects/123
   *
   * The following are not project IDs:
   *
   * - projects/123/foo/123
   * - //cloudresourcemanager.googleapis.com/projects/123/foo/123
   *
   */
  public static @NotNull ProjectId parse(@NotNull String s) {
    if (s.startsWith(ABSOLUTE_PREFIX) && s.indexOf('/', ABSOLUTE_PREFIX.length()) == -1) {
      return new ProjectId(s.substring(ABSOLUTE_PREFIX.length()));
    }
    else if (s.startsWith(RELATIVE_PREFIX) && s.indexOf('/', RELATIVE_PREFIX.length()) == -1) {
      return new ProjectId(s.substring(RELATIVE_PREFIX.length()));
    }
    else {
      throw new IllegalArgumentException("Invalid project ID");
    }
  }

  /**
   * Check if the string is a well-formed project ID and can be parsed.
   */
  public static boolean canParse(@Nullable String s) {
    if (Strings.isNullOrEmpty(s)) {
      return false;
    }
    else if (s.startsWith(ABSOLUTE_PREFIX) && s.indexOf('/', ABSOLUTE_PREFIX.length()) == -1) {
      return true;
    }
    else if (s.startsWith(RELATIVE_PREFIX) && s.indexOf('/', RELATIVE_PREFIX.length()) == -1) {
      return true;
    }
    else {
      return false;
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
  public String path() {
    return RELATIVE_PREFIX + this.id;
  }
}
