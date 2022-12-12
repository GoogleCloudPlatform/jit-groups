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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestProjectRole {
  private final String SAMPLE_PROJECT_FULLRESOURCENAME =
    "//cloudresourcemanager.googleapis.com/projects/project-1";

  // -------------------------------------------------------------------------
  // Constructor.
  // -------------------------------------------------------------------------

  @Test
  public void whenResourceIsNotAProject_ThenConstructorThrowsException() {
    assertThrows(
      IllegalArgumentException.class,
      () -> new ProjectRole(
        new RoleBinding("//cloudresourcemanager.googleapis.com/folders/folder-1", "role/sample"),
        ProjectRole.Status.ELIGIBLE_FOR_JIT));

  }

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toStringReturnsId() {
    var role = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);
    assertEquals(
      "//cloudresourcemanager.googleapis.com/projects/project-1:role/sample (ELIGIBLE_FOR_JIT)",
      role.toString());
  }

  // -------------------------------------------------------------------------
  // getProjectId.
  // -------------------------------------------------------------------------

  @Test
  public void getProjectIdReturnsUnqualifiedProjectId() {
    var role = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);
    assertEquals(new ProjectId("project-1"), role.getProjectId());
  }

  // -------------------------------------------------------------------------
  // equals.
  // -------------------------------------------------------------------------

  @Test
  public void whenValueIsEquivalent_ThenEqualsReturnsTrue() {
    var role1 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);
    var role2 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);

    assertTrue(role1.equals(role2));
    assertTrue(role1.equals((Object) role2));
    assertEquals(role1.hashCode(), role2.hashCode());
    assertEquals(role1.toString(), role2.toString());
  }

  @Test
  public void whenObjectsAreSame_ThenEqualsReturnsTrue() {
    var role1 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);

    assertTrue(role1.equals(role1));
    assertTrue(role1.equals((Object) role1));
    assertEquals(role1.hashCode(), role1.hashCode());
  }

  @Test
  public void whenRolesDiffer_ThenEqualsReturnsFalse() {
    var role1 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/one"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);
    var role2 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/two"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);

    assertFalse(role1.equals(role2));
    assertFalse(role1.equals((Object) role2));
  }

  @Test
  public void whenStatusesDiffer_ThenEqualsReturnsFalse() {
    var role1 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);
    var role2 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ACTIVATED);

    assertFalse(role1.equals(role2));
    assertFalse(role1.equals((Object) role2));
  }

  @Test
  public void equalsNullIsFalse() {
    var role = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);

    assertFalse(role.equals(null));
  }
}