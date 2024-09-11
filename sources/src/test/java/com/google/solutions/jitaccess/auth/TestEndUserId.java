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

package com.google.solutions.jitaccess.auth;

import com.google.solutions.jitaccess.TestRecord;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestEndUserId extends TestRecord<EndUserId> {
  @Override
  protected @NotNull EndUserId createInstance() {
    return new EndUserId("alice@example.com");
  }

  @Override
  protected @NotNull EndUserId createDifferentInstance() {
    return new EndUserId("bob@example.com");
  }

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsEmailInLowerCase() {
    assertEquals("user:test@example.com", new EndUserId("test@example.com").toString());
    assertEquals("user:test@example.com", new EndUserId("Test@Example.com").toString());
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
    "user:  user@example.com  ",
    "  user:USER@example.com "
  })
  public void parse(String id) {
    assertEquals(
      new EndUserId("user@example.com"),
      EndUserId.parse(id).get());
  }
}
