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

package com.google.solutions.jitaccess.catalog.auth;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;

/**
 * Identifier of a JIT group.
 *
 * A JitGroupId uniquely identifies a group across
 * all environments and systems.
 *
 * JitGroupIds are principals, but they can't be used for IAM.
 */
public class JitGroupId implements Comparable<JitGroupId>, PrincipalId {
  public static final String TYPE = "jit-group";

  private final @NotNull String environment;
  private final @NotNull String system;
  private final @NotNull String name;

  public JitGroupId(
    @Nullable String environment,
    @Nullable String system,
    @Nullable String name
  ) {
    Preconditions.checkArgument(
      environment != null && !environment.isBlank(),
      "environment must not be blank");
    Preconditions.checkArgument(
      system != null && !system.isBlank(),
      "system must not be blank");
    Preconditions.checkArgument(
      name != null && !name.isBlank(),
      "name must not be blank");

    //
    // All names must be lower-case as some backend
    // systems (in particular, group names) aren't case-sensitive.
    //

    Preconditions.checkArgument(
      environment.toLowerCase().equals(environment),
      "environment must be a lowe-case name");
    Preconditions.checkArgument(
      system.toLowerCase().equals(system),
      "system must be a lowe-case name");
    Preconditions.checkArgument(
      name.toLowerCase().equals(name),
      "name must be a lowe-case name");

    this.environment = environment;
    this.system = system;
    this.name = name;
  }

  /**
   * Environment that this group applies to.
   */
  public @NotNull String environment() {
    return environment;
  }

  /**
   * System that this group applies to.
   */
  public @NotNull String system() {
    return system;
  }

  /**
   * Name of the group.
   */
  public @NotNull String name() {
    return name;
  }

  @Override
  public String toString() {
    return this.value();
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

    var that = (JitGroupId)o;
    return
      this.environment.equals(that.environment) &&
      this.system.equals(that.system) &&
      this.name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return
      this.environment.hashCode() ^
      this.system.hashCode() ^
      this.name.hashCode();
  }

  @Override
  public int compareTo(@NotNull JitGroupId o) {
    return Comparator
      .comparing((JitGroupId r) -> r.environment)
      .thenComparing(r -> r.system)
      .thenComparing(r -> r.name)
      .compare(this, o);
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
    return String.format(
      "%s.%s.%s",
      this.environment,
      this.system,
      this.name);
  }

  // -------------------------------------------------------------------------
  // Parse.
  // -------------------------------------------------------------------------

  /**
   * Parse a string into a JIT Group ID.
   *
   * @return ID if parsing was successful, empty otherwise.
   */
  public static @NotNull Optional<JitGroupId> parse(@Nullable String s) {
    if (Strings.isNullOrEmpty(s) || s.isBlank()) {
      return Optional.empty();
    }

    var parts = s.split("\\.");
    if (parts.length != 3 ||
      parts[0].isBlank() ||
      parts[1].isBlank() ||
      parts[2].isBlank()) {
      return Optional.empty();
    }

    return Optional.of(new JitGroupId(parts[0], parts[1], parts[2]));
  }
}
