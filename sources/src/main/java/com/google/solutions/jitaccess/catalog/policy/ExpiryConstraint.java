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

package com.google.solutions.jitaccess.catalog.policy;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.util.DurationFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Constraint that requires group memberships to have
 * an expiry. The expiry can be pre-defined (fixed) or
 * user-defined (within a defined range).
 */
public class ExpiryConstraint implements Constraint {
  /**
   * Name of property. The leading underscore prevents
   * collisions with any user-defined properties.
   */
  public static final @NotNull String NAME = "_expiry";

  private final @NotNull Duration minDuration;
  private final @NotNull Duration maxDuration;

  public ExpiryConstraint(
    @NotNull Duration minDuration,
    @NotNull Duration maxDuration
  ) {
    Preconditions.checkArgument(
      minDuration.toMinutes() <= maxDuration.toMinutes(),
      "Minimum duration must not exceed maximum duration");
    Preconditions.checkArgument(
      !minDuration.isNegative(),
      "Minimum duration must be positive");
    Preconditions.checkArgument(
      !maxDuration.isNegative(),
      "Maximum duration must be positive");

    this.minDuration = minDuration;
    this.maxDuration = maxDuration;
  }

  public ExpiryConstraint(@NotNull Duration duration) {
    this(duration, duration);
  }

  /**
   * @return minimum duration.
   */
  public @NotNull Duration minDuration() {
    return minDuration;
  }

  /**
   * @return maximum duration.
   */
  public @NotNull Duration maxDuration() {
    return maxDuration;
  }

  /**
   * Check if the user can choose an expiry or
   * if it's fixed.
   */
  public boolean isFixedDuration() {
    return this.minDuration.equals(this.maxDuration);
  }

  @Override
  public @NotNull String name() {
    return NAME;
  }

  @Override
  public @NotNull String displayName() {
    if (isFixedDuration()) {
      return String.format(
        "Membership expires after %s",
        DurationFormatter.format(this.maxDuration));
    }
    else {
      return String.format(
        "You must choose an expiry between %s and %s",
        DurationFormatter.format(this.minDuration),
        DurationFormatter.format(this.maxDuration));
    }
  }

  @Override
  public String toString() {
    return displayName();
  }

  @Override
  public Check createCheck() {
    return new Check() {
      private @Nullable Duration userProvidedDuration = null;

      @Override
      public @NotNull Constraint constraint() {
        return ExpiryConstraint.this;
      }

      @Override
      public @NotNull List<Property> input() {
        if(isFixedDuration()) {
          //
          // No input needed.
          //
          return List.of();
        }
        else {
          return List.of(new AbstractDurationProperty(NAME, "Expiry", true, minDuration, maxDuration) {
            @Override
            protected void setCore(@Nullable Duration value) {
              userProvidedDuration = value;
            }

            @Override
            protected @Nullable Duration getCore() {
              return userProvidedDuration;
            }
          });
        }
      }

      @Override
      public @NotNull Context addContext(@NotNull String name) {
        //
        // We don't use contexts.
        //
        return new Context() {
          @Override
          public Context set(@NotNull String name, @NotNull Object val) {
            return this;
          }
        };
      }

      @Override
      public @NotNull Boolean evaluate() {
        if (isFixedDuration()) {
          return true;
        }
        else {
          //
          // Verify that the user-provided duration is within range.
          //
          return userProvidedDuration != null &&
            userProvidedDuration.toMinutes() >= minDuration.toMinutes() &&
            userProvidedDuration.toMinutes() <= maxDuration.toMinutes();
        }
      }
    };
  }

  public @NotNull Optional<Duration> extractExpiry(
    @NotNull Collection<Property> input
    ) {
    if (isFixedDuration()) {
      return Optional.of(this.minDuration);
    }
    else {
      return input
        .stream()
        .filter(p -> p instanceof AbstractDurationProperty && p.name().equals(NAME))
        .map(p -> (AbstractDurationProperty)p)
        .map(p -> p.getCore())
        .filter(v -> v != null)
        .findFirst();
    }
  }
}
