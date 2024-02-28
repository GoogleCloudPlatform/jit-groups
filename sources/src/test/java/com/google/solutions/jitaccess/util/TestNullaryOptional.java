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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TestNullaryOptional {
  //---------------------------------------------------------------------------
  // ifTrue.
  //---------------------------------------------------------------------------

  @Test
  public void ifTrue_whenTrue() {
    assertTrue(NullaryOptional.ifTrue(true).isPresent());
    assertFalse(NullaryOptional.ifTrue(true).isEmpty());
  }

  @Test
  public void ifTrue_whenFalse() {
    assertFalse(NullaryOptional.ifTrue(false).isPresent());
    assertTrue(NullaryOptional.ifTrue(false).isEmpty());
  }

  //---------------------------------------------------------------------------
  // map.
  //---------------------------------------------------------------------------

  @Test
  public void map_whenTrue() {
    assertEquals(
      Optional.of("test"),
      NullaryOptional.ifTrue(true).map(() -> "test"));
  }

  @Test
  public void map_whenFalse() {
    assertFalse(NullaryOptional.ifTrue(false).map(() -> "test").isPresent());
  }

  //---------------------------------------------------------------------------
  // flatMap.
  //---------------------------------------------------------------------------

  @Test
  public void flatMap_whenNotEmpty() {
    assertEquals(
      Optional.of("test"),
      NullaryOptional
        .ifTrue(true)
        .flatMap(() -> Optional.of("test")));
  }

  @Test
  public void map_whenEmpty() {
    assertFalse(NullaryOptional
      .ifTrue(false)
      .flatMap(() -> Optional.empty()).isPresent());
  }
}
