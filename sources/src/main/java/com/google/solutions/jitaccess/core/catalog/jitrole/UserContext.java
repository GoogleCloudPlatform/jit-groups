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

import com.google.api.services.cloudidentity.v1.model.Membership;
import com.google.solutions.jitaccess.cel.TimeSpan;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.GroupId;
import com.google.solutions.jitaccess.core.auth.PrincipalId;
import com.google.solutions.jitaccess.core.auth.Subject;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.CatalogUserContext;
import com.google.solutions.jitaccess.core.clients.CloudIdentityGroupsClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Captures authorization information about the user interacting
 * with the catalog.
 */
public class UserContext implements Subject, CatalogUserContext {
  private final @NotNull UserId id;
  private final @NotNull Set<GroupId> groups;
  private final @NotNull Set<PrincipalId> allPrincipals;
  private final @NotNull Set<ActiveRole> activeRoles;

  UserContext(
    @NotNull UserId id,
    @NotNull Set<GroupId> groups,
    Set<ActiveRole> activeRoles
  ) {
    this.id = id;
    this.groups = groups;
    this.activeRoles = activeRoles;

    //
    // For authorization purposes, we have to consider the user
    // and all its groups.
    //
    this.allPrincipals = Stream
      .concat(
        groups.stream().map(g -> (PrincipalId)g),
        Stream.of(id))
      .collect(Collectors.toSet());
  }

  /**
   * @return the user ID/primary email.
   */
  @NotNull public UserId user() {
    return id;
  }

  /**
   * @return active roles.
   */
  @NotNull Set<ActiveRole> activeRoles() {
    return this.activeRoles;
  }

  /**
   * @return full set of principals, including all their groups
   * (groups that map to entitlements as well as other groups).
   */
  public @NotNull Set<PrincipalId> principals() {
    return this.allPrincipals;
  }

  //---------------------------------------------------------------------------
  // Inner classes.
  //---------------------------------------------------------------------------

  /**
   * Represents an active JIT role.
   */
  record ActiveRole(
     @NotNull JitRole role,
     @Nullable TimeSpan validity
  ) {
  }

  public static class Resolver {
    private final @NotNull CloudIdentityGroupsClient groupsClient;
    private final @NotNull JitRoleGroupMapping mapper;
    private final @NotNull Executor executor;

    public Resolver(
      @NotNull CloudIdentityGroupsClient groupsClient,
      @NotNull JitRoleGroupMapping mapper,
      @NotNull Executor executor
    ) {
      this.groupsClient = groupsClient;
      this.mapper = mapper;
      this.executor = executor;
    }

    public UserContext lookup(
      @NotNull UserId user
    ) throws AccessException, IOException {
      //
      // Find the user's direct group memberships. This includes all
      // groups, JIT role groups and others.
      //
      var allMemberships = this.groupsClient
        .listMembershipsByUser(user)
        .stream()
        .toList();

      //
      // Separate memberships into two buckets:
      // - JIT role groups
      // - other groups
      //
      var jitRoleGroupMemberships = allMemberships
        .stream()
        .filter(m -> this.mapper.isJitRole(new GroupId(m.getGroup())))
        .toList();

      var otherGroups = allMemberships
        .stream()
        .filter(m -> !this.mapper.isJitRole(new GroupId(m.getGroup())))
        .map(m -> new GroupId(m.getGroup()))
        .collect(Collectors.toSet());

      //
      // All JIT roles should be filtered out so that they're not used for authorization.
      //
      assert otherGroups.stream().noneMatch(g -> this.mapper.isJitRole(g));
      assert jitRoleGroupMemberships.size() + otherGroups.size() == allMemberships.size();

      //
      // For JIT role groups, we need to know the expiry. The API doesn't
      // return that, so we have to perform extra lookups.
      //
      assert jitRoleGroupMemberships
        .stream()
        .filter(m -> m.getRoles() != null)
        .flatMap(m -> m.getRoles().stream())
        .allMatch(r -> r.getExpiryDetail() == null);

      List<CompletableFuture<Membership>> membershipFutures = jitRoleGroupMemberships
        .stream()
        .map(r -> new CloudIdentityGroupsClient.MembershipId(r.getMembership()))
        .map(membershipId -> ThrowingCompletableFuture.submit(
          () -> this.groupsClient.getMembership(membershipId),
          this.executor))
        .toList();

      var activeJitRoles = new HashSet<ActiveRole>();
      for (var future : membershipFutures) {
        try {
          var membership = ThrowingCompletableFuture.awaitAndRethrow(future);
          var groupEmail = new GroupId(membership.getPreferredMemberKey().getId());

          //
          // NB. Temporary group memberships don't have a start date, but they
          // must have an expiry date.
          //
          var expiryDate = membership.getRoles()
            .stream()
            .filter(r -> r.getExpiryDetail() != null && r.getExpiryDetail().getExpireTime() != null)
            .map(d -> Instant.parse(d.getExpiryDetail().getExpireTime()))
            .min(Instant::compareTo)
            .orElse(null);

          if (expiryDate == null) {
            //
            // This is not a proper JIT role group. Somebody might have created a group
            // that just happens to fit the naming convention.
            //
            otherGroups.add(groupEmail);
          }
          else {
            activeJitRoles.add(new ActiveRole(
              this.mapper.jitRoleFromGroup(groupEmail),
              new TimeSpan(Instant.EPOCH, expiryDate)));
          }
        }
        catch (ResourceNotFoundException ignored) {
          //
          // Membership expired in the meantime.
          //
        }
      }

      assert activeJitRoles.size() <= jitRoleGroupMemberships.size();

      return new UserContext(
        user,
        otherGroups,
        activeJitRoles);
    }
  }
}
