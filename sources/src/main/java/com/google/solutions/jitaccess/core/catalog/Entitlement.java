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

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Represents an entitlement. An entitlement is dormant unless the user
 * activates it, and it automatically becomes inactive again after a certain
 * period of time has elapsed.
 */
public record Entitlement<TEntitlementId extends EntitlementId> (
  @NotNull TEntitlementId id,
  @NotNull String name,
  @NotNull ActivationType activationType
) implements Comparable<Entitlement<TEntitlementId>> {
  public Entitlement {
    Preconditions.checkNotNull(id, "id");
    Preconditions.checkNotNull(name, "name");
  }

  @Override
  public String toString() {
    return this.name;
  }

  @Override
  public int compareTo(@NotNull Entitlement<TEntitlementId> o) {
    return Comparator
      .comparing((Entitlement<TEntitlementId> e) -> e.id)
      .compare(this, o);
  }
}
