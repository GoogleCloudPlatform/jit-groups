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

package com.google.solutions.jitaccess.catalog.policy;

import com.google.solutions.jitaccess.apis.IamRole;
import com.google.solutions.jitaccess.apis.ProjectId;
import com.google.solutions.jitaccess.catalog.auth.JitGroupId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestJitGroupPolicy {

  private static EnvironmentPolicy createEnvironmentPolicy() {
    return new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new Policy.Metadata("test", Instant.EPOCH));
  }

  //---------------------------------------------------------------------------
  // Constructor.
  //---------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    " ",
    "123456789_123456789_12345",
    "with spaces",
    "?"})
  public void constructor_whenNameInvalid_throwsException(String name) {
    assertThrows(
      IllegalArgumentException.class,
      () -> new JitGroupPolicy(name, "description"));
  }

  @Test
  public void constructor_whenNameTooLong() {
    assertThrows(
      IllegalArgumentException.class,
      () -> new JitGroupPolicy(
        new String(new char[JitGroupPolicy.NAME_MAX_LENGTH + 1]).replace('\0', 'a'),
        "description"));
  }

  //---------------------------------------------------------------------------
  // toString.
  //---------------------------------------------------------------------------

  @Test
  public void toStringReturnsName() {
    var group = new JitGroupPolicy("group-1", "description");

    assertEquals(
      "group-1",
      group.toString());
  }

  //---------------------------------------------------------------------------
  // id.
  //---------------------------------------------------------------------------

  @Test
  public void id() {
    var group = new JitGroupPolicy("group-1", "description");

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "")
        .add(group));

    assertEquals(
      new JitGroupId("env-1", "system-1", "group-1"),
      group.id());
  }

  //---------------------------------------------------------------------------
  // accessControlList.
  //---------------------------------------------------------------------------

  @Test
  public void privileges() {
    var privileges = List.<Privilege>of(new IamRoleBinding(
      new ProjectId("project-1"),
      new IamRole("roles/viewer")));

    var group = new JitGroupPolicy(
      "group-1",
      "description",
      AccessControlList.EMPTY,
      Map.of(),
      privileges);

    assertEquals(privileges, group.privileges());
  }

  //---------------------------------------------------------------------------
  // accessControlList.
  //---------------------------------------------------------------------------

  @Test
  public void accessControlList() {
    var group = new JitGroupPolicy(
      "group-1",
      "description",
      AccessControlList.EMPTY,
      Map.of(),
      List.of());

    assertTrue(group.accessControlList().isPresent());
  }
}
