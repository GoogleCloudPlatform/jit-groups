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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestEmailAddress {
  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsEmail() {
    assertEquals("test@example.com", new EmailAddress("test@example.com").toString());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void equals_whenObjectAreEquivalent() {
    EmailAddress id1 = new EmailAddress("bob@example.com");
    EmailAddress id2 = new EmailAddress("bob@example.com");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void equals_whenObjectAreSame() {
    EmailAddress id1 = new EmailAddress("bob@example.com");

    assertTrue(id1.equals(id1));
  }

  @Test
  public void equals_whenObjectAreMotEquivalent() {
    EmailAddress id1 = new EmailAddress("alice@example.com");
    EmailAddress id2 = new EmailAddress("bob@example.com");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void equals_whenObjectIsNull() {
    EmailAddress id1 = new EmailAddress("bob@example.com");

    assertFalse(id1.equals(null));
  }

  @Test
  public void equals_whenObjectIsDifferentType() {
    EmailAddress id1 = new EmailAddress("bob@example.com");

    assertFalse(id1.equals(""));
  }
}
