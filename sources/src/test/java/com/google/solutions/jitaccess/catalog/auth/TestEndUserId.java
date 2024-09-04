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

public class TestEndUserId {
  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsEmailInLowerCase() {
    assertEquals("user:test@example.com", new EndUserId("test@example.com").toString());
    assertEquals("user:test@example.com", new EndUserId("Test@Example.com").toString());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void equals_whenObjectAreEquivalent() {
    var id1 = new EndUserId("bob@example.com");
    var id2 = new EndUserId("bob@example.com");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectAreEquivalentButDifferInCasing() {
    var id1 = new EndUserId("Bob@Example.Com");
    var id2 = new EndUserId("bob@example.com");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectAreSame() {
    var id1 = new EndUserId("bob@example.com");

    assertTrue(id1.equals(id1));
    assertEquals(0, id1.compareTo(id1));
  }

  @Test
  public void equals_whenEmailsDiffer() {
    var id1 = new EndUserId("alice@example.com");
    var id2 = new EndUserId("bob@example.com");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
    assertNotEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectIsNull() {
    var id1 = new EndUserId("bob@example.com");

    assertFalse(id1.equals(null));
  }

  @Test
  public void equals_whenObjectIsDifferentType() {
    var id1 = new EndUserId("bob@example.com");

    assertFalse(id1.equals(""));
  }

  // -------------------------------------------------------------------------
  // PrincipalId.
  // -------------------------------------------------------------------------

  @Test
  public void value() {
    assertEquals(
      "bob@example.com",
      new EndUserId("bob@example.com").value());
  }

  @Test
  public void userId() {
    assertInstanceOf(UserId.class, new EndUserId("bob@example.com"));
  }

  @Test
  public void iamPrincipalId() {
    assertInstanceOf(IamPrincipalId.class, new EndUserId("bob@example.com"));
  }

  // -------------------------------------------------------------------------
  // parse.
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    "user",
    "user:",
    "user:joe@",
    "@example.com",
    "invalid",
    "user@example.com",
    "  user@EXAMPLE.COM "
  })
  public void parse_whenInvalid(String s) {
    assertFalse(EndUserId.parse(null).isPresent());
    assertFalse(EndUserId.parse(s).isPresent());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "user:user@example.com",
    "  user:USER@example.com "
  })
  public void parse(String id) {
    assertEquals(
      new EndUserId("user@example.com"),
      EndUserId.parse(id).get());
  }
}
