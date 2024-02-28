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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

public class TestTemporaryIamCondition {
  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toStringReturnsClause() {
    var condition =new TemporaryIamCondition(
      Instant.from(OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)),
      Duration.ofMinutes(5));

    assertEquals(
      "(request.time >= timestamp(\"2020-01-01T00:00:00Z\") && "
        + "request.time < timestamp(\"2020-01-01T00:05:00Z\"))",
      condition.toString());
    assertTrue(TemporaryIamCondition.isTemporaryAccessCondition(condition.toString()));
  }

  // -------------------------------------------------------------------------
  // evaluate.
  // -------------------------------------------------------------------------

  @Test
  public void whenNotATemporaryCondition_ThenEvaluateReturnsFalse() throws Exception {
    assertFalse(new TemporaryIamCondition("true").evaluate());
    assertFalse(new TemporaryIamCondition("1+1").evaluate());
  }

  @Test
  public void whenInPermittedTimeRange_ThenEvaluateReturnsTrue() throws Exception {
    var condition = new TemporaryIamCondition(
      Instant.now().minusSeconds(30),
      Duration.ofSeconds(60));

    assertTrue(condition.evaluate());
  }

  @Test
  public void whenBeforePermittedTimeRange_ThenEvaluateReturnsTrue() throws Exception {
    var condition = new TemporaryIamCondition(
      Instant.now().plusSeconds(30),
      Duration.ofSeconds(60));

    assertFalse(condition.evaluate());
  }

  @Test
  public void whenPastPermittedTimeRange_ThenEvaluateReturnsTrue() throws Exception {
    var condition = new TemporaryIamCondition(
      Instant.now().minusSeconds(90),
      Duration.ofSeconds(60));

    assertFalse(condition.evaluate());
  }

  // -------------------------------------------------------------------------
  // getValidity.
  // -------------------------------------------------------------------------

  @Test
  public void whenNotATemporaryCondition_ThenGetValidityThrowsException() {
    assertThrows(
      IllegalArgumentException.class,
      () -> new TemporaryIamCondition("1+1").getValidity());
  }

  @Test
  public void getEndTimeReturnsTime() {
    var startTime = Instant.ofEpochSecond(12345);
    var validity = new TemporaryIamCondition(startTime, Duration.ofHours(1)).getValidity();

    assertEquals(startTime, validity.start());
    assertEquals(startTime.plus(Duration.ofHours(1)), validity.end());
  }

  // -------------------------------------------------------------------------
  // isTemporaryAccessCondition.
  // -------------------------------------------------------------------------

  @Test
  public void whenExpressionIsNull_ThenIsTemporaryAccessConditionReturnsFalse() {
    assertFalse(TemporaryIamCondition.isTemporaryAccessCondition(null));
  }

  @Test
  public void whenExpressionIsEmpty_ThenIsTemporaryAccessConditionReturnsFalse() {
    assertFalse(TemporaryIamCondition.isTemporaryAccessCondition(""));
  }

  @Test
  public void whenExpressionIsTemporaryCondition_ThenIsTemporaryAccessConditionReturnsTrue() {
    var clause =
      "  (request.time >= timestamp(\"2020-01-01T00:00:00Z\") && "
        + "request.time < timestamp(\"2020-01-01T00:05:00Z\"))  ";

    assertTrue(TemporaryIamCondition.isTemporaryAccessCondition(clause));
  }

  @Test
  public void whenExpressionHasExcessParentheses_ThenIsTemporaryAccessConditionReturnsTrue() {
    var clause =
      "  (((request.time >= timestamp(\"2020-01-01T00:00:00Z\") && "
        + "request.time < timestamp(\"2020-01-01T00:05:00Z\"))))  ";

    assertTrue(TemporaryIamCondition.isTemporaryAccessCondition(clause));
  }

  @Test
  public void whenExpressionContainsMoreThanTemporaryCondition_ThenIsTemporaryAccessConditionReturnsFalse() {
    var clause =
      "  (request.time >= timestamp(\"2020-01-01T00:00:00Z\") && "
        + "request.time < timestamp(\"2020-01-01T00:05:00Z\")) && foo='foo' ";

    assertFalse(TemporaryIamCondition.isTemporaryAccessCondition(clause));
  }
}