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

package com.google.solutions.jitaccess.catalog.policy;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumSet;

/**
 * Access permissions that can be granted on a policy.
 *
 * Permissions are bit fields and are intended to be used
 * in an AccessControlList.
 */
public enum PolicyPermission {
  /**
   * View a group. This right is included in all other rights.
   */
  VIEW(1),

  /**
   * Join a group.
   */
  JOIN(VIEW.value + 2),

  /**
   * Approve someone's request to join a group.
   */
  APPROVE_OTHERS(VIEW.value + 4),

  /**
   * Self-approve.
   */
  APPROVE_SELF(VIEW.value + 8),

  /**
   * Export raw policy.
   */
  EXPORT(VIEW.value + 16),

  /**
   * Reconcile groups and IAM bindings with the policy.
   */
  RECONCILE(VIEW.value + 32);

  private final int value;

  PolicyPermission(int value) {
    this.value = value;
  }

  /**
   * Get the bit mask.
   */
  public int toMask() {
    return this.value;
  }

  /**
   * Convert EnumSet to the equivalent bit mask representation.
   */
  public static int toMask(@NotNull EnumSet<PolicyPermission> actions) {
    int mask = 0;
    for (var action : actions) {
      mask |= action.value;
    }
    return mask;
  }

  /**
   * Convert a bit mask to an EnumSet.
   */
  public static EnumSet<PolicyPermission> fromMask(int mask) {
    return EnumSet.copyOf(
      Arrays.stream(PolicyPermission.values())
        .filter(p -> (p.toMask() & mask) == p.toMask())
        .toList());
  }

  /**
   * Parse a list of permissions such as "VIEW, JOIN".
   */
  public static @NotNull EnumSet<PolicyPermission> parse(@NotNull String list) {
    return EnumSet.copyOf(
      Arrays.stream(list.split(","))
        .map(String::trim)
        .map(String::toUpperCase)
        .filter(s -> !s.isBlank())
        .map(s -> {
          try {
            return PolicyPermission.valueOf(s);
          }
          catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(s);
          }
        })
        .toList());
  }

  /**
   * Returns comma-separated list of elements without enclosing brackets.
   */
  public static @NotNull String toString(@NotNull EnumSet<PolicyPermission> set) {
    var iterator = set.iterator();
    var buffer = new StringBuilder();

    while (true) {
      var e = iterator.next();
      buffer.append(e);
      if (iterator.hasNext()) {
        buffer.append(',').append(' ');
      }
      else {
        return buffer.toString();
      }
    }
  }
}