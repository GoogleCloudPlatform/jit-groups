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

package com.google.solutions.jitaccess.catalog.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestUserId {
  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsEmailInLowerCase() {
    assertEquals("user:test@example.com", new UserId("test@example.com").toString());
    assertEquals("user:test@example.com", new UserId("Test@Example.com").toString());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void equals_whenObjectAreEquivalent() {
    UserId id1 = new UserId("bob@example.com");
    UserId id2 = new UserId("bob@example.com");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectAreEquivalentButDifferInCasing() {
    UserId id1 = new UserId("Bob@Example.Com");
    UserId id2 = new UserId("bob@example.com");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectAreSame() {
    UserId id1 = new UserId("bob@example.com");

    assertTrue(id1.equals(id1));
    assertEquals(0, id1.compareTo(id1));
  }

  @Test
  public void equals_whenObjectAreMotEquivalent() {
    UserId id1 = new UserId("alice@example.com");
    UserId id2 = new UserId("bob@example.com");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
    assertNotEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectIsNull() {
    UserId id1 = new UserId("bob@example.com");

    assertFalse(id1.equals(null));
  }

  @Test
  public void equals_whenObjectIsDifferentType() {
    UserId id1 = new UserId("bob@example.com");

    assertFalse(id1.equals(""));
  }

  // -------------------------------------------------------------------------
  // PrincipalId.
  // -------------------------------------------------------------------------

  @Test
  public void value() {
    assertEquals(
      "bob@example.com",
      new UserId("bob@example.com").value());
  }

  @Test
  public void iamPrincipalId() {
    assertInstanceOf(IamPrincipalId.class, new UserId("bob@example.com"));
  }

  // -------------------------------------------------------------------------
  // parse.
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    "user",
    "user:",
    "invalid"
  })
  public void parse_whenInvalid(String s) {
    assertFalse(UserId.parse(null).isPresent());
    assertFalse(UserId.parse(s).isPresent());
  }

  @Test
  public void parse() {
    assertEquals(
      new UserId("user@example.com"),
      UserId.parse("user:user@example.com").get());
  }
}
