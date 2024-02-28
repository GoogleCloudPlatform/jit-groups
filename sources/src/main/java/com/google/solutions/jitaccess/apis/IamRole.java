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

package com.google.solutions.jitaccess.apis;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record IamRole(
  @NotNull String name
) {
  private static final String PREDEFINED_ROLE_PREFIX = "roles/";
  private static final String CUSTOM_ORG_ROLE_PREFIX = "organizations/";
  private static final String CUSTOM_PROJECT_ROLE_PREFIX = "projects/";

  public IamRole {
    Preconditions.checkArgument(
      name.startsWith(PREDEFINED_ROLE_PREFIX) ||
        name.startsWith(CUSTOM_ORG_ROLE_PREFIX) ||
        name.startsWith(CUSTOM_PROJECT_ROLE_PREFIX),
      "The IAM role uses an invalid prefix");
  }

  /**
   * Parse a role in the format roles/name.
   *
   * @return empty if the input string is malformed.
   */
  public static @NotNull Optional<IamRole> parse(@Nullable String s) {
    if (s == null) {
      return Optional.empty();
    }

    s = s.trim();

    if (s.startsWith(PREDEFINED_ROLE_PREFIX) && s.length() > PREDEFINED_ROLE_PREFIX.length()) {
      return Optional.of(new IamRole(s));
    }
    else if (s.startsWith(CUSTOM_ORG_ROLE_PREFIX) && s.length() > CUSTOM_ORG_ROLE_PREFIX.length()) {
      return Optional.of(new IamRole(s));
    }
    else if (s.startsWith(CUSTOM_PROJECT_ROLE_PREFIX) && s.length() > CUSTOM_PROJECT_ROLE_PREFIX.length()) {
      return Optional.of(new IamRole(s));
    }
    else {
      return Optional.empty();
    }
  }

  @Override
  public String toString() {
    return this.name;
  }
}
