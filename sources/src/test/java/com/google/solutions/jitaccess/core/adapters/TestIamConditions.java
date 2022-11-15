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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

public class TestIamConditions {
  // -------------------------------------------------------------------------
  // isTemporaryConditionClause.
  // -------------------------------------------------------------------------

  @Test
  public void whenExpressionIsNull_ThenIsTemporaryConditionClauseReturnsFalse() {
    assertFalse(IamConditions.isTemporaryConditionClause(null));
  }

  @Test
  public void whenExpressionIsEmpty_ThenIsTemporaryConditionClauseReturnsFalse() {
    assertFalse(IamConditions.isTemporaryConditionClause(""));
  }

  @Test
  public void whenExpressionIsTemporaryCondition_ThenIsTemporaryConditionClauseReturnsTrue() {
    var clause =
      "  (request.time >= timestamp(\"2020-01-01T00:00:00Z\") && "
        + "request.time < timestamp(\"2020-01-01T00:05:00Z\"))  ";

    assertTrue(IamConditions.isTemporaryConditionClause(clause));
  }

  @Test
  public void
  whenExpressionContainsMoreThanTemporaryCondition_ThenIsTemporaryConditionClauseReturnsTrue() {
    var clause =
      "  (request.time >= timestamp(\"2020-01-01T00:00:00Z\") && "
        + "request.time < timestamp(\"2020-01-01T00:05:00Z\")) && foo='foo' ";

    assertFalse(IamConditions.isTemporaryConditionClause(clause));
  }

  // -------------------------------------------------------------------------
  // createTemporaryConditionClause.
  // -------------------------------------------------------------------------

  @Test
  public void whenDurationValid_ThenCreateTemporaryConditionClauseReturnsClause() {
    var clause =
      IamConditions.createTemporaryConditionClause(
        OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), Duration.ofMinutes(5));

    assertEquals(
      "(request.time >= timestamp(\"2020-01-01T00:00:00Z\") && "
        + "request.time < timestamp(\"2020-01-01T00:05:00Z\"))",
      clause);
  }
}
