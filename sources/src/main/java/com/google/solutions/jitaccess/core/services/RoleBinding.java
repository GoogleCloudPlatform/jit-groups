//
// Copyright 2021 Google LLC
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

package com.google.solutions.jitaccess.core.services;

import java.util.Objects;

/** A role/resource combination. */
public class RoleBinding {
  /** Status of the binding. */
  private final RoleBindingStatus status;

  /** Unqualified resource name such as "project-1". */
  private final String resourceName;

  /** Qualified resource name such as "//cloudresourcemanager.googleapis.com/projects/project-1". */
  private final String fullResourceName;

  /** Role name such as roles/xxx. */
  private final String role;

  public RoleBinding(
      String resourceName, String fullResourceName, String role, RoleBindingStatus status) {
    this.status = status;
    this.resourceName = resourceName;
    this.fullResourceName = fullResourceName;
    this.role = role;
  }

  public RoleBindingStatus getStatus() {
    return status;
  }

  public String getResourceName() {
    return resourceName;
  }

  public String getFullResourceName() {
    return fullResourceName;
  }

  public String getRole() {
    return role;
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RoleBinding that = (RoleBinding) o;
    return status == that.status
        && resourceName.equals(that.resourceName)
        && fullResourceName.equals(that.fullResourceName)
        && role.equals(that.role);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, resourceName, fullResourceName, role);
  }

  @Override
  public String toString() {
    return String.format("%s on %s (%s)", this.role, this.fullResourceName, this.status);
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public enum RoleBindingStatus {
    ELIGIBLE,
    ACTIVATED
  }
}
