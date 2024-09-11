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

package com.google.solutions.jitaccess;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public abstract class TestRecord<T> {
  /**
   * Create an instance.
   */
  protected abstract @NotNull T createInstance();

  /**
   * Create an instance that isn't equal to the one created by
   * createInstance.
   */
  protected abstract @NotNull T createDifferentInstance();

  //---------------------------------------------------------------------
  // equals.
  //---------------------------------------------------------------------

  @Test
  public void equals_whenObjectAreEquivalent() {
    T obj1 = createInstance();
    T obj2 = createInstance();

    assertTrue(obj1.equals(obj2));
    assertEquals(obj1.hashCode(), obj2.hashCode());
  }

  @Test
  public void equals_whenObjectAreSame() {
    T obj1 = createInstance();

    assertTrue(obj1.equals(obj1));
  }

  @Test
  public void equals_whenObjectAreMotEquivalent() {
    T obj1 = createInstance();
    T obj2 = createDifferentInstance();

    assertFalse(obj1.equals(obj2));
    assertNotEquals(obj1.hashCode(), obj2.hashCode());
  }

  @Test
  public void equals_whenObjectIsNull() {
    T obj1 = createInstance();

    assertFalse(obj1.equals(null));
  }

  @Test
  public void equals_whenObjectIsDifferentType() {
    T obj1 = createInstance();

    assertFalse(obj1.equals(""));
  }

  //---------------------------------------------------------------------
  // toString.
  //---------------------------------------------------------------------

  @Test
  public void toString_whenObjectAreEquivalent() {
    T obj1 = createInstance();
    T obj2 = createInstance();

    assertEquals(obj1.toString(), obj2.toString());
  }

}
