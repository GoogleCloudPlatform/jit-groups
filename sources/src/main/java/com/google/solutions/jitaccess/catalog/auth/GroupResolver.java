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

import com.google.api.services.cloudidentity.v1.model.Membership;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.catalog.ThrowingCompletableFuture;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Expands group memberships using the Cloud Identity
 * Groups API.
 */
public class GroupResolver {
  private final @NotNull CloudIdentityGroupsClient groupsClient;
  private final @NotNull Executor executor;

  public GroupResolver(
    @NotNull CloudIdentityGroupsClient groupsClient,
    @NotNull Executor executor
  ) {
    this.groupsClient = groupsClient;
    this.executor = executor;
  }

  private static @NotNull Optional<PrincipalId> principalFromMembership(
    @NotNull Membership membership
  ) {
    if (UserId.TYPE.equalsIgnoreCase(membership.getType())) {
      return Optional.of(new UserId(membership.getPreferredMemberKey().getId()));
    }
    else if (GroupId.TYPE.equalsIgnoreCase(membership.getType())) {
      return Optional.of(new GroupId(membership.getPreferredMemberKey().getId()));
    }
    else {
      //
      // Ignore other types (in particular, SERVICE_ACCOUNT).
      //
      return Optional.empty();
    }
  }

  /**
   * Expand all groups in a list of principals.
   *
   * Group resolution is done non-recursively, so the resulting
   * set of principals might again contain a set of groups.
   * To fully resolve all groups, call this method until the
   * set contains no more groups.
   *
   * While Cloud Identity API does support looking
   * up nested group memberships, the functionality is only
   * available in premium SKUs, and we therefore don't use
   * it here.
   */
  @NotNull Set<PrincipalId> expand(
    @NotNull Set<PrincipalId> principals
  ) throws AccessException, IOException {
    var groups = principals
      .stream()
      .filter(p -> p instanceof GroupId)
      .map(p -> (GroupId)p)
      .toList();

    //
    // Expand groups a single level deep.
    //
    List<CompletableFuture<List<Membership>>> futures = groups
      .stream()
      .map(group -> ThrowingCompletableFuture.submit(
        () -> this.groupsClient.listMemberships(group),
        this.executor))
      .toList();

    //
    // Create a new set in which the groups are replaced
    // by its members.
    //
    var nonGroups = principals
      .stream()
      .filter(p -> !(p instanceof GroupId))
      .toList();

    var expandedPrincipals = new HashSet<>(nonGroups);
    for (var future : futures) {
      var members = ThrowingCompletableFuture
        .awaitAndRethrow(future)
        .stream()
        .map(m -> principalFromMembership(m))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
      expandedPrincipals.addAll(members);
    }

    assert groups.stream().noneMatch(g -> expandedPrincipals.contains(g));

    return expandedPrincipals;
  }
}
