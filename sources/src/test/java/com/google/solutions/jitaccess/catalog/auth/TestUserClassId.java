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

public class TestUserClassId {
  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsPrefixedValue() {
    assertEquals("class:iapUsers", UserClassId.IAP_USERS.toString());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void equals_whenObjectAreEquivalent() {
    UserClassId id1 = UserClassId.IAP_USERS;
    UserClassId id2 = UserClassId.IAP_USERS;

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectIsNull() {
    assertFalse(UserClassId.IAP_USERS.equals(null));
  }

  @Test
  public void equals_whenObjectIsDifferentType() {
    assertFalse(UserClassId.IAP_USERS.equals(""));
    assertFalse(UserClassId.IAP_USERS.equals(new UserId("user@example.com")));
  }

  // -------------------------------------------------------------------------
  // PrincipalId.
  // -------------------------------------------------------------------------

  @Test
  public void value() {
    assertEquals("iapUsers", UserClassId.IAP_USERS.value());
  }

  @Test
  public void iamPrincipalId() {
    assertFalse(((Object)UserClassId.IAP_USERS) instanceof IamPrincipalId);
  }

  // -------------------------------------------------------------------------
  // parse.
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    "class",
    "class:",
    " class:  iapUsers",
    "class"
  })
  public void parse_whenInvalid(String s) {
    assertFalse(UserClassId.parse(null).isPresent());
    assertFalse(UserClassId.parse(s).isPresent());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    " class:iapUsers",
    "class:IAPUSERS  "
  })
  public void parse(String s) {
    assertEquals(UserClassId.IAP_USERS, UserClassId.parse(s).get());
  }
}
