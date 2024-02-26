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

package com.google.solutions.jitaccess.core.catalog.group;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.GroupEmail;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.clients.CloudIdentityGroupsClient;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Activator for group memberships.
 */
public class GroupMembershipActivator extends EntitlementActivator<GroupMembership, OrganizationId> {
  private final @NotNull CloudIdentityGroupsClient groupsClient;

  public GroupMembershipActivator(
    @NotNull EntitlementCatalog<GroupMembership, OrganizationId> catalog,
    @NotNull CloudIdentityGroupsClient groupsClient,
    @NotNull JustificationPolicy policy
  ) {
    super(catalog, policy);

    Preconditions.checkNotNull(groupsClient, "groupsClient");
    this.groupsClient = groupsClient;
  }

  private void provisionGroupMembership(
    @NotNull GroupEmail group,
    @NotNull UserEmail user,
    @NotNull Instant startTime,
    @NotNull Duration duration
  ) throws AccessException, IOException {
    //
    // Add time-bound group membership. If the user is a member
    // already, update the expiry of their membership.
    //
    // NB. Unlike with IAM conditions, we can't specify a start time
    // for a temporary group memberships.
    //
    Preconditions.checkArgument(
      startTime.isBefore(Instant.now()),
      "Start time must not be in the future");

    this.groupsClient.addMembership(group, user, startTime.plus(duration));
  }

  //---------------------------------------------------------------------------
  // Overrides.
  //---------------------------------------------------------------------------

  @Override
  protected void provisionAccess(
    JitActivationRequest<GroupMembership> request
  ) throws AccessException, AlreadyExistsException, IOException {
    Preconditions.checkNotNull(request, "request");
    Preconditions.checkArgument(
      request.entitlements().size() == 1,
      "Only one group membership can be activated at a time");

    provisionGroupMembership(
      request.entitlements().stream().findFirst().get().group(),
      request.requestingUser(),
      request.startTime(),
      request.duration());
  }

  @Override
  protected void provisionAccess(
    UserEmail approvingUser,
    MpaActivationRequest<GroupMembership> request
  ) throws AccessException, AlreadyExistsException, IOException {
    Preconditions.checkNotNull(request, "request");
    Preconditions.checkArgument(
      request.entitlements().size() == 1,
      "Only one group membership can be activated at a time");

    //
    // NB. The start/end time for the membership is derived from the approval token. If multiple
    // reviewers try to approve the same token, the resulting condition (and binding) will
    // be the same.
    //

    provisionGroupMembership(
      request.entitlements().stream().findFirst().get().group(),
      request.requestingUser(),
      request.startTime(),
      request.duration());
  }

  @Override
  public @NotNull JsonWebTokenConverter<MpaActivationRequest<GroupMembership>> createTokenConverter() {
    //TODO: add membership!
    throw new RuntimeException("NIY!");
  }
}
