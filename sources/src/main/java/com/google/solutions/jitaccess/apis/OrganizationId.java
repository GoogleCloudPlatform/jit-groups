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

package com.google.solutions.jitaccess.apis;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.clients.ResourceManagerClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Identifier for a Google Cloud organization.
 */
public record OrganizationId(
  @NotNull String id
) implements Comparable<OrganizationId>, ResourceId {
  static final String PREFIX = "organizations/";

  public OrganizationId {
    Preconditions.checkNotNull(id, "id");
    assert !id.startsWith("//");
    assert !id.contains("/");
  }

  /**
   * Parse an organization ID from one of the formats
   *
   *  <ul>
   *   <li>organizations/123</li>
   *   <li>123</li>
   * </ul>
   *
   * @return empty if the input string is malformed.
   */
  public static @NotNull Optional<OrganizationId> parse(@Nullable String s) {
    if (s == null) {
      return Optional.empty();
    }

    s = s.trim();

    if (s.startsWith(PREFIX) &&
      s.indexOf('/', PREFIX.length()) == -1 &&
      s.length() > PREFIX.length()) {
      //
      // String has organizations/ prefix, strip.
      //
      s = s.substring(PREFIX.length());
    }

    if (!s.isEmpty() && s.indexOf('/') == -1 && s.chars().allMatch(Character::isDigit)) {
      return Optional.of(new OrganizationId(s));
    }
    else {
      return Optional.empty();
    }
  }

  // -------------------------------------------------------------------------
  // Comparable.
  // -------------------------------------------------------------------------

  @Override
  public int compareTo(@NotNull OrganizationId o) {
    return this.id.compareTo(o.id);
  }

  // -------------------------------------------------------------------------
  // ResourceId.
  // -------------------------------------------------------------------------

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull String service() {
    return ResourceManagerClient.SERVICE;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull String type() {
    return "organization";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull String path() {
    return PREFIX + this.id;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return this.id;
  }
}