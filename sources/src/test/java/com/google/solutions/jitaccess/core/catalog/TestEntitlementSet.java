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

package com.google.solutions.jitaccess.core.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class TestEntitlementSet {
  private class StringId extends EntitlementId {
    private final String id;

    public StringId(String id) {
      this.id = id;
    }

    @Override
    public String catalog() {
      return "test";
    }

    @Override
    public String id() {
      return this.id;
    }
  }

  // -------------------------------------------------------------------------
  // allEntitlements.
  // -------------------------------------------------------------------------

  @Test
  public void whenActiveIsEmpty_ThenAllEntitlementsReturnsConsolidatedSet() {
    var available1 = new Entitlement<StringId>(
      new StringId("available-1"),
      "available-1",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);
    var available2 = new Entitlement<StringId>(
      new StringId("available-2"),
      "available-2",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);

    var set = new EntitlementSet<StringId>(
      Set.of(available1, available2),
      Set.of(),
      Set.of());

    assertEquals(Set.of(available1, available2), set.availableEntitlements());
    assertEquals(Set.of(), set.activeEntitlementIds());
    assertIterableEquals(List.of(available1, available2), set.allEntitlements());
  }

  @Test
  public void whenOneEntitlementActive_ThenAllEntitlementsReturnsConsolidatedSet() {
    var available1 = new Entitlement<StringId>(
      new StringId("available-1"),
      "available-1",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);
    var available2 = new Entitlement<StringId>(
      new StringId("available-2"),
      "available-2",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);

    var set = new EntitlementSet<StringId>(
      Set.of(available1, available2),
      Set.of(available1.id()),
      Set.of());

    assertEquals(Set.of(available1, available2), set.availableEntitlements());
    assertEquals(Set.of(available1.id()), set.activeEntitlementIds());
    assertIterableEquals(List.of(
      available2,
      new Entitlement<StringId>(
        new StringId("available-1"),
        "available-1",
        ActivationType.JIT,
        Entitlement.Status.ACTIVE)),
      set.allEntitlements());
  }

  @Test
  public void whenAllEntitlementsActive_ThenAllEntitlementsReturnsConsolidatedSet() {
    var available1 = new Entitlement<StringId>(
      new StringId("available-1"),
      "available-1",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);
    var available2 = new Entitlement<StringId>(
      new StringId("available-2"),
      "available-2",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);

    var set = new EntitlementSet<StringId>(
      Set.of(available1, available2),
      Set.of(available1.id(), available2.id()),
      Set.of());

    assertEquals(Set.of(available1, available2), set.availableEntitlements());
    assertEquals(Set.of(available1.id(), available2.id()), set.activeEntitlementIds());
    assertIterableEquals(List.of(
        new Entitlement<StringId>(
          new StringId("available-1"),
          "available-1",
          ActivationType.JIT,
          Entitlement.Status.ACTIVE),
        new Entitlement<StringId>(
          new StringId("available-2"),
          "available-2",
          ActivationType.JIT,
          Entitlement.Status.ACTIVE)),
      set.allEntitlements());
  }

  @Test
  public void whenUnavailableEntitlementsIsActive_ThenAllEntitlementsReturnsConsolidatedSet() {
    var available1 = new Entitlement<StringId>(
      new StringId("available-1"),
      "available-1",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);
    var available2 = new Entitlement<StringId>(
      new StringId("available-2"),
      "available-2",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);

    var set = new EntitlementSet<StringId>(
      Set.of(available1, available2),
      Set.of(new StringId("unavailable-1")),
      Set.of());

    assertEquals(Set.of(available1, available2), set.availableEntitlements());
    assertIterableEquals(List.of(
        available1,
        available2,
        new Entitlement<StringId>(
          new StringId("unavailable-1"),
          "unavailable-1",
          ActivationType.NONE,
          Entitlement.Status.ACTIVE)),
      set.allEntitlements());
  }
}
