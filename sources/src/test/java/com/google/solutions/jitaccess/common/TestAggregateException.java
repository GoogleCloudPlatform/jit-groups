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

package com.google.solutions.jitaccess.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;

public class TestAggregateException {
  //---------------------------------------------------------------------------
  // getMessage.
  //---------------------------------------------------------------------------

  @Test
  public void getMessage_combinesExceptions() {
    assertEquals(
      "[one, two]",
      new AggregateException(
        new IllegalArgumentException("one"),
        new IllegalArgumentException("two")
      ).getMessage());
  }

  //---------------------------------------------------------------------------
  // toString.
  //---------------------------------------------------------------------------

  @Test
  public void toString_combinesExceptions() {
    var exception = new AggregateException(
      new IllegalArgumentException("one"),
      new IllegalArgumentException("two"));

    assertEquals(
      "[java.lang.IllegalArgumentException: one, java.lang.IllegalArgumentException: two]",
      exception.toString());
  }

  //---------------------------------------------------------------------------
  // getCauses.
  //---------------------------------------------------------------------------

  @Test
  public void getCauses_whenEmpty() {
    assertTrue(new AggregateException().getCauses().isEmpty());
  }

  @Test
  public void getCauses_whenNotEmpty() {
    var cause1 = new IllegalArgumentException("one");
    var cause2 = new IllegalArgumentException("two");

    var exception = new AggregateException(cause1, cause2);

    assertEquals(
      List.of(cause1, cause2),
      exception.getCauses());
  }

  //---------------------------------------------------------------------------
  // getCause.
  //---------------------------------------------------------------------------

  @Test
  public void getCause_whenEmpty() {
    assertNull(new AggregateException().getCause());
  }

  @Test
  public void getCause_whenSingleException() {
    var cause = new IllegalArgumentException("one");
    var exception = new AggregateException(cause);

    assertSame(cause, exception.getCause());
  }

  @Test
  public void getCause_whenMultipleExceptions() {
    var cause1 = new IllegalArgumentException("one");
    var cause2 = new IllegalArgumentException("two");
    var cause3 = new IllegalStateException("three");

    var exception = new AggregateException(cause1, cause2, cause3);

    assertSame(cause1, exception.getCause());
    assertEquals(
      List.of(cause2, cause3),
      List.of(exception.getCause().getSuppressed()));
    assertEquals(
      List.of(cause2, cause3),
      List.of(exception.getCause().getSuppressed()));
  }

  @Test
  public void getCause_unwrap_whenSingleException() {
    var cause = new IllegalArgumentException("one");

    var unwrapped = Exceptions.unwrap(
      new ExecutionException(
        new AggregateException(cause)));

    assertSame(cause, unwrapped);
    assertEquals(0, unwrapped.getSuppressed().length);
  }

  @Test
  public void getCause_unwrap_whenMultipleExceptions() {
    var cause1 = new IllegalArgumentException("one");
    var cause2 = new IllegalArgumentException("two");
    var cause3 = new IllegalStateException("three");

    var unwrapped = Exceptions.unwrap(
      new ExecutionException(
        new AggregateException(cause1, cause2, cause3)));

    assertSame(cause1, unwrapped);
    assertEquals(
      List.of(cause2, cause3),
      List.of(unwrapped.getSuppressed()));
    assertEquals(
      List.of(cause2, cause3),
      List.of(unwrapped.getSuppressed()));
  }
}
