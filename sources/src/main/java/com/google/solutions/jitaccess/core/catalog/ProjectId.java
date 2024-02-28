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
import org.jetbrains.annotations.NotNull;

/**
 * ID of a Google Cloud project.
 */
public record ProjectId(String id) implements Comparable<ProjectId>, ResourceId {
  private static final String PROJECT_RESOURCE_NAME_PREFIX = "//cloudresourcemanager.googleapis.com/projects/";

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
    return PROJECT_RESOURCE_NAME_PREFIX + this.id;
  }

  /**
   * Parse a full resource name (as used by the Asset API).
   */
  public static @NotNull ProjectId fromFullResourceName(@NotNull String fullResourceName) {
    return new ProjectId(fullResourceName.substring(PROJECT_RESOURCE_NAME_PREFIX.length()));
  }

  /**
   * Check if a full resource name identifies a project and can be used for
   * a ProjectRole.
   */
  public static boolean isProjectFullResourceName(@NotNull String fullResourceName) {
    return fullResourceName.startsWith(PROJECT_RESOURCE_NAME_PREFIX)
        && fullResourceName.indexOf('/', PROJECT_RESOURCE_NAME_PREFIX.length()) == -1;
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
    return String.format("projects/%s", this.id);
  }
}
