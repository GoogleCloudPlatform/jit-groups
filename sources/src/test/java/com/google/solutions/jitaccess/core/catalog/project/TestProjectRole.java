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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.solutions.jitaccess.core.catalog.ProjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestProjectRole {
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toStringReturnsResourceAndRole() {
    var role = new ProjectRole(SAMPLE_PROJECT, "role/sample");
    assertEquals("iam:project-1:role/sample", role.toString());
  }

  // -------------------------------------------------------------------------
  // equals.
  // -------------------------------------------------------------------------

  @Test
  public void whenValueIsEquivalent_ThenEqualsReturnsTrue() {
    var ref1 = new ProjectRole(SAMPLE_PROJECT, "roles/test");
    var ref2 = new ProjectRole(SAMPLE_PROJECT, "roles/test");

    assertTrue(ref1.equals(ref2));
    assertTrue(ref1.equals((Object) ref2));
    assertEquals(ref1.hashCode(), ref2.hashCode());
    assertEquals(ref1.toString(), ref2.toString());
  }

  @Test
  public void whenObjectsAreSame_ThenEqualsReturnsTrue() {
    var role = new ProjectRole(SAMPLE_PROJECT, "roles/test");

    assertTrue(role.equals(role));
    assertTrue(role.equals((Object) role));
    assertEquals(role.hashCode(), role.hashCode());
  }

  @Test
  public void whenRolesDiffer_ThenEqualsReturnsFalse() {
    var role1 = new ProjectRole(new ProjectId("project-1"), "roles/test");
    var role2 = new ProjectRole(new ProjectId("project-1"), "roles/other");

    assertFalse(role1.equals(role2));
    assertFalse(role1.equals((Object) role2));
  }

  @Test
  public void whenResourcesDiffer_ThenEqualsReturnsFalse() {
    var ref1 = new ProjectRole(new ProjectId("project-1"), "roles/test");
    var ref2 = new ProjectRole(new ProjectId("project-2"), "roles/test");

    assertFalse(ref1.equals(ref2));
    assertFalse(ref1.equals((Object) ref2));
  }

  @Test
  public void equalsNullIsFalse() {
    var ref1 = new ProjectRole(SAMPLE_PROJECT, "roles/test");

    assertFalse(ref1.equals(null));
  }
}
