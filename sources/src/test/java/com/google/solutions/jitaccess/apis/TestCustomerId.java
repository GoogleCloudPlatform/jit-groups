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

package com.google.solutions.jitaccess.apis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestCustomerId {
  @Test
  public void constructor_whenMalformed() {
    assertThrows(
      IllegalArgumentException.class,
      () -> new CustomerId("123"));
  }

  @Test
  public void toString_returnsId() {
    assertEquals(
      "C123",
      new CustomerId("C123").toString());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void equals_whenObjectAreEquivalent() {
    CustomerId id1 = new CustomerId("C1");
    CustomerId id2 = new CustomerId("C1");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void equals_whenObjectAreSame() {
    CustomerId id1 = new CustomerId("C1");

    assertTrue(id1.equals(id1));
  }

  @Test
  public void equals_whenObjectAreMotEquivalent() {
    CustomerId id1 = new CustomerId("C1");
    CustomerId id2 = new CustomerId("C2");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void equals_whenObjectIsNull() {
    CustomerId id1 = new CustomerId("C1");

    assertFalse(id1.equals(null));
  }

  @Test
  public void equals_whenObjectIsDifferentType() {
    CustomerId id1 = new CustomerId("C1");

    assertFalse(id1.equals(""));
  }
}
