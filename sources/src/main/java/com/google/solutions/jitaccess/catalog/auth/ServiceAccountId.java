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
import com.google.solutions.jitaccess.apis.ProjectId;
import com.google.solutions.jitaccess.util.NullaryOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Principal identifier for a service account.
 */
public class ServiceAccountId implements IamPrincipalId {
  static final @NotNull Pattern PATTERN =
    Pattern.compile("^serviceaccount:(.+)@(.+).iam.gserviceaccount.com$");

  public static final String TYPE = "serviceAccount";
  private static final String TYPE_PREFIX = TYPE + ":";

  public final @NotNull String id;
  public final @NotNull ProjectId projectId;

  public ServiceAccountId(@NotNull String id, @NotNull ProjectId projectId) {
    Preconditions.checkNotNull(id, "id");
    Preconditions.checkNotNull(projectId, "projectId");
    Preconditions.checkArgument(!id.isBlank());

    //
    // Use lower-case as canonical format.
    //
    this.id = id.toLowerCase();
    this.projectId = projectId;
  }

  @Override
  public String toString() {
    return TYPE_PREFIX + this.value();
  }

  /**
   * Project that contains the service account.
   */
  public ProjectId projectId() {
    return this.projectId;
  }

  /**
   *  Return email of service account.
   */
  public String email() {
    return String.format("%s@%s.iam.gserviceaccount.com", this.id, this.projectId);
  }

  /**
   * Parse a user ID that uses the syntax serviceAccount:email.
   */
  public static Optional<ServiceAccountId> parse(@Nullable String s) {
    if (s == null || s.isBlank()) {
      return Optional.empty();
    }

    var matcher = PATTERN.matcher(s.trim().toLowerCase());
    return NullaryOptional
      .ifTrue(matcher.matches())
      .map(() -> new ServiceAccountId(matcher.group(1), new ProjectId(matcher.group(2))));
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

    ServiceAccountId other = (ServiceAccountId) o;
    return this.id.equals(other.id) && this.projectId.equals(other.projectId);
  }

  @Override
  public int hashCode() {
    return this.id.hashCode() ^ this.projectId.hashCode();
  }

  // -------------------------------------------------------------------------
  // PrincipalId.
  // -------------------------------------------------------------------------

  @Override
  public @NotNull String type() {
    return TYPE;
  }

  /**
   * Return email of service account.
   */
  @Override
  public @NotNull String value() {
    return email();
  }
}

