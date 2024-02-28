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

package com.google.solutions.jitaccess.catalog.legacy;

import dev.cel.common.CelValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestIamCondition {

  //-------------------------------------------------------------------------
  // toString.
  //-------------------------------------------------------------------------

  @Test
  public void toStringReturnsCondition() {
    var condition = new IamCondition("1+1");
    assertEquals("1+1", condition.toString());
  }

  //-------------------------------------------------------------------------
  // equals.
  //-------------------------------------------------------------------------

  @Test
  public void whenEqual_ThenEqualsReturnsTrue() {
    assertTrue(new IamCondition("1+1").equals(new IamCondition("1+1")));
  }

  @Test
  public void whenNotEqual_ThenEqualsReturnsFalse() {
    assertFalse(new IamCondition("1+1").equals(null));
    assertFalse(new IamCondition("1+1").equals(new IamCondition("1")));
  }

  //-------------------------------------------------------------------------
  // evaluate.
  //-------------------------------------------------------------------------

  @Test
  public void whenExpressionSyntaxInvalid_thenEvaluateThrowsException() throws Exception {
    assertThrows(
      CelValidationException.class,
      () -> new IamCondition("invalidSyntax(").evaluate());
  }

  @Test
  public void whenExpressionUsesUnknownFunction_thenEvaluateThrowsException() throws Exception {
    assertThrows(
      CelValidationException.class,
      () -> new IamCondition("unknownFunction()").evaluate());
  }

  @Test
  public void whenExpressionValid_thenEvaluateReturns() throws Exception {
    assertTrue(new IamCondition("1+1==2").evaluate());
  }

  //-------------------------------------------------------------------------
  // evaluate: request.time.
  //-------------------------------------------------------------------------

  @Test
  public void whenExpressionUsesRequestTime_thenEvaluateReturns() throws Exception {
    assertTrue(new IamCondition("request.time > timestamp(\"2024-01-01T01:02:03Z\")").evaluate());
    assertTrue(new IamCondition("request.time < timestamp(\"2034-01-01T01:02:03Z\")").evaluate());

    assertFalse(new IamCondition("request.time <= timestamp(\"2024-01-01T01:02:03Z\")").evaluate());
    assertFalse(new IamCondition("request.time >= timestamp(\"2034-01-01T01:02:03Z\")").evaluate());
  }

  //-------------------------------------------------------------------------
  // reformat.
  //-------------------------------------------------------------------------

  @Test
  public void whenExpressionContainsExcessWhitespace_ThenReformatCleansWhitespace() throws Exception {
    var condition = new IamCondition("\r\n   1<2    &&\n2<3\n ");
    assertEquals("1 < 2 && 2 < 3", condition.reformat().expression);
  }

  @Test
  public void whenExpressionContainsUnknownSymbols_ThenReformatSucceeds() throws Exception {
    var condition = new IamCondition("\r\n  a<foo() ");
    assertEquals("a < foo()", condition.reformat().expression);
  }

  @Test
  public void whenExpressionMalformed_ThenReformatThrowsException() throws Exception {
    assertThrows(
      IllegalArgumentException.class,
      () -> new IamCondition("\r\n  a<foo(' ").reformat());
  }

  //-------------------------------------------------------------------------
  // splitAnd.
  //-------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {" ", "1<2", "true || false", "a=='&'"})
  public void whenExpressionHasSingleClause_ThenSplitAndReturnsClause(String expression) {
    var clauses = new IamCondition(expression).splitAnd();

    assertEquals(1, clauses.size());
    assertEquals(expression, clauses.get(0).expression);
  }

  @ParameterizedTest
  @ValueSource(strings = {"(a && b && (c || d))"})
  public void whenExpressionHasSingleNestedClause_ThenSplitAndReturnsClause(String expression) {
    var clauses = new IamCondition(expression).splitAnd();

    assertEquals(1, clauses.size());
    assertEquals(expression, clauses.get(0).expression);
  }

  @ParameterizedTest
  @ValueSource(strings = {"s == '&&' || s==\"&&\""})
  public void whenExpressionContainsQuotedOperators_ThenSplitAndReturnsClause(String expression) {
    var clauses = new IamCondition(expression).splitAnd();

    assertEquals(1, clauses.size());
    assertEquals(expression, clauses.get(0).expression);
  }

  @Test
  public void whenExpressionHasMultipleClauses_ThenSplitAndReturnsClauses() {
    var clauses = new IamCondition("a || b && c&&(d&&e) ").splitAnd();

    assertEquals(3, clauses.size());
    assertEquals("a || b ", clauses.get(0).expression);
    assertEquals(" c", clauses.get(1).expression);
    assertEquals("(d&&e) ", clauses.get(2).expression);
  }

  @Test
  public void whenExpressionHasComments_ThenSplitAndReturnsClauses() {
    var clauses = new IamCondition("// comment\r\n" +
      "a || b\n" +
      "   //\n" +
      "   // another comment\r\n" +
      "   //\n" +
      " && \n" +
      "// ignore this-> foo() &&bar()\n" +
      "c&&(d&&e) \n" +
      "//").splitAnd();

    assertEquals(3, clauses.size());
    assertEquals("a || b", clauses.get(0).reformat().toString());
    assertEquals("c", clauses.get(1).reformat().toString());
    assertEquals("d && e", clauses.get(2).reformat().toString());
  }

  //-------------------------------------------------------------------------
  // and.
  //-------------------------------------------------------------------------

  @Test
  public void andCombinesClauses() {
    var condition = IamCondition.and(List.of(
      new IamCondition("a"),
      new IamCondition("b && c"),
      new IamCondition("d && (e||f)")));
    assertEquals("(a) && (b && c) && (d && (e||f))", condition.toString());
  }
}