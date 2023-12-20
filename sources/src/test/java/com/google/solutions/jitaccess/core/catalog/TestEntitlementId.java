//
// Copyright 2023 Google LLC
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

package com.google.solutions.jitaccess.core.catalog;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

public class TestEntitlementId {
  // -------------------------------------------------------------------------
  // hashCode.
  // -------------------------------------------------------------------------

  @Test
  public void whenIdIsEqual_ThenHashCodeIsEqual() {
    assertEquals(
      new SampleEntitlementId("cat", "1").hashCode(),
      new SampleEntitlementId("dog", "1").hashCode()
    );
  }

  // -------------------------------------------------------------------------
  // equals.
  // -------------------------------------------------------------------------

  @Test
  public void whenObjectAreEquivalent_ThenEqualsReturnsTrue() {
    SampleEntitlementId id1 = new SampleEntitlementId("cat", "jit-1");
    SampleEntitlementId id2 = new SampleEntitlementId("cat", "jit-1");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectAreSame_ThenEqualsReturnsTrue() {
    SampleEntitlementId id1 = new SampleEntitlementId("cat", "jit-1");

    assertTrue(id1.equals(id1));
  }

  @Test
  public void whenObjectAreNotEquivalent_ThenEqualsReturnsFalse() {
    SampleEntitlementId id1 = new SampleEntitlementId("cat", "jit-1");
    SampleEntitlementId id2 = new SampleEntitlementId("cat", "jit-2");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectIsNull_ThenEqualsReturnsFalse() {
    SampleEntitlementId id1 = new SampleEntitlementId("cat", "jit-1");

    assertFalse(id1.equals(null));
  }

  @Test
  public void whenObjectIsDifferentType_ThenEqualsReturnsFalse() {
    SampleEntitlementId id1 = new SampleEntitlementId("cat", "jit-1");

    assertFalse(id1.equals(""));
  }

  // -------------------------------------------------------------------------
  // compareTo.
  // -------------------------------------------------------------------------

  @Test
  public void compareToOrdersByCatalogThenId() {
    var ids = List.of(
      new SampleEntitlementId("b", "2"),
      new SampleEntitlementId("b", "1"),
      new SampleEntitlementId("a", "2"),
      new SampleEntitlementId("b", "3"),
      new SampleEntitlementId("a", "1"));

    var sorted = new TreeSet<SampleEntitlementId>();
    sorted.addAll(ids);

    Assertions.assertIterableEquals(
      List.of(
        new SampleEntitlementId("a", "1"),
        new SampleEntitlementId("a", "2"),
        new SampleEntitlementId("b", "1"),
        new SampleEntitlementId("b", "2"),
        new SampleEntitlementId("b", "3")),
      sorted);
  }
}
