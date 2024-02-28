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

package com.google.solutions.jitaccess.apis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestIamRole {

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsId() {
    assertEquals("roles/viewer", new IamRole("roles/viewer").toString());
  }

  // -------------------------------------------------------------------------
  // parse.
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    " ",
    "roles/",
    "role/x",
    "ROLES/x",
    "x",
    "organizations/",
    "  projects/  "})
  public void whenRoleInvalid(String s) {
    assertFalse(IamRole.parse(null).isPresent());
    assertFalse(IamRole.parse(s).isPresent());
  }

  @Test
  public void parse_whenPredefinedRole() {
    var role = IamRole.parse("  roles/viewer  ");

    assertTrue(role.isPresent());
    assertEquals("roles/viewer", role.get().name());
  }

  @Test
  public void parse_whenCustomProjectRole() {
    var role = IamRole.parse(" projects/project-1/roles/CustomRole ");

    assertTrue(role.isPresent());
    assertEquals("projects/project-1/roles/CustomRole", role.get().name());
  }

  @Test
  public void parse_whenCustomOrgRole() {
    var role = IamRole.parse(" organizations/123/roles/CustomRole ");

    assertTrue(role.isPresent());
    assertEquals("organizations/123/roles/CustomRole", role.get().name());
  }
}
