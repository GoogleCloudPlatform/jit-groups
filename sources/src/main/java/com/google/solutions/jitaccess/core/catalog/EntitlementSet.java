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
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Set of entitlements
 *
 * @param currentEntitlements available and active entitlements
 * @param expiredEntitlements previously active entitlements
 * @param warnings encountered warnings, if any.
 */
public record EntitlementSet<TId extends EntitlementId>(
  SortedSet<Entitlement<TId>> currentEntitlements, // TODO(later): rename to available
  SortedSet<Entitlement<TId>> expiredEntitlements,
  Set<String> warnings
) {
  public EntitlementSet {
    Preconditions.checkNotNull(currentEntitlements, "currentEntitlements");
    Preconditions.checkNotNull(warnings, "warnings");

    assert currentEntitlements.stream().allMatch(e -> e.status() != Entitlement.Status.EXPIRED);
    assert expiredEntitlements.stream().allMatch(e -> e.status() == Entitlement.Status.EXPIRED);
  }

  public static <T extends EntitlementId> @NotNull EntitlementSet<T> build(
    @NotNull Set<Entitlement<T>> availableEntitlements,
    @NotNull Set<ActivatedEntitlement<T>> validActivations,
    @NotNull Set<ActivatedEntitlement<T>> expiredActivations,
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

    var current = new TreeSet<Entitlement<T>>(availableAndInactive);
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
        current.add(new Entitlement<>(
          validActivation.entitlementId(),
          correspondingEntitlement.get().name(),
          correspondingEntitlement.get().activationType(),
          Entitlement.Status.ACTIVE,
          validActivation.validity));
      }
      else {
        //
        // Active, but no longer available for activation.
        //
        current.add(new Entitlement<>(
          validActivation.entitlementId(),
          validActivation.entitlementId().id(),
          ActivationType.NONE,
          Entitlement.Status.ACTIVE,
          validActivation.validity));
      }
    }

    var expired = new TreeSet<Entitlement<T>>();
    for (var expiredActivation : expiredActivations) {
      //
      // Find the corresponding entitlement to determine
      // whether this is currently (*) JIT or MPA-eligible.
      //
      // (*) it might have changed in the meantime, but that's ok.
      //
      var correspondingEntitlement = availableEntitlements
        .stream()
        .filter(ent -> ent.id().equals(expiredActivation.entitlementId()))
        .findFirst();
      if (correspondingEntitlement.isPresent()) {
        expired.add(new Entitlement<>(
          expiredActivation.entitlementId(),
          correspondingEntitlement.get().name(),
          correspondingEntitlement.get().activationType(),
          Entitlement.Status.EXPIRED,
          expiredActivation.validity));
      }
      else {
        //
        // Active, but no longer available for activation.
        //
        expired.add(new Entitlement<>(
          expiredActivation.entitlementId(),
          expiredActivation.entitlementId().id(),
          ActivationType.NONE,
          Entitlement.Status.EXPIRED,
          expiredActivation.validity));
      }
    }

    return new EntitlementSet<>(current, expired, warnings);
  }

  public static <TId extends EntitlementId> @NotNull EntitlementSet<TId> empty() {
    return new EntitlementSet<TId>(new TreeSet<>(), new TreeSet<>(), Set.of());
  }

  public record ActivatedEntitlement<TId>(TId entitlementId, TimeSpan validity) {
    public ActivatedEntitlement {
      Preconditions.checkNotNull(entitlementId, "entitlementId");
      Preconditions.checkNotNull(validity, "validity");
    }
  }
}
