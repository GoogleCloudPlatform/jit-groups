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

package com.google.solutions.jitaccess.core.adapters;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

public class TestIamTemporaryAccessConditions {
  // -------------------------------------------------------------------------
  // isTemporaryAccessCondition.
  // -------------------------------------------------------------------------

  @Test
  public void whenExpressionIsNull_ThenIsTemporaryAccessConditionReturnsFalse() {
    assertFalse(IamTemporaryAccessConditions.isTemporaryAccessCondition(null));
  }

  @Test
  public void whenExpressionIsEmpty_ThenIsTemporaryAccessConditionReturnsFalse() {
    assertFalse(IamTemporaryAccessConditions.isTemporaryAccessCondition(""));
  }

  @Test
  public void whenExpressionIsTemporaryCondition_ThenIsTemporaryAccessConditionReturnsTrue() {
    var clause =
      "  (request.time >= timestamp(\"2020-01-01T00:00:00Z\") && "
        + "request.time < timestamp(\"2020-01-01T00:05:00Z\"))  ";

    assertTrue(IamTemporaryAccessConditions.isTemporaryAccessCondition(clause));
  }

  @Test
  public void
  whenExpressionContainsMoreThanTemporaryCondition_ThenIsTemporaryAccessConditionReturnsTrue() {
    var clause =
      "  (request.time >= timestamp(\"2020-01-01T00:00:00Z\") && "
        + "request.time < timestamp(\"2020-01-01T00:05:00Z\")) && foo='foo' ";

    assertFalse(IamTemporaryAccessConditions.isTemporaryAccessCondition(clause));
  }

  // -------------------------------------------------------------------------
  // createExpression.
  // -------------------------------------------------------------------------

  @Test
  public void whenDurationValid_ThenCreateExpressionReturnsClause() {
    var clause =
      IamTemporaryAccessConditions.createExpression(
        Instant.from(OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)), Duration.ofMinutes(5));

    assertEquals(
      "(request.time >= timestamp(\"2020-01-01T00:00:00Z\") && "
        + "request.time < timestamp(\"2020-01-01T00:05:00Z\"))",
      clause);
  }
}
