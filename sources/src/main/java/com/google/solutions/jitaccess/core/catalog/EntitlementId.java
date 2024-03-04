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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Unique identifier of an entitlement.
 */
public abstract class EntitlementId implements Comparable<EntitlementId> {
  /**
   * @return the catalog the entitlement belongs to
   */
  public abstract @NotNull String catalog();

  /**
   * @return the ID within the catalog
   */
  public abstract @NotNull String id();

  @Override
  public String toString() {
    return String.format("%s:%s", this.catalog(), this.id());
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    var that = (EntitlementId) o;
    return this.catalog().equals(that.catalog()) && this.id().equals(that.id());
  }

  @Override
  public int hashCode() {
    return id().hashCode();
  }

  @Override
  public int compareTo(@NotNull EntitlementId o) {
    return Comparator
      .comparing((EntitlementId e) -> e.catalog())
      .thenComparing(e -> e.id())
      .compare(this, o);
  }
}
