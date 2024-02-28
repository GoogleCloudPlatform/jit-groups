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

package com.google.solutions.jitaccess.core.catalog.jitrole;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.clients.CloudIdentityGroupsClient;
import org.checkerframework.checker.units.qual.N;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

/**
 * Activates JIT roles by modifying memberships to the Google group that backs
 * the JIT role.
 */
public class JitRoleActivator extends EntitlementActivator<JitRole, OrganizationId, UserContext> {
  private final @NotNull CloudIdentityGroupsClient groupsClient;
  private final @NotNull JitRoleGroupMapping mapping;
  private final @NotNull InstantSource clock;

  public JitRoleActivator(
    @NotNull Catalog<JitRole, OrganizationId, UserContext> catalog,
    @NotNull CloudIdentityGroupsClient groupsClient,
    @NotNull JitRoleGroupMapping mapping,
    @NotNull JustificationPolicy policy,
    @NotNull InstantSource clock
  ) {
    super(catalog, policy);

    Preconditions.checkNotNull(groupsClient, "groupsClient");

    this.groupsClient = groupsClient;
    this.mapping = mapping;
    this.clock = clock;
  }

  private Activation provisionGroupMembership(
    @NotNull JitRole role,
    @NotNull UserId user,
    @NotNull Instant startTime,
    @NotNull Duration duration
  ) throws AccessException, IOException {

    var group = this.mapping.groupFromJitRole(role);

    //
    // Add time-bound group membership. If the user is a member
    // already, update the expiry of their membership.
    //
    // NB. Unlike with IAM conditions, we can't specify a start time
    // for a temporary group memberships.
    //
    Preconditions.checkArgument(
      !startTime.isAfter(this.clock.instant()),
      "Start time must not be in the future");

    this.groupsClient.addMembership(group, user, startTime.plus(duration));

    return new Activation(startTime, duration);
  }

  //---------------------------------------------------------------------------
  // Overrides.
  //---------------------------------------------------------------------------

  @Override
  public int maximumNumberOfEntitlementsPerJitRequest() {
    return 1;
  }

  @Override
  protected Activation provisionAccess(
    JitActivationRequest<JitRole> request
  ) throws AccessException, AlreadyExistsException, IOException {
    Preconditions.checkNotNull(request, "request");
    Preconditions.checkArgument(
      request.entitlements().size() == 1,
      "Only one JIT role can be activated at a time");

    return provisionGroupMembership(
      request.entitlements().stream().findFirst().get(),
      request.requestingUser(),
      request.startTime(),
      request.duration());
  }

  @Override
  protected Activation provisionAccess(
    UserId approvingUser,
    MpaActivationRequest<JitRole> request
  ) throws AccessException, AlreadyExistsException, IOException {
    Preconditions.checkNotNull(request, "request");
    Preconditions.checkArgument(
      request.entitlements().size() == 1,
      "Only one JIT role can be activated at a time");

    //
    // NB. The start/end time for the membership is derived from the approval token. If multiple
    // reviewers try to approve the same token, the resulting condition (and binding) will
    // be the same.
    //

    return provisionGroupMembership(
      request.entitlements().stream().findFirst().get(),
      request.requestingUser(),
      request.startTime(),
      request.duration());
  }

  @Override
  public @NotNull JsonWebTokenConverter<MpaActivationRequest<JitRole>> createTokenConverter() {
    //TODO: add membership!
    throw new RuntimeException("NIY!");
  }
}
