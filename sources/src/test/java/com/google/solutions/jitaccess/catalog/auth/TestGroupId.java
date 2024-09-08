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

package com.google.solutions.jitaccess.catalog.auth;

import com.google.solutions.jitaccess.TestRecord;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestGroupId extends TestRecord<GroupId> {
  @Override
  protected @NotNull GroupId createInstance() {
    return new GroupId("group-1@example.com");
  }

  @Override
  protected @NotNull GroupId createDifferentInstance() {
    return new GroupId("group-2@example.com");
  }

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsEmailInLowerCase() {
    Assertions.assertEquals("group:test@example.com", new GroupId("test@example.com").toString());
    Assertions.assertEquals("group:test@example.com", new GroupId("Test@Example.com").toString());
  }

  // -------------------------------------------------------------------------
  // PrincipalId.
  // -------------------------------------------------------------------------

  @Test
  public void value() {
    assertEquals(
      "group@example.com",
      new GroupId("group@example.com").value());
  }

  @Test
  public void iamPrincipalId() {
    assertInstanceOf(IamPrincipalId.class, new GroupId("group@example.com"));
  }

  // -------------------------------------------------------------------------
  // parse.
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    "group",
    "group:",
    "invalid",
    "invalid@",
    "@invalid",
    "group@example.com",
    "  group@EXAMPLE.COM "
  })
  public void parse_whenInvalid(String s) {
    assertFalse(GroupId.parse(null).isPresent());
    assertFalse(GroupId.parse(s).isPresent());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "group:group@example.com",
    "group: group@example.com ",
    "  group:GROUP@example.com "
  })
  public void parse(String id) {
    assertEquals(
      new GroupId("group@example.com"),
      GroupId.parse(id).get());
  }

  // -------------------------------------------------------------------------
  // Components.
  // -------------------------------------------------------------------------

  @Test
  public void components_whenEmailInvalid() {
    assertThrows(
      IllegalStateException.class,
      () -> new GroupId("invalid").components());
  }

  @Test
  public void components() {
    var components = new GroupId("GROUP-1@SUB.EXAMPLE.COM").components();
    assertEquals("group-1", components.name());
    assertEquals("sub.example.com", components.domain());
  }
}
