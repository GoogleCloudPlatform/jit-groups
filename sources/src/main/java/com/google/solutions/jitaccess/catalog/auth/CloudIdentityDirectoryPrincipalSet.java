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
import com.google.solutions.jitaccess.apis.Domain;
import com.google.solutions.jitaccess.util.NullaryOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Principal set that matches all users of a
 * Cloud Identity/Workspace account, equivalent to the
 * <code>domain:</code> principal identifier used by IAM.
 */
public class CloudIdentityDirectoryPrincipalSet implements PrincipalId {
  private static final @NotNull Pattern PATTERN = Pattern.compile("^domain:([^:]+)$");

  public static final String TYPE = "domain";
  private static final String TYPE_PREFIX = TYPE + ":";

  private final @NotNull String primaryDomain;

  CloudIdentityDirectoryPrincipalSet(@NotNull String primaryDomain) {
    this.primaryDomain = primaryDomain;
  }

  CloudIdentityDirectoryPrincipalSet(@NotNull Directory directory) {
    Preconditions.checkArgument(directory.type() == Directory.Type.CLOUD_IDENTITY);
    Preconditions.checkNotNull(directory.hostedDomain());
    Preconditions.checkArgument(directory.hostedDomain().type() == Domain.Type.PRIMARY);

    this.primaryDomain = directory.hostedDomain().name();
  }

  @Override
  public String toString() {
    return TYPE_PREFIX + this.primaryDomain;
  }

  /**
   * Get the (primary) domain identified by this principal set.
   */
  public @NotNull Domain domain() {
    return new Domain(this.primaryDomain, Domain.Type.PRIMARY);
  }

  /**
   * Parse a group ID that uses the syntax <code>domain:PRIMARY_DOMAIN</code>.
   */
  public static Optional<CloudIdentityDirectoryPrincipalSet> parse(@Nullable String s) {
    if (s == null || s.isBlank()) {
      return Optional.empty();
    }

    s = s.trim();

    var matcher = PATTERN.matcher(s.trim().toLowerCase());
    return NullaryOptional
      .ifTrue(matcher.matches())
      .map(() -> new CloudIdentityDirectoryPrincipalSet(matcher.group(1).trim()));
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

    CloudIdentityDirectoryPrincipalSet set = (CloudIdentityDirectoryPrincipalSet) o;
    return this.primaryDomain.equals(set.primaryDomain);
  }

  @Override
  public int hashCode() {
    return this.primaryDomain.hashCode();
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
    return this.primaryDomain;
  }
}
