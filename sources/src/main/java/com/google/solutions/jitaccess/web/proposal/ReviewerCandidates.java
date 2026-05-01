//
// Copyright 2026 Wave Mobile Money / wavemm fork
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//

package com.google.solutions.jitaccess.web.proposal;

import com.google.api.services.cloudidentity.v1.model.Membership;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.auth.EndUserId;
import com.google.solutions.jitaccess.auth.GroupId;
import com.google.solutions.jitaccess.auth.GroupResolver;
import com.google.solutions.jitaccess.auth.IamPrincipalId;
import com.google.solutions.jitaccess.auth.PrincipalId;
import com.google.solutions.jitaccess.common.CompletableFutures;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Computes the candidate-reviewer list shown in the picker UI when a
 * requester is about to submit an MPA elevation.
 *
 * <p>Two outputs per call:
 * <ul>
 *   <li>The full list of qualified peer reviewers (after group
 *       expansion, excluding the requester).
 *   <li>A {@code suggested} flag per candidate indicating whether they
 *       are likely to be on the requester's team — defined as sharing
 *       at least one small {@code *@roles.wave.com} group with the
 *       requester (regardless of whether that group is in the policy
 *       ACL).
 * </ul>
 *
 * <p>"Small enough" is what makes the heuristic tractable: a broad
 * group like {@code engineering@} (hundreds of members) is a poor
 * signal of "team", so we skip groups exceeding {@link
 * #SUGGESTION_GROUP_MAX_SIZE}. Team groups (typically &lt;15 members
 * per Wave's org chart) survive.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>{@link CloudIdentityGroupsClient#listMembershipsByUser} on the
 *       requester to find every group they belong to (not just
 *       approver groups).
 *   <li>Fetch each group's members in parallel via {@link
 *       CompletableFutures#mapAsync} so 10 small groups cost ~one
 *       round-trip wallclock instead of ten. Groups whose membership
 *       exceeds the size cap are skipped after the fetch.
 *   <li>Tally how many requester-groups each candidate co-inhabits;
 *       higher overlap → stronger team signal. Top {@link
 *       #SUGGESTED_BADGE_TOP_N} get the badge.
 * </ol>
 *
 * <p>The candidate list itself is computed independently by expanding
 * the policy-ACL principals — same code path as the SlackProposalHandler
 * uses for DM delivery, ensuring consistency between picker and
 * dispatch.
 */
public class ReviewerCandidates {
  /**
   * A group with more than this many direct members is treated as
   * "broad" (e.g. {@code engineering@}) and not used as a team-mate
   * signal. Cap is intentionally generous — the typical Wave team
   * is &lt;15, but cross-team initiatives can reach the low tens.
   */
  static final int SUGGESTION_GROUP_MAX_SIZE = 30;

  /**
   * Hard cap on the number of candidates the suggested badge is
   * rendered for in the UI. The caller (the GET /reviewers handler)
   * still returns the full candidate list; the badge is what
   * highlights the top-N teammates.
   */
  static final int SUGGESTED_BADGE_TOP_N = 3;

  private final @NotNull GroupResolver groupResolver;
  private final @NotNull CloudIdentityGroupsClient groupsClient;
  private final @NotNull Executor executor;

  public ReviewerCandidates(
    @NotNull GroupResolver groupResolver,
    @NotNull CloudIdentityGroupsClient groupsClient,
    @NotNull Executor executor
  ) {
    this.groupResolver = groupResolver;
    this.groupsClient = groupsClient;
    this.executor = executor;
  }

  /**
   * Compute the candidate list for a given (requester, qualified-peers)
   * tuple.
   *
   * @param requester the user making the elevation request
   * @param qualifiedPeers the union of principals (EndUserId + GroupId)
   *                       holding APPROVE_OTHERS in the policy ACL
   * @return suggested-first, alphabetically-then list of candidates
   */
  public @NotNull List<Candidate> compute(
    @NotNull EndUserId requester,
    @NotNull Set<IamPrincipalId> qualifiedPeers
  ) throws AccessException, IOException {
    //
    // Step 1: expand any GroupId in the qualified peers to individual
    // EndUserIds so the picker shows actual people, not groups.
    //
    Set<PrincipalId> expandedAcl = this.groupResolver.expand(
      new HashSet<>(qualifiedPeers));

    Set<EndUserId> individualCandidates = expandedAcl.stream()
      .filter(EndUserId.class::isInstance)
      .map(p -> (EndUserId) p)
      .filter(u -> !u.equals(requester))  // never list the requester
      .collect(Collectors.toSet());

    if (individualCandidates.isEmpty()) {
      return List.of();
    }

    //
    // Step 2: rank candidates by team-mate likelihood.
    //
    Map<EndUserId, Integer> teamScore = computeTeamScores(
      requester, individualCandidates);

    //
    // Order candidates: those with a non-zero team score come first
    // (highest score wins ties), then everyone else alphabetically.
    // The badge applies only to the top N from the score-ranked
    // subset to avoid surfacing dozens of teammates if the requester
    // is in many small overlapping groups.
    //
    var ranked = individualCandidates.stream()
      .sorted(Comparator
        .comparingInt((EndUserId u) ->
          -teamScore.getOrDefault(u, 0))     // higher score first
        .thenComparing(u -> u.email))
      .toList();

    Set<EndUserId> badgeSet = ranked.stream()
      .filter(u -> teamScore.getOrDefault(u, 0) > 0)
      .limit(SUGGESTED_BADGE_TOP_N)
      .collect(Collectors.toSet());

    return ranked.stream()
      .map(u -> new Candidate(
        u.email,
        u.email,  // displayName: same as email until we wire Directory.users.get
        badgeSet.contains(u)))
      .toList();
  }

  /**
   * Fetch each requester-group's members in parallel and tally how
   * many groups each candidate co-inhabits. Skips groups exceeding
   * the size cap so broad memberships like {@code engineering@} don't
   * dilute the signal.
   *
   * <p>Calls {@link CloudIdentityGroupsClient#listMemberships} directly
   * (one parallel batch via {@link CompletableFutures#mapAsync})
   * instead of routing through {@link GroupResolver#expand} per-group,
   * which would serialise 10 round-trips at 200 ms each into a
   * 2-second blocking call on the picker page.
   */
  private @NotNull Map<EndUserId, Integer> computeTeamScores(
    @NotNull EndUserId requester,
    @NotNull Set<EndUserId> candidates
  ) throws AccessException, IOException {
    var requesterGroups = this.groupsClient
      .listMembershipsByUser(requester)
      .stream()
      .map(rel -> rel.getGroupKey())
      .filter(key -> key != null && key.getId() != null)
      .map(key -> new GroupId(key.getId()))
      .toList();

    if (requesterGroups.isEmpty()) {
      return Map.of();
    }

    //
    // Parallel fan-out: kick off every listMemberships call at once
    // and join the lot. Per-group failures are turned into empty
    // results inside the lambda so one bad group (deleted, permission
    // hiccup) doesn't poison the entire suggestion computation.
    //
    var future = CompletableFutures.mapAsync(
      requesterGroups,
      group -> {
        try {
          return new GroupMembers(group, this.groupsClient.listMemberships(group));
        }
        catch (Exception e) {
          return new GroupMembers(group, List.of());
        }
      },
      this.executor);

    Collection<GroupMembers> all;
    try {
      all = future.get();
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Map.of();
    }
    catch (ExecutionException e) {
      // mapAsync wraps individual failures, but our lambda already
      // catches them. An ExecutionException here means something
      // truly unexpected; degrade rather than blow up the picker.
      return Map.of();
    }

    var scores = new HashMap<EndUserId, Integer>();
    for (var gm : all) {
      // Skip broad groups — engineering@ has hundreds of members and
      // sharing it with the requester says nothing about being on
      // their team.
      if (gm.members().size() > SUGGESTION_GROUP_MAX_SIZE) {
        continue;
      }

      gm.members().stream()
        .map(ReviewerCandidates::endUserOrNull)
        .filter(u -> u != null)
        .filter(candidates::contains)
        .forEach(c -> scores.merge(c, 1, Integer::sum));
    }
    return scores;
  }

  /**
   * Convert a {@link Membership} to an {@link EndUserId}, or null if
   * the membership isn't a user (groups, service accounts, malformed
   * entries). Mirrors the conversion in {@code
   * GroupResolver.principalFromMembership} but inlined to avoid going
   * through the resolver's higher-level {@code expand} API for a
   * call we deliberately want to parallelise ourselves.
   */
  private static EndUserId endUserOrNull(@NotNull Membership m) {
    if (!EndUserId.TYPE.equalsIgnoreCase(m.getType())) {
      return null;
    }
    var key = m.getPreferredMemberKey();
    if (key == null || key.getId() == null) {
      return null;
    }
    return new EndUserId(key.getId());
  }

  /** Pair of (group, fetched members) used by the parallel fan-out. */
  private record GroupMembers(@NotNull GroupId group, @NotNull List<Membership> members) {}

  /**
   * One picker candidate — what the frontend renders as a checkbox.
   *
   * @param email primary email (also the JIT principal value)
   * @param displayName presentation name; equal to email until we wire
   *                    Directory API for actual display names
   * @param suggested true if this candidate is in the top
   *                  {@link #SUGGESTED_BADGE_TOP_N} ranked teammates
   *                  (rendered with the "team" badge in the UI)
   */
  public record Candidate(
    @NotNull String email,
    @NotNull String displayName,
    boolean suggested
  ) {}
}
