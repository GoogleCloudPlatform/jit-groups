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
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Set of entitlements and associated activations.
 *
 * NB. The current/expired activations might refer to
 *     entitlements that are no longer available.
 *
 * @param available entitlements available to the user
 * @param currentActivations currently active entitlements
 * @param expiredActivations previously active (but now expired) entitlements
 * @param warnings encountered warnings, if any.
 */
public record EntitlementSet<TId extends EntitlementId>(
  @NotNull SortedSet<Entitlement<TId>> available,
  @NotNull Map<TId, Activation> currentActivations,
  @NotNull Map<TId, Activation> expiredActivations,
  @NotNull Set<String> warnings
) {
  public EntitlementSet {
    Preconditions.checkNotNull(available, "available");
    Preconditions.checkNotNull(currentActivations, "currentActivations");
    Preconditions.checkNotNull(expiredActivations, "expiredActivations");
    Preconditions.checkNotNull(warnings, "warnings");

    Preconditions.checkArgument(currentActivations.values().stream().allMatch(a -> a.isValid(Instant.now())));
    Preconditions.checkArgument(expiredActivations.values().stream().allMatch(a -> !a.isValid(Instant.now())));
  }

  public static <TId extends EntitlementId> @NotNull EntitlementSet<TId> empty() {
    return new EntitlementSet<TId>(new TreeSet<>(), Map.of(), Map.of(), Set.of());
  }
}
