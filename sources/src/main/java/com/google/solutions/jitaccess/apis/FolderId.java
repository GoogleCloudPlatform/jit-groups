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
 * Identifier for a Google Cloud folder.
 */
public record FolderId(
  @NotNull String id
) implements Comparable<FolderId>, ResourceId {
  static final String PREFIX = "folders/";

  public FolderId {
    Preconditions.checkNotNull(id, "id");
    assert !id.startsWith("//");
    assert !id.contains("/");
  }

  /**
   * Parse a folder ID from one of the formats
   *
   * <ul>
   *   <li>folders/123/li>
   *   <li>123</li>
   * </ul>
   *
   * @return empty if the input string is malformed.
   */
  public static @NotNull Optional<FolderId> parse(@Nullable String s) {
    if (s == null) {
      return Optional.empty();
    }

    s = s.trim();

    if (s.startsWith(PREFIX) &&
      s.indexOf('/', PREFIX.length()) == -1 &&
      s.length() > PREFIX.length()) {
      //
      // String has folders/ prefix, strip.
      //
      s = s.substring(PREFIX.length());
    }

    if (!s.isEmpty() && s.indexOf('/') == -1 && s.chars().allMatch(Character::isDigit)) {
      return Optional.of(new FolderId(s));
    }
    else {
      return Optional.empty();
    }
  }

  // -------------------------------------------------------------------------
  // Comparable.
  // -------------------------------------------------------------------------

  @Override
  public int compareTo(@NotNull FolderId o) {
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
    return "folder";
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