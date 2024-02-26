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

package com.google.solutions.jitaccess.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestGroupId {

  // -------------------------------------------------------------------------
  // Constructor.
  // -------------------------------------------------------------------------

  @Test
  public void whenIdHasPrefix_ThenConstructorStripsPrefix() {
    assertEquals("1", new GroupId("1", "group-1@example.com").id);
    assertEquals("1", new GroupId("groups/1", "group-1@example.com").id);
  }

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toStringReturnsPrefixedId() {
    assertEquals("groups/1", new GroupId("1", "test@example.com").toString());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void whenObjectAreEquivalent_ThenEqualsReturnsTrue() {
    GroupId id1 = new GroupId("group-1", "group-1@example.com");
    GroupId id2 = new GroupId("group-1", "group-1@example.com");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectAreSame_ThenEqualsReturnsTrue() {
    GroupId id1 = new GroupId("group-1", "group-1@example.com");

    assertTrue(id1.equals(id1));
  }

  @Test
  public void whenObjectAreMotEquivalent_ThenEqualsReturnsFalse() {
    GroupId id1 = new GroupId("id-1", "group-1@example.com");
    GroupId id2 = new GroupId("id-2", "group-1@example.com");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectIsNull_ThenEqualsReturnsFalse() {
    GroupId id1 = new GroupId("group-1", "group-1@example.com");

    assertFalse(id1.equals(null));
  }

  @Test
  public void whenObjectIsDifferentType_ThenEqualsReturnsFalse() {
    var id = new GroupId("group-1", "group-1@example.com");
    var email = new UserEmail("group-1@example.com");

    assertFalse(id.equals(email));
  }
}
