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

import java.util.Map;
import java.util.Optional;

/**
 * Principal set that matches a class of users.
 */
public class ClassPrincipalSet implements PrincipalId, Comparable<ClassPrincipalSet> {
  public static final String TYPE = "class";
  private static final String TYPE_PREFIX = TYPE + ":";

  private final @NotNull String value;

  /**
   * Principal identifier that identifies all users that
   * have been authorized by IAP.
   */
  public static final @NotNull ClassPrincipalSet IAP_USERS = new ClassPrincipalSet("iapUsers");

  /**
   * Principal identifier that identifies all users that
   * belong to the "internal" Cloud Identity/Workspace account, i.e.,
   * the account that this instance of JIT Groups is associated with.
   *
   * Consumer accounts and service accounts are not considered
   * internal.
   */
  public static final @NotNull ClassPrincipalSet INTERNAL_USERS = new ClassPrincipalSet("internalUsers");

  /**
   * Principal identifier that identifies all users that
   * do not belong to the internal Cloud Identity/Workspace account,
   * including consumer accounts and service accounts.
   */
  public static final @NotNull ClassPrincipalSet EXTERNAL_USERS = new ClassPrincipalSet("externalUsers");

  private static final Map<String, ClassPrincipalSet> PARSE_MAP = Map.of(
    IAP_USERS.toString().toLowerCase(), IAP_USERS,
    INTERNAL_USERS.toString().toLowerCase(), INTERNAL_USERS,
    EXTERNAL_USERS.toString().toLowerCase(), EXTERNAL_USERS);

  @SuppressWarnings("SameParameterValue")
  private ClassPrincipalSet(@NotNull String value) {
    this.value = value;
  }

  /**
   * Parse a user ID that uses prefixed syntax.
   */
  public static Optional<ClassPrincipalSet> parse(@Nullable String s) {
    if (s == null || s.isBlank()) {
      return Optional.empty();
    }

    return Optional.ofNullable(PARSE_MAP.get(s.trim().toLowerCase()));
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

    ClassPrincipalSet id = (ClassPrincipalSet) o;
    return this.value.equals(id.value);
  }

  @Override
  public int compareTo(@NotNull ClassPrincipalSet o) {
    return this.value.compareTo(o.value());
  }
}
