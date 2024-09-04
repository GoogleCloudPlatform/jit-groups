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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestEmailMapping {
  @Test
  public void emailFromPrincipalId_whenExpressionIsNull() {
    var mapping = new EmailMapping();
    assertEquals(
      "user@example.com",
      mapping.emailFromPrincipalId(new EndUserId("user@example.com")).value());
  }

  @Test
  public void emailFromPrincipalId_whenExpressionIsEmpty() {
    var mapping = new EmailMapping("");
    assertEquals(
      "user@example.com",
      mapping.emailFromPrincipalId(new EndUserId("user@example.com")).value());
  }

  @Test
  public void emailFromPrincipalId_withUserDotEmailExpression() {
    var expression ="user.email";
    assertEquals(
      "user@example.com",
      new EmailMapping(expression).emailFromPrincipalId(new EndUserId("user@example.com")).value());
  }

  @Test
  public void emailFromPrincipalId_withPrincipalDotIdExpression() {
    var expression ="principal.id";
    assertEquals(
      "user@example.com",
      new EmailMapping(expression).emailFromPrincipalId(new EndUserId("user@example.com")).value());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "user.email.extract('{handle}@example.com') + '@test.example.com'",
    "principal.id.extract('{handle}@example.com') + '@test.example.com'"
  })
  public void emailFromPrincipalId_withSubstituteDomainExpression(String expression) {
    assertEquals(
      "user@test.example.com",
      new EmailMapping(expression).emailFromPrincipalId(new EndUserId("user@example.com")).value());
    assertEquals(
      "group@test.example.com",
      new EmailMapping(expression).emailFromPrincipalId(new GroupId("group@example.com")).value());
  }

  @Test
  public void emailFromPrincipalId_withConditionalSubstituteDomainExpressionBasedOnId() {
    var expression =
      "principal.id.endsWith('@external.example.com') " +
        "? principal.id.extract('{handle}@external.example.com') + '@otherdomain.example' " +
        ": principal.id";

    assertEquals(
      "contractor@otherdomain.example",
      new EmailMapping(expression).emailFromPrincipalId(new EndUserId("contractor@external.example.com")).value());
    assertEquals(
      "user@example.com",
      new EmailMapping(expression).emailFromPrincipalId(new EndUserId("user@example.com")).value());
  }

  @Test
  public void emailFromPrincipalId_withConditionalSubstituteDomainExpressionBasedOnType() {
    var expression =
      "principal.type == 'user' " +
        "? principal.id.extract('{handle}@example.com') + '@users.example.com' " +
        ": principal.id.extract('{handle}@example.com') + '@groups.example.com'";

    assertEquals(
      "user@users.example.com",
      new EmailMapping(expression).emailFromPrincipalId(new EndUserId("user@example.com")).value());
    assertEquals(
      "group@groups.example.com",
      new EmailMapping(expression).emailFromPrincipalId(new GroupId("group@example.com")).value());
  }

  @Test
  public void emailFromPrincipalId_whenExpressionInvalid() {
    assertThrows(
      EmailMapping.MappingException.class,
      () -> new EmailMapping("user.email.extract(")
        .emailFromPrincipalId(new EndUserId("user@example.com"))
        .value());
  }
}
