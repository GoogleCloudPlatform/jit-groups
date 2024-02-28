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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Pseudo-principal identifier for a class of users.
 */
public class UserClassId implements PrincipalId, Comparable<UserClassId> {
  public static final String TYPE = "class";
  private static final String TYPE_PREFIX = TYPE + ":";

  private final @NotNull String value;

  /**
   * Principal identifier that identifies all users.
   */
  public static final @NotNull UserClassId AUTHENTICATED_USERS = new UserClassId("authenticatedUsers");

  private UserClassId(@NotNull String value) {
    this.value = value;
  }

  /**
   * Parse a user ID that uses prefixed syntax.
   */
  public static Optional<UserClassId> parse(@Nullable String s) {
    if (s == null || s.isBlank()) {
      return Optional.empty();
    }

    s = s.trim();

    if (s.equalsIgnoreCase(AUTHENTICATED_USERS.toString())) {
      return Optional.of(AUTHENTICATED_USERS);
    }
    else {
      return Optional.empty();
    }
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
    return this.value;
  }

  @Override
  public int hashCode() {
    return this.value.hashCode();
  }

  @Override
  public String toString() {
    return TYPE_PREFIX + this.value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    UserClassId id = (UserClassId) o;
    return value.equals(id.value);
  }

  @Override
  public int compareTo(@NotNull UserClassId o) {
    return this.value.compareTo(o.value());
  }
}
