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

package com.google.solutions.jitaccess.core.catalog;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.cel.TimeSpan;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Comparator;

/**
 * Represents a requester privilege. A requester privilege is dormant unless the
 * user
 * activates it, and it automatically becomes inactive again after a certain
 * period of time has elapsed.
 */
public record RequesterPrivilege<TPrivilegeID extends PrivilegeId>(
    TPrivilegeID id,
    String name,
    ActivationType activationType,
    Status status,
    TimeSpan validity) implements Comparable<RequesterPrivilege<TPrivilegeID>> {

  public RequesterPrivilege {
    Preconditions.checkNotNull(id, "id");
    Preconditions.checkNotNull(name, "name");

    Preconditions.checkArgument(
        validity == null || (status == Status.ACTIVE || status == Status.EXPIRED));
    Preconditions.checkArgument(
        status != Status.ACTIVE || validity.end().isAfter(Instant.now()));
    Preconditions.checkArgument(
        status != Status.EXPIRED || validity.end().isBefore(Instant.now()));
  }

  public RequesterPrivilege(
      TPrivilegeID id,
      String name,
      ActivationType activationType,
      Status status) {
    this(id, name, activationType, status, null);
  }

  @Override
  public String toString() {
    return this.name;
  }

  @Override
  public int compareTo(@NotNull RequesterPrivilege<TPrivilegeID> o) {
    return Comparator
        .comparing((RequesterPrivilege<TPrivilegeID> e) -> e.status)
        .thenComparing(e -> e.activationType.name())
        .thenComparing(e -> e.id)
        .thenComparing(e -> e.validity, Comparator.nullsLast(Comparator.naturalOrder()))
        .compare(this, o);
  }

  // ---------------------------------------------------------------------------
  // Inner classes.
  // ---------------------------------------------------------------------------

  public enum Status {
    /**
     * Privilege can be activated.
     */
    INACTIVE,

    /**
     * Privilege is active.
     */
    ACTIVE,

    /**
     * Entitlement was active, but has no expired.
     */
    EXPIRED,

    /**
     * Approval pending.
     */
    ACTIVATION_PENDING
  }
}
