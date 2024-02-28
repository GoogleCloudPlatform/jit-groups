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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestGroupId {
  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsEmailInLowerCase() {
    Assertions.assertEquals("group:test@example.com", new GroupId("test@example.com").toString());
    Assertions.assertEquals("group:test@example.com", new GroupId("Test@Example.com").toString());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void equals_whenObjectAreEquivalent() {
    GroupId id1 = new GroupId("group@example.com");
    GroupId id2 = new GroupId("group@example.com");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectAreEquivalentButDifferInCasing() {
    GroupId id1 = new GroupId("Group@Example.com");
    GroupId id2 = new GroupId("group@example.com");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectAreSame() {
    GroupId id1 = new GroupId("group@example.com");

    assertTrue(id1.equals(id1));
    assertEquals(0, id1.compareTo(id1));
  }

  @Test
  public void equals_whenObjectAreMotEquivalent() {
    GroupId id1 = new GroupId("alice@example.com");
    GroupId id2 = new GroupId("group@example.com");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
    assertNotEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectIsNull() {
    GroupId id1 = new GroupId("group@example.com");

    assertFalse(id1.equals(null));
  }

  @Test
  public void equals_whenObjectIsDifferentType() {
    GroupId id1 = new GroupId("group@example.com");

    assertFalse(id1.equals(""));
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
    "invalid"
  })
  public void parse_whenInvalid(String s) {
    assertFalse(GroupId.parse(null).isPresent());
    assertFalse(GroupId.parse(s).isPresent());
  }

  @Test
  public void parse() {
    assertEquals(
      new GroupId("group@example.com"),
      GroupId.parse("group:group@example.com").get());
  }
}
