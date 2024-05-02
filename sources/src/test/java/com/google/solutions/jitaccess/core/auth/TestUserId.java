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

package com.google.solutions.jitaccess.core.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestUserId {
  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toStringReturnsEmailInLowerCase() {
    assertEquals("test@example.com", new UserId("test@example.com").toString());
    assertEquals("test@example.com", new UserId("Test@Example.com").toString());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void whenObjectAreEquivalent_ThenEqualsReturnsTrue() {
    UserId id1 = new UserId("bob@example.com");
    UserId id2 = new UserId("bob@example.com");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void whenObjectAreEquivalentButDifferInCasing_ThenEqualsReturnsTrue() {
    UserId id1 = new UserId("Bob@Example.Com");
    UserId id2 = new UserId("bob@example.com");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void whenObjectAreSame_ThenEqualsReturnsTrue() {
    UserId id1 = new UserId("bob@example.com");

    assertTrue(id1.equals(id1));
    assertEquals(0, id1.compareTo(id1));
  }

  @Test
  public void whenObjectAreMotEquivalent_ThenEqualsReturnsFalse() {
    UserId id1 = new UserId("alice@example.com");
    UserId id2 = new UserId("bob@example.com");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
    assertNotEquals(0, id1.compareTo(id2));
  }

  @Test
  public void whenObjectIsNull_ThenEqualsReturnsFalse() {
    UserId id1 = new UserId("bob@example.com");

    assertFalse(id1.equals(null));
  }

  @Test
  public void whenObjectIsDifferentType_ThenEqualsReturnsFalse() {
    UserId id1 = new UserId("bob@example.com");

    assertFalse(id1.equals(""));
  }

  // -------------------------------------------------------------------------
  // PrincipalIdentifier.
  // -------------------------------------------------------------------------

  @Test
  public void value() {
    assertEquals(
      "bob@example.com",
      new UserId("bob@example.com").value());
  }
}
