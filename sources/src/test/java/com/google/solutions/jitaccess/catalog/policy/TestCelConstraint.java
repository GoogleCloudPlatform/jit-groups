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

package com.google.solutions.jitaccess.catalog.policy;

import com.google.solutions.jitaccess.common.cel.InvalidExpressionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestCelConstraint {

  //---------------------------------------------------------------------------
  // Constructor.
  //---------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    " ",
    "123456789_1234567",
    "with spaces",
    "?"})
  public void constructor_whenNameInvalid(String name) {
    assertThrows(
      IllegalArgumentException.class,
      () -> new CelConstraint(name, "display name", List.of(), "?"));
  }

  //---------------------------------------------------------------------------
  // toString.
  //---------------------------------------------------------------------------

  @Test
  public void toString_returnsName() {
    var constraint = new CelConstraint("name", "display name", List.of(), "?");
    assertEquals("name [?]", constraint.toString());
  }

  //---------------------------------------------------------------------------
  // Context variables.
  //---------------------------------------------------------------------------

  @Test
  public void execute_whenExpressionInvalid_throwsException() {
    var constraint = new CelConstraint(
      "name",
      "display name",
      List.of(),
      "my.name == 'missing quote");

    assertThrows(
      InvalidExpressionException.class,
      () -> constraint.createCheck().evaluate());
  }

  @Test
  public void execute_whenContextVariableSet_returnsFalse() throws Exception {
    var constraint = new CelConstraint(
      "name",
      "display name",
      List.of(),
      "my.name == 'test'");

    var positive = constraint.createCheck();
    positive.addContext("my").set("name", "test");
    assertTrue(positive.evaluate());

    var negative = constraint.createCheck();
    negative.addContext("my").set("name", "foo");
    assertFalse(negative.evaluate());
  }

  @Test
  public void execute_whenExpressionContainsExtractCall_returnsTrue() throws Exception {
    var constraint = new CelConstraint(
      "name",
      "display name",
      List.of(),
      "my.name.extract('t{x}t') == 'es'");

    var positive = constraint.createCheck();
    positive.addContext("my").set("name", "test");
    assertTrue(positive.evaluate());
  }

  //---------------------------------------------------------------------------
  // lint.
  //---------------------------------------------------------------------------

  @Test
  public void lint_whenExpressionInvalid() {
    var constraint = new CelConstraint(
      "name",
      "display name",
      List.of(),
      "has(");

    var issues = constraint.lint();
    assertFalse(issues.isEmpty());
  }

  @Test
  public void lint() {
    var constraint = new CelConstraint(
      "name",
      "display name",
      List.of(new CelConstraint.StringVariable("test", "Test variable", 1, 10)),
      "has(input.test)");

    var issues = constraint.lint();
    assertTrue(issues.isEmpty());
  }

  //---------------------------------------------------------------------------
  // Check.
  //---------------------------------------------------------------------------

  @Test
  public void execute_whenInputMissing_throwsException() {
    var constraint = new CelConstraint(
      "name",
      "display name",
      List.of(new CelConstraint.StringVariable("test", "Test variable", 1, 10)),
      "has(input.test)");

    assertThrows(
      IllegalArgumentException.class,
      () -> constraint.createCheck().evaluate());
  }

  @Test
  public void execute_whenInputSet_thenValueIsAvailable() throws Exception {
    var constraint = new CelConstraint(
      "name",
      "display name",
      List.of(new CelConstraint.StringVariable("test", "Test variable", 1, 10)),
      "has(input.test) && input.test == 'some value'");

    var check = constraint.createCheck();
    check.input().get(0).set("some value");

    assertTrue(check.evaluate());
  }

  //---------------------------------------------------------------------------
  // Variable.
  //---------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    " ",
    "123456789_1234567",
    "with spaces",
    "_reserved"})
  public void variableConstructor_whenNameInvalid(String name) {
    assertThrows(
      IllegalArgumentException.class,
      () -> new CelConstraint.StringVariable(name, "display name", 0, 1));
    assertThrows(
      IllegalArgumentException.class,
      () -> new CelConstraint.LongVariable(name, "display name", 0L, 1L));
    assertThrows(
      IllegalArgumentException.class,
      () -> new CelConstraint.BooleanVariable(name, "display name"));
  }
}
