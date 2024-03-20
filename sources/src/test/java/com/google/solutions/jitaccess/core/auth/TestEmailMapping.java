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

package com.google.solutions.jitaccess.core.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestEmailMapping {
  @Test
  public void nullExpression() {
    var mapping = new EmailMapping();
    assertEquals(
      "user@example.com",
      mapping.emailFromUserId(new UserId("user@example.com")).value());
  }

  @Test
  public void emptyExpression() {
    var mapping = new EmailMapping("");
    assertEquals(
      "user@example.com",
      mapping.emailFromUserId(new UserId("user@example.com")).value());
  }

  @Test
  public void userDotEmailExpression() {
    var expression ="user.email";
    assertEquals(
      "user@example.com",
      new EmailMapping(expression).emailFromUserId(new UserId("user@example.com")).value());
  }

  @Test
  public void substituteDomainExpression() {
    var expression = "user.email.extract('{handle}@example.com') + '@test.example.com'";
    assertEquals(
      "user@test.example.com",
      new EmailMapping(expression).emailFromUserId(new UserId("user@example.com")).value());
  }

  @Test
  public void substituteDomainConditionallyExpression() {
    var expression =
      "user.email.endsWith('@external.example.com') " +
        "? user.email.extract('{handle}@external.example.com') + '@otherdomain.example' " +
        ": user.email";

    assertEquals(
      "contractor@otherdomain.example",
      new EmailMapping(expression).emailFromUserId(new UserId("contractor@external.example.com")).value());
    assertEquals(
      "user@example.com",
      new EmailMapping(expression).emailFromUserId(new UserId("user@example.com")).value());
  }

  @Test
  public void invalidExpression() {
    assertThrows(
      EmailMapping.MappingException.class,
      () -> new EmailMapping("user.email.extract(")
        .emailFromUserId(new UserId("user@example.com"))
        .value());
  }
}
