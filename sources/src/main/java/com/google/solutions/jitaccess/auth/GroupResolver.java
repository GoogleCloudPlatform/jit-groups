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

package com.google.solutions.jitaccess.auth;

import com.google.api.services.cloudidentity.v1.model.Membership;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.common.CompletableFutures;
import com.google.solutions.jitaccess.common.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Expands group memberships using the Cloud Identity Groups API.
 *
 * <p><b>Visibility &amp; trust boundary (wavemm fork P1-7).</b> The
 * output of {@link #expand} is a flat set of {@link EndUserId}/{@link
 * GroupId} principals derived from policy-ACL-supplied group ids. It
 * is NOT itself an authorisation decision — it answers the question
 * "who are the (one-level-resolved) members of these groups?", nothing
 * more. Callers that use it to populate audit fields, message
 * recipients, or picker candidate lists are fine; callers that use it
 * to decide whether a specific principal can perform a privileged
 * action MUST cross-check against the policy ACL, because:
 *
 * <ul>
 *   <li>resolution is one level deep (nested groups are not flattened
 *       — see method comment), so the absence of a user from
 *       {@code expand(...)} does NOT prove they aren't an effective
 *       member;</li>
 *   <li>an empty/failed Cloud Identity response is silently equivalent
 *       to "no members" inside this method (the per-group lambda in
 *       {@link com.google.solutions.jitaccess.web.proposal.ReviewerCandidates}
 *       turns errors into empty lists for picker robustness; do not
 *       copy that pattern when correctness matters);</li>
 *   <li>service-account members of approver groups are silently
 *       dropped (see {@link #principalFromMembership}), so any
 *       authorisation decision derived from {@code expand} must also
 *       account for non-EndUser principals via the policy ACL
 *       directly.</li>
 * </ul>
 *
 * <p><b>Audit of in-tree callers</b> (re-verify when adding a new
 * one):
 *
 * <ul>
 *   <li>{@link com.google.solutions.jitaccess.web.rest.GroupsResource#post}
 *       — selectedReviewers subset check: combines {@code expand} with
 *       a separate ACL lookup, NOT a sole authorisation source. ✓
 *   <li>{@link com.google.solutions.jitaccess.web.rest.GroupsResource#getReviewers}
 *       — picker candidate listing: best-effort, output is for UX
 *       only. ✓
 *   <li>{@link com.google.solutions.jitaccess.web.proposal.ReviewerCandidates#compute}
 *       — picker UI: same as above. ✓
 *   <li>{@link com.google.solutions.jitaccess.web.proposal.SlackProposalHandler#fingerprint}
 *       — registry key + DM dispatch: NOT an authorisation source;
 *       the JWT verified by {@code JitGroupContext.approve} is. ✓
 * </ul>
 *
 * <p>Any new caller that uses {@code expand} for an authorisation
 * decision must add itself here AND ensure a separate ACL check is
 * still performed. The catalog-side defense in {@link
 * com.google.solutions.jitaccess.catalog.JitGroupContext.JoinOperation#propose(java.time.Instant,
 * java.util.Set)} is the trust-boundary backstop that catches a forgotten check.
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
    if (EndUserId.TYPE.equalsIgnoreCase(membership.getType())) {
      return Optional.of(new EndUserId(membership.getPreferredMemberKey().getId()));
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
   * <p>
   * Group resolution is done non-recursively, so the resulting
   * set of principals might again contain a set of groups.
   * To fully resolve all groups, call this method until the
   * set contains no more groups.
   * <p>
   * While Cloud Identity API does support looking
   * up nested group memberships, the functionality is only
   * available in premium SKUs, and we therefore don't use
   * it here.
   */
  public @NotNull Set<PrincipalId> expand(
    @NotNull Set<PrincipalId> principals
  ) throws AccessException {
    var groups = principals
      .stream()
      .filter(p -> p instanceof GroupId)
      .map(p -> (GroupId)p)
      .toList();

    //
    // Create a new set in which the groups are replaced
    // by its members.
    //
    var nonGroups = principals
      .stream()
      .filter(p -> !(p instanceof GroupId))
      .toList();

    var future = CompletableFutures.mapAsync(
      groups,
      group -> this.groupsClient
        .listMemberships(group)
        .stream()
        .map(m -> principalFromMembership(m))
        .flatMap(Optional::stream)
        .toList(),
      this.executor);

    try {
      var expandedPrincipals = new HashSet<>(nonGroups);

      future.get()
        .forEach(expandedPrincipals::addAll);

      assert groups
        .stream()
        .noneMatch(g -> expandedPrincipals.contains(g));

      return expandedPrincipals;
    }
    catch (InterruptedException | ExecutionException e) {
      if (Exceptions.unwrap(e) instanceof AccessException accessException) {
        throw (AccessException) accessException.fillInStackTrace();
      }
      else {
        throw new UncheckedExecutionException(e);
      }
    }
  }
}
