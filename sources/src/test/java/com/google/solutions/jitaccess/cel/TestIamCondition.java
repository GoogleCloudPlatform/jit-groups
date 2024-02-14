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

package com.google.solutions.jitaccess.cel;

import dev.cel.common.CelValidationException;
import org.junit.jupiter.api.Test;

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
}
