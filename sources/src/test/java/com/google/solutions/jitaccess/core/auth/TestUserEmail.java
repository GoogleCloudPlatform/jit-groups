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

import com.google.solutions.jitaccess.core.auth.UserEmail;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestUserEmail {
  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toStringReturnsEmail() {
    assertEquals("test@example.com", new UserEmail("test@example.com").toString());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void whenObjectAreEquivalent_ThenEqualsReturnsTrue() {
    UserEmail id1 = new UserEmail("bob@example.com");
    UserEmail id2 = new UserEmail("bob@example.com");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectAreSame_ThenEqualsReturnsTrue() {
    UserEmail id1 = new UserEmail("bob@example.com");

    assertTrue(id1.equals(id1));
  }

  @Test
  public void whenObjectAreMotEquivalent_ThenEqualsReturnsFalse() {
    UserEmail id1 = new UserEmail("alice@example.com");
    UserEmail id2 = new UserEmail("bob@example.com");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectIsNull_ThenEqualsReturnsFalse() {
    UserEmail id1 = new UserEmail("bob@example.com");

    assertFalse(id1.equals(null));
  }

  @Test
  public void whenObjectIsDifferentType_ThenEqualsReturnsFalse() {
    UserEmail id1 = new UserEmail("bob@example.com");

    assertFalse(id1.equals(""));
  }

  // -------------------------------------------------------------------------
  // PrincipalIdentifier.
  // -------------------------------------------------------------------------

  @Test
  public void value() {
    assertEquals(
      "bob@example.com",
      new UserEmail("bob@example.com").value());
  }
}
