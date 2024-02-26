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

package com.google.solutions.jitaccess.core;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Represents a role that has been granted on a resource.
 */
public record RoleBinding (
  String fullResourceName,
  String role
) implements Comparable<RoleBinding> {

  public RoleBinding {
    Preconditions.checkNotNull(fullResourceName, "fullResourceName");
    Preconditions.checkNotNull(role, "role");
  }

  public RoleBinding(@NotNull ProjectId project, String role) {
    this(project.getFullResourceName(), role);
  }

  @Override
  public String toString() {
    return String.format("%s:%s", this.fullResourceName, this.role);
  }

  // -------------------------------------------------------------------------
  // Comparable.
  // -------------------------------------------------------------------------

  @Override
  public int compareTo(@NotNull RoleBinding o) {
    return Comparator.comparing((RoleBinding r) -> r.fullResourceName)
      .thenComparing(r -> r.role)
      .compare(this, o);
  }
}
