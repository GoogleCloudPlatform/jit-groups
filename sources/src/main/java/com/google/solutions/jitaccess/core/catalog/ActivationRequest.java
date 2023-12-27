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
import com.google.solutions.jitaccess.core.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a request for activating one or more entitlements.
 */
public abstract class ActivationRequest<TEntitlementId extends EntitlementId> {
  private final ActivationId id;
  private final Instant startTime;
  private final Duration duration;
  private final UserId requestingUser;
  private final Set<TEntitlementId> entitlements;
  private final String justification;

  protected ActivationRequest(
    ActivationId id,
    UserId requestingUser,
    Set<TEntitlementId> entitlements,
    String justification,
    Instant startTime,
    Duration duration
    ) {

    Preconditions.checkNotNull(id, "id");
    Preconditions.checkNotNull(requestingUser, "user");
    Preconditions.checkNotNull(entitlements, "entitlements");
    Preconditions.checkNotNull(justification, "justification");
    Preconditions.checkNotNull(startTime);
    Preconditions.checkNotNull(startTime);

    Preconditions.checkArgument(
      !entitlements.isEmpty(),
      "At least one entitlement must be specified");

    Preconditions.checkArgument(
      !duration.isZero() &&! duration.isNegative(),
      "The duration must be positive");

    this.id = id;
    this.startTime = startTime;
    this.duration = duration;
    this.requestingUser = requestingUser;
    this.entitlements = entitlements;
    this.justification = justification;
  }

  /**
   * @return unique ID of the request.
   */
  public ActivationId id() {
    return this.id;
  }

  /**
   * @return start time for requested access.
   */
  public Instant startTime() {
    return this.startTime;
  }

  /**
   * @return duration of requested activation.
   */
  public Duration duration() {
    return this.duration;
  }

  /**
   * @return end time for requested access.
   */
  public Instant endTime() {
    return this.startTime.plus(this.duration);
  }

  /**
   * @return user that requested access.
   */
  public UserId requestingUser() {
    return this.requestingUser;
  }

  /**
   * @return one or more entitlements.
   */
  public Collection<TEntitlementId> entitlements() {
    return this.entitlements;
  }

  /**
   * @return user-provided justification for the request.
   */
  public String justification() {
    return this.justification;
  }

  public abstract ActivationType type();

  @Override
  public String toString() {
    return String.format(
      "[%s] entitlements=%s, startTime=%s, duration=%s, justification=%s",
      this.id,
      this.entitlements.stream().map(e -> e.toString()).collect(Collectors.joining(",")),
      this.startTime,
      this.duration,
      this.justification);
  }
}
