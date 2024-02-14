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

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.cel.TimeSpan;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Set of entitlements
 *
 * @param availableEntitlements available entitlements, regardless of status
 * @param activeEntitlementIds IDs of active entitlements (subset of available entitlements)
 * @param expiredEntitlementIds IDs of expired entitlements (subset of available entitlements)
 * @param warnings encountered warnings, if any.
 */
public record EntitlementSet<TId extends EntitlementId>(
  Set<Entitlement<TId>> availableEntitlements,
  Set<IdAndValidity<TId>> activeEntitlementIds, //TODO: Strip suffix
  Set<IdAndValidity<TId>> expiredEntitlementIds,
  Set<String> warnings
) {
  public EntitlementSet {
    Preconditions.checkNotNull(availableEntitlements, "availableEntitlements");
    Preconditions.checkNotNull(activeEntitlementIds, "activeEntitlementIds");
    Preconditions.checkNotNull(expiredEntitlementIds, "expiredEntitlementIds");
    Preconditions.checkNotNull(warnings, "warnings");

    assert availableEntitlements.stream().allMatch(e -> e.status() == Entitlement.Status.AVAILABLE);
    assert activeEntitlementIds.stream().noneMatch(id -> expiredEntitlementIds.contains(id));
    assert expiredEntitlementIds.stream().noneMatch(id -> activeEntitlementIds.contains(id));
  }

  /**
   * @return all entitlements that are active or can be activated.
   */
  public SortedSet<Entitlement<TId>> currentEntitlements() {
    //
    // Return a set containing:
    //
    //  1. Available entitlements
    //  2. Active entitlements
    //
    // where (1) and (2) don't overlap.
    //
    // Expired entitlements are ignored.
    //
    var availableAndInactive = this.availableEntitlements
      .stream()
      .filter(ent -> !this.activeEntitlementIds
        .stream()
        .anyMatch(active -> active.id().equals(ent.id())))
      .collect(Collectors.toCollection(TreeSet::new));

    assert availableAndInactive.stream().noneMatch(e -> this.activeEntitlementIds.contains(e.id()));

    var consolidatedSet = new TreeSet<Entitlement<TId>>(availableAndInactive);
    for (var activeEntitlementId : this.activeEntitlementIds) {
      //
      // Find the corresponding entitlement to determine
      // whether this is JIT or MPA-eligible.
      //
      var correspondingEntitlement = this.availableEntitlements
        .stream()
        .filter(ent -> ent.id().equals(activeEntitlementId.id()))
        .findFirst();
      if (correspondingEntitlement.isPresent()) {
        consolidatedSet.add(new Entitlement<>(
          activeEntitlementId.id(),
          correspondingEntitlement.get().name(),
          correspondingEntitlement.get().activationType(),
          Entitlement.Status.ACTIVE));
      }
      else {
        //
        // Active, but no longer available for activation.
        //
        consolidatedSet.add(new Entitlement<>(
          activeEntitlementId.id(),
          activeEntitlementId.id().id(),
          ActivationType.NONE,
          Entitlement.Status.ACTIVE));
      }

      //TODO: add validity to Entitlement
    }

    return consolidatedSet;
  }

  public SortedSet<Entitlement<TId>> expiredEntitlements() {
    throw new RuntimeException("NIY"); // TODO
  }

  public static <TId extends EntitlementId> EntitlementSet<TId> empty() {
    return new EntitlementSet<TId>(new TreeSet<>(), Set.of(), Set.of(), Set.of());
  }

  public record IdAndValidity<TId>(TId id, TimeSpan validity) {
    public IdAndValidity {
      Preconditions.checkNotNull(id, "id");
      Preconditions.checkNotNull(validity, "validity");
    }
  }
}
