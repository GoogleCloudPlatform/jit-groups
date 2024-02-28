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

package com.google.solutions.jitaccess.core.auth;

import com.google.api.services.cloudidentity.v1.model.Membership;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.clients.CloudIdentityGroupsClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Expands groups in access control lists.
 */
public class GroupExpander {
  private final @NotNull CloudIdentityGroupsClient groupsClient;
  private final @NotNull Executor executor;

  public GroupExpander(CloudIdentityGroupsClient groupsClient, Executor executor) {
    this.groupsClient = groupsClient;
    this.executor = executor;
  }

  private static @Nullable PrincipalIdentifier tryGetPrincipalFromMembership(
    @NotNull Membership membership
  ) {
    if (UserEmail.TYPE.equalsIgnoreCase(membership.getType())) {
      return new UserEmail(membership.getPreferredMemberKey().getId());
    }
    else if (GroupEmail.TYPE.equalsIgnoreCase(membership.getType())) {
      return new GroupEmail(membership.getPreferredMemberKey().getId());
    }
    else {
      //
      // Ignore other types (in particular, SERVICE_ACCOUNT).
      //
      return null;
    }
  }

  /**
   * Create an ACL that is like the input ACL, but has groups
   * replaced by their members. Group resolution is done non-
   * recursively. To fully resolve all groups, call this method
   * until the ACL contains no more groups.
   */
  public AccessControlList expand(
    @NotNull AccessControlList acl
  ) throws AccessException, IOException {

    var groups = acl
      .allowedPrincipals()
      .stream()
      .filter(p -> p instanceof GroupEmail)
      .map(p -> (GroupEmail)p)
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
    // Create a new ACL in which the groups are replaced
    // by its members.
    //
    var nonGroups = acl
      .allowedPrincipals()
      .stream()
      .filter(p -> !(p instanceof GroupEmail))
      .toList();

    var expandedPrincipals = new HashSet<>(nonGroups);
    for (var future : futures) {
      var members = ThrowingCompletableFuture
        .awaitAndRethrow(future)
        .stream()
        .map(m -> tryGetPrincipalFromMembership(m))
        .filter(p -> p != null)
        .toList();
      expandedPrincipals.addAll(members);
    }

    assert groups.stream().noneMatch(g -> expandedPrincipals.contains(g));

    return new AccessControlList(expandedPrincipals);
  }
}
