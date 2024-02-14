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
 * @param currentEntitlements available and active entitlements
 * @param warnings encountered warnings, if any.
 */
public record EntitlementSet<TId extends EntitlementId>(
  SortedSet<Entitlement<TId>> currentEntitlements,
  Set<String> warnings
) {
  public EntitlementSet {
    Preconditions.checkNotNull(currentEntitlements, "currentEntitlements");
    Preconditions.checkNotNull(warnings, "warnings");
  }

  public static <T extends EntitlementId> EntitlementSet<T> build(
    Set<Entitlement<T>> availableEntitlements,
    Set<ActivatedEntitlement<T>> validActivations,
    Set<ActivatedEntitlement<T>> expiredActivations,
    Set<String> warnings
  )
  {
    assert availableEntitlements.stream().allMatch(e -> e.status() == Entitlement.Status.AVAILABLE);
    assert validActivations.stream().noneMatch(id -> expiredActivations.contains(id));
    assert expiredActivations.stream().noneMatch(id -> validActivations.contains(id));

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
    var availableAndInactive = availableEntitlements
      .stream()
      .filter(ent -> !validActivations
        .stream()
        .anyMatch(active -> active.entitlementId().equals(ent.id())))
      .collect(Collectors.toCollection(TreeSet::new));

    assert availableAndInactive.stream().noneMatch(e -> validActivations.contains(e.id()));

    var consolidatedSet = new TreeSet<Entitlement<T>>(availableAndInactive);
    for (var validActivation : validActivations) {
      //
      // Find the corresponding entitlement to determine
      // whether this is JIT or MPA-eligible.
      //
      var correspondingEntitlement = availableEntitlements
        .stream()
        .filter(ent -> ent.id().equals(validActivation.entitlementId()))
        .findFirst();
      if (correspondingEntitlement.isPresent()) {
        consolidatedSet.add(new Entitlement<>(
          validActivation.entitlementId(),
          correspondingEntitlement.get().name(),
          correspondingEntitlement.get().activationType(),
          Entitlement.Status.ACTIVE));
      }
      else {
        //
        // Active, but no longer available for activation.
        //
        consolidatedSet.add(new Entitlement<>(
          validActivation.entitlementId(),
          validActivation.entitlementId().id(),
          ActivationType.NONE,
          Entitlement.Status.ACTIVE));
      }

      //TODO: add validity to Entitlement
    }

    return new EntitlementSet<>(consolidatedSet, warnings);
  }

  public SortedSet<Entitlement<TId>> expiredEntitlements() {
    throw new RuntimeException("NIY"); // TODO
  }

  public static <TId extends EntitlementId> EntitlementSet<TId> empty() {
    return new EntitlementSet<TId>(new TreeSet<>(), Set.of());
  }

  public record ActivatedEntitlement<TId>(TId entitlementId, TimeSpan validity) {
    public ActivatedEntitlement {
      Preconditions.checkNotNull(entitlementId, "entitlementId");
      Preconditions.checkNotNull(validity, "validity");
    }
  }
}
