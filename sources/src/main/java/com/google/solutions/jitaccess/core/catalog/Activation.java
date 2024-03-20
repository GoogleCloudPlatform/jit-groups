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
import com.google.solutions.jitaccess.cel.TimeSpan;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a successful activation of an entitlement.
 *
 * @param validity validity of the activation.
 */
public record Activation(
  @NotNull TimeSpan validity
) implements Comparable<Activation> {
  public Activation {
    Preconditions.checkNotNull(validity, "validity");
  }

  public Activation(@NotNull Instant start, @NotNull Duration duration) {
    this(new TimeSpan(start, start.plus(duration)));
  }

  @Override
  public int compareTo(@NotNull Activation o) {
    return this.validity.compareTo(o.validity());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Activation that = (Activation) o;
    return validity.equals(that.validity);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validity);
  }

  boolean isValid(Instant now) {
    return !this.validity.start().isAfter(now) && !this.validity.end().isBefore(now);
  }
}
