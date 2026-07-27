//
// Copyright 2026 Google LLC
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
import com.google.common.base.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Gatherers;

/**
 * Identifier for a Google Cloud region.
 */
public record RegionId(@NotNull String id) {

  public RegionId {
    Preconditions.checkNotNull(id, "id");
    assert !id.startsWith("//");
    assert !id.contains("/");
  }

  /**
   * Parse a region ID from one of the formats
   *
   * <ul>
   *   <li>region-id</li>
   *   <li>projects/123456789/regions/region-id</li>
   * </ul>
   *
   * @return empty if the input string is malformed.
   */
  public static @NotNull Optional<RegionId> parse(@Nullable String s) {
    if (s == null) {
      return Optional.empty();
    }

    s = s.trim().toLowerCase();

    if (Strings.isNullOrEmpty(s)) {
      return Optional.empty();
    }

    var slashIndex = s.lastIndexOf('/');
    if (slashIndex == -1)
    {
      //
      // Unqualified region.
      //
      return Optional.of(new RegionId(s));
    }
    else if (slashIndex < s.length() - 2) {
      //
      // Qualified region.
      //
      return Optional.of(new RegionId(s.substring(slashIndex + 1)));
    }
    else {
      return Optional.empty();
    }
  }

  public boolean isGlobal() {
    return "global".equals(this.id);
  }

  @Override
  public String toString() {
    return this.id;
  }
}
