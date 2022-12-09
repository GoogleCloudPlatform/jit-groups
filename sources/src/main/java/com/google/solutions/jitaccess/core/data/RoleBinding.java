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
 * Represents a role that has been granted on a resource.
 */
public class RoleBinding {
  public final String fullResourceName;
  public final String role;

  public RoleBinding(String fullResourceName, String role) {
    Preconditions.checkNotNull(fullResourceName);
    Preconditions.checkNotNull(role);

    this.fullResourceName = fullResourceName;
    this.role = role;
  }

  public RoleBinding(ProjectId project, String role) {
    this(project.getFullResourceName(), role);
  }

  @Override
  public String toString() {
    return String.format("%s:%s", this.fullResourceName, this.role);
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

    var that = (RoleBinding) o;
    return this.fullResourceName.equals(that.fullResourceName) && this.role.equals(that.role);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.fullResourceName, this.role);
  }
}
