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

package com.google.solutions.jitaccess.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestAggregateException {
  @Test
  public void constructor_whenListEmpty() {
    var exception = new AggregateException();
    assertNull(exception.getCause());
    assertEquals(0, exception.getCauses().size());
  }

  @Test
  public void getMessage_combinesExceptions() {
    assertEquals(
      "[one, two]",
      new AggregateException(
        new IllegalArgumentException("one"),
        new IllegalArgumentException("two")
      ).getMessage());
  }

  @Test
  public void toString_combinesExceptions() {
    assertEquals(
      "[java.lang.IllegalArgumentException: one, java.lang.IllegalArgumentException: two]",
      new AggregateException(
        new IllegalArgumentException("one"),
        new IllegalArgumentException("two")
      ).toString());
  }
}
