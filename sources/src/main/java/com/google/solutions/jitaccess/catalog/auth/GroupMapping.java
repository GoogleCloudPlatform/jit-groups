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

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Defines the mapping between JIT groups and Google group email addresses.
 */
public class GroupMapping {
  private static final String PREFIX = "jit";
  static final String NAME_PATTERN = "[a-zA-Z0-9\\-]+";
  private final @NotNull Pattern pattern;
  private final @NotNull String domain;

  public GroupMapping(@NotNull String domain) {
    this.domain = domain;
    this.pattern = Pattern.compile(
      String.format(
        "^%s\\.(%s)\\.(%s)\\.(%s)@%s$",
        PREFIX,
        NAME_PATTERN,
        NAME_PATTERN,
        NAME_PATTERN,
        domain));
  }

  /**
   * Check if a group email corresponds to a JIT Group.
   */
  public boolean isJitGroup(GroupId group) {
    return this.pattern.matcher(group.email).matches();
  }

  /**
   * Determine JIT Group corresponding to a group email.
   */
  public @NotNull JitGroupId jitGroupFromGroup(GroupId group) {
    var matcher = this.pattern.matcher(group.email);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
        String.format("'%s' is not a JIT group", group.email));
    }

    return new JitGroupId(
      matcher.group(1),
      matcher.group(2),
      matcher.group(3));
  }

  /**
   * Determine group email corresponding to a JIT Group.
   */
  public @NotNull GroupId groupFromJitGroup(JitGroupId group) {
    var handle = String.join(".", new String[] {
      PREFIX,
      group.environment(),
      group.system(),
      group.name()
    });

    return new GroupId(String.format("%s@%s", handle, this.domain));
  }

  /**
   * Get prefix applied to all groups in an environment.
   */
  public @NotNull String groupPrefix(@NotNull String environmentName) {
    return String.format("%s.%s.", PREFIX, environmentName);
  }
}
