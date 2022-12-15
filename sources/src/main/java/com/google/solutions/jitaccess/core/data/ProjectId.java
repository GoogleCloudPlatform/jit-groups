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

package com.google.solutions.jitaccess.core.data;

import com.google.common.base.Preconditions;

import java.util.Objects;

/**
 * Project ID for a Google Cloud project.
 */
public class ProjectId {
  private static final String PROJECT_RESOURCE_NAME_PREFIX = "//cloudresourcemanager.googleapis.com/projects/";

  public final String id;

  public ProjectId(String id) {
    Preconditions.checkNotNull(id, "id");
    assert !id.startsWith("//");

    this.id = id;
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
  public String getFullResourceName() {
    return PROJECT_RESOURCE_NAME_PREFIX + this.id;
  }

  /**
   * Parse a full resource name (as used by the Asset API).
   */
  public static ProjectId fromFullResourceName(String fullResourceName) {
    return new ProjectId(fullResourceName.substring(PROJECT_RESOURCE_NAME_PREFIX.length()));
  }

  /**
   * Check if a full resource name identifies a project and can be used for
   * a ProjectRole.
   */
  public static boolean isProjectFullResourceName(String fullResourceName) {
    return fullResourceName.startsWith(PROJECT_RESOURCE_NAME_PREFIX)
      && fullResourceName.indexOf('/', PROJECT_RESOURCE_NAME_PREFIX.length()) == -1;
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ProjectId projectId = (ProjectId) o;
    return this.id.equals(projectId.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.id);
  }
}
