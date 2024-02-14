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

import com.google.solutions.jitaccess.cel.TimeSpan;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
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
  // CurrentEntitlements.
  // -------------------------------------------------------------------------

  @Test
  public void whenActiveIsEmpty_ThenCurrentEntitlementsHaveRightStatus() {
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

    var set = EntitlementSet.build(
      Set.of(available1, available2),
      Set.of(),
      Set.of(),
      Set.of());

    assertIterableEquals(
      List.of(available1, available2),
      set.currentEntitlements());
  }

  @Test
  public void whenOneEntitlementActive_ThenCurrentEntitlementsHaveRightStatus() {
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

    var set = EntitlementSet.build(
      Set.of(available1, available2),
      Set.of(new EntitlementSet.ActivatedEntitlement<>(
        available1.id(),
        new TimeSpan(Instant.now(), Duration.ZERO))),
      Set.of(),
      Set.of());

    assertIterableEquals(List.of(
      available2,
      new Entitlement<StringId>(
        new StringId("available-1"),
        "available-1",
        ActivationType.JIT,
        Entitlement.Status.ACTIVE)),
      set.currentEntitlements());
  }

  @Test
  public void whenAllEntitlementsActive_ThenCurrentEntitlementsHaveRightStatus() {
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

    var set = EntitlementSet.build(
      Set.of(available1, available2),
      Set.of(
        new EntitlementSet.ActivatedEntitlement<>(
          available1.id(),
          new TimeSpan(Instant.now(), Duration.ZERO)),
        new EntitlementSet.ActivatedEntitlement<>(
          available2.id(),
          new TimeSpan(Instant.now(), Duration.ZERO))),
      Set.of(),
      Set.of());

    assertIterableEquals(
      List.of(
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
      set.currentEntitlements());
  }

  @Test
  public void whenUnavailableEntitlementsIsActive_ThenCurrentEntitlementsHaveRightStatus() {
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

    var set = EntitlementSet.build(
      Set.of(available1, available2),
      Set.of(new EntitlementSet.ActivatedEntitlement<>(
        new StringId("unavailable-1"),
        new TimeSpan(Instant.now(), Duration.ZERO))),
      Set.of(),
      Set.of());

    assertIterableEquals(List.of(
        available1,
        available2,
        new Entitlement<StringId>(
          new StringId("unavailable-1"),
          "unavailable-1",
          ActivationType.NONE,
          Entitlement.Status.ACTIVE)),
      set.currentEntitlements());
  }
}
