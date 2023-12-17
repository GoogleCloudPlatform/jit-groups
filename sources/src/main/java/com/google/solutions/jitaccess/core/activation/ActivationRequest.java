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

package com.google.solutions.jitaccess.core.activation;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.entitlements.EntitlementId;

import java.time.Instant;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Request to activate one or more entitlements.
 */
public abstract class ActivationRequest {
  private final ActivationId id;
  private final Instant startTime;
  private final Instant endTime;
  private final UserId requestingUser;
  private final Collection<EntitlementId> entitlements;
  private final String justification;

  protected ActivationRequest(
    ActivationId id,
    UserId requestingUser,
    Collection<EntitlementId> entitlements,
    String justification,
    Instant startTime,
    Instant endTime
    ) {

    Preconditions.checkNotNull(id, "id");
    Preconditions.checkNotNull(requestingUser, "user");
    Preconditions.checkNotNull(entitlements, "entitlements");
    Preconditions.checkNotNull(justification, "justification");
    Preconditions.checkNotNull(startTime);
    Preconditions.checkNotNull(endTime);

    Preconditions.checkArgument(!entitlements.isEmpty());

    this.id = id;
    this.startTime = startTime;
    this.endTime = endTime;
    this.requestingUser = requestingUser;
    this.entitlements = entitlements;
    this.justification = justification;
  }

  public void apply(
    JustificationPolicy policy
  ) throws AccessException
  {
    Preconditions.checkNotNull(policy, "policy");

    //
    // Check that the justification is ok.
    //
    policy.checkJustification(this.requestingUser, this.justification);

    applyCore();
  }

  protected abstract void applyCore() throws AccessException;

  @Override
  public String toString() {
    return String.format(
      "[%s] entitlements=%s, duration=%s-%s, justification=%s",
      this.id,
      this.entitlements.stream().map(e -> e.toString()).collect(Collectors.joining(",")),
      this.startTime,
      this.endTime,
      this.justification);
  }
}
