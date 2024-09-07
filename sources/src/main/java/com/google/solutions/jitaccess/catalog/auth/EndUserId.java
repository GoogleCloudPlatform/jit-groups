//
// Copyright 2021 Google LLC
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

package com.google.solutions.jitaccess.catalog.auth;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.util.NullaryOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Principal identifier for an end user.
 * <p>
 * NB. The ID looks like an email address, but it might not
 *     be a route-able email address.
 */
public class EndUserId implements UserId {
  private static final @NotNull Pattern PATTERN = Pattern.compile("^user:(.+)@(.+)$");

  public static final String TYPE = "user";
  private static final String TYPE_PREFIX = TYPE + ":";

  public final @NotNull String email;

  public EndUserId(@NotNull String email) {
    Preconditions.checkNotNull(email, "email");
    Preconditions.checkArgument(!email.isBlank());

    //
    // Use lower-case as canonical format.
    //
    this.email = email.toLowerCase();
  }

  @Override
  public String toString() {
    return TYPE_PREFIX + this.email;
  }

  /**
   * Parse a user ID that uses the syntax <code>user:email</code>.
   */
  public static Optional<EndUserId> parse(@Nullable String s) {
    if (s == null || s.isBlank()) {
      return Optional.empty();
    }

    s = s.trim();

    var matcher = PATTERN.matcher(s.trim().toLowerCase());
    return NullaryOptional
      .ifTrue(matcher.matches())
      .map(() -> new EndUserId(matcher.group(1).trim() + "@" + matcher.group(2)));
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    EndUserId userId = (EndUserId) o;
    return this.email.equals(userId.email);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.email);
  }

  // -------------------------------------------------------------------------
  // PrincipalId.
  // -------------------------------------------------------------------------

  @Override
  public @NotNull String type() {
    return TYPE;
  }

  @Override
  public @NotNull String value() {
    return this.email;
  }
}
