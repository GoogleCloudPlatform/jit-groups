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

package com.google.solutions.jitaccess.web.rest;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;
import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.auth.EndUserId;
import com.google.solutions.jitaccess.auth.GroupResolver;
import com.google.solutions.jitaccess.auth.JitGroupId;
import com.google.solutions.jitaccess.auth.Principal;
import com.google.solutions.jitaccess.auth.Subject;
import com.google.solutions.jitaccess.catalog.Catalog;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.policy.PolicyAnalysis;
import com.google.solutions.jitaccess.catalog.policy.PolicyPermission;
import com.google.solutions.jitaccess.catalog.policy.Privilege;
import com.google.solutions.jitaccess.catalog.policy.Property;
import com.google.solutions.jitaccess.common.Coalesce;
import com.google.solutions.jitaccess.web.*;
import com.google.solutions.jitaccess.web.proposal.ProposalHandler;
import com.google.solutions.jitaccess.web.proposal.ReviewerCandidates;
import com.google.solutions.jitaccess.web.proposal.TokenObfuscator;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Dependent
@Path("/api")
@RequireIapPrincipal
@LogRequest
public class GroupsResource {
  private static final AccessDeniedException NOT_FOUND = new AccessDeniedException(
    "The group does not exist or access is denied");

  @Inject
  Catalog catalog;

  @Inject
  Logger logger;

  @Inject
  OperationAuditTrail auditTrail;

  @Inject
  ProposalHandler proposalHandler;

  @Context
  UriInfo uriInfo;

  @Inject
  LinkBuilder linkBuilder;

  @Inject
  Consoles consoles;

  @Inject
  Options options;

  @Inject
  CloudIdentityGroupsClient groupsClient;

  @Inject
  Subject subject;

  @Inject
  Executor executor;

  /**
   * Get group details, including information about requirements
   * to join the group.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}/systems/{system}/groups/{name}")
  public @NotNull GroupInfo get(
    @PathParam("environment") @NotNull String environment,
    @PathParam("system") @NotNull String system,
    @PathParam("name") @NotNull String name
  ) throws Exception {
    try {
      var groupId = new JitGroupId(environment, system, name);

      return this.catalog
        .group(groupId)
        .map(grp -> GroupInfo.create(
          grp,
          JoinInfo.forJoinAnalysis(grp, this.options.slackCopyLinkEnabled())))
        .orElseThrow(() -> NOT_FOUND);
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_VIEW_GROUPS, e);
      throw (Exception)e.fillInStackTrace();
    }
  }

  // Form field names reserved for the picker UX. These are extracted
  // from the request body before the rest is forwarded to the constraint
  // inputs, so a constraint named `selectedReviewers` (improbable, but
  // we reserve the namespace) wouldn't collide.
  static final String FIELD_SELECTED_REVIEWERS = "selectedReviewers";
  static final String FIELD_NOTIFY_REVIEWERS = "notifyReviewers";

  /**
   * Attempt to join the group.
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Path("environments/{environment}/systems/{system}/groups/{name}")
  public @NotNull GroupInfo post(
    @PathParam("environment") @NotNull String environment,
    @PathParam("system") @NotNull String system,
    @PathParam("name") @NotNull String name,
    @NotNull MultivaluedMap<String, String> inputValues
  ) throws Exception {

    var groupId = new JitGroupId(environment, system, name);

    try {
      var group = this.catalog
        .group(groupId)
        .orElseThrow(() -> NOT_FOUND);

      //
      // Pop the picker UX form fields out of inputValues before the
      // remaining constraint inputs are forwarded to the join op.
      //
      var selectedReviewers = parseSelectedReviewers(
        inputValues.remove(FIELD_SELECTED_REVIEWERS));
      var notifyReviewers = parseNotifyReviewers(
        inputValues.remove(FIELD_NOTIFY_REVIEWERS));

      //
      // When the requester picked specific reviewers, validate every
      // email is in the expanded qualified-peer set BEFORE calling
      // propose. JoinOperation.propose trusts the filter is already
      // an authorised subset (it short-circuits the recipients list
      // to whatever we pass), so the security check needs to happen
      // here where we have a GroupResolver to expand groups.
      //
      if (selectedReviewers != null && !selectedReviewers.isEmpty()) {
        var qualified = group.policy().effectiveAccessControlList()
          .allowedPrincipals(PolicyPermission.APPROVE_OTHERS.toMask())
          .stream()
          .filter(p -> !p.equals(this.subject.user()))
          .filter(p -> p instanceof com.google.solutions.jitaccess.auth.IamPrincipalId)
          .map(p -> (com.google.solutions.jitaccess.auth.IamPrincipalId) p)
          .collect(Collectors.toCollection(HashSet::new));
        var resolver = new GroupResolver(this.groupsClient, this.executor);
        var allowedEmails = new ReviewerCandidates(resolver, this.groupsClient, this.executor)
          .compute(this.subject.user(), qualified)
          .stream()
          .map(c -> c.email().toLowerCase())
          .collect(Collectors.toSet());
        var rejected = selectedReviewers.stream()
          .map(EndUserId::value)
          .filter(e -> !allowedEmails.contains(e.toLowerCase()))
          .toList();
        if (!rejected.isEmpty()) {
          throw new AccessDeniedException(
            "Selected reviewers are not authorised to approve this request: "
              + String.join(", ", rejected));
        }
      }

      //
      // Attempt to join.
      //
      var joinOp = group.join();
      Inputs.copyValues(inputValues, joinOp.input());

      if (joinOp.requiresApproval()) {
        //
        // Approval required, propose to someone else.
        //
        // NB. The action URL needs to point to the frontend, not the
        //     REST API. Also, it can't use a fragment because fragments
        //     might be lost during an authentication redirect.
        //
        //     We therefore pass the API path in a query parameter,
        //     the frontend then translates this into making the right
        //     API call.
        //
        Function<String, URI> buildActionUri = token -> this.linkBuilder
          .absoluteUriBuilder(this.uriInfo)
          .path("/")
          .queryParam("f", String.format(
            "/environments/%s/proposal/%s",
            environment, TokenObfuscator.encode(token)))
          .build();

        var proposal = this.proposalHandler.propose(
          joinOp,
          buildActionUri,
          new ProposalHandler.ProposeOptions(
            selectedReviewers,
            notifyReviewers));

        // Plumb the notifyReviewers flag into the audit event so the
        // log-based BigQuery dashboard distinguishes copy-link
        // ("notifyReviewers=false") from the regular DM-everyone flow.
        // Both still fall under api.groups.join — same event id —
        // because they're the same domain action; the new label is
        // what splits them.
        this.auditTrail.joinProposed(joinOp, proposal, notifyReviewers);

        // Surface the approval URL to the requester only when copy-link
        // mode is enabled — it lets them paste it manually elsewhere.
        // When the mode is off the URL is still computed (via
        // buildActionUri) but never returned, matching the upstream
        // contract where the URL is only seen by reviewers.
        var approvalUrl = this.options.slackCopyLinkEnabled()
          ? buildActionUri.apply(proposal.value()).toString()
          : null;

        return GroupInfo.create(
          group,
          JoinInfo.forProposal(
            joinOp.input(),
            approvalUrl,
            this.options.slackCopyLinkEnabled()));
      }
      else {
        //
        // No approval required, execute the join.
        //
        var principal = joinOp.execute();
        this.auditTrail.joinExecuted(joinOp, principal);

        return GroupInfo.create(
          group,
          JoinInfo.forCompletedJoin(principal, joinOp.input()));
      }
    }
    catch (PolicyAnalysis.ConstraintFailedException e) {
      this.auditTrail.constraintFailed(groupId, e);
      throw new AccessDeniedException(e.getMessage(), e);
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_JOIN_GROUP, e);
      throw (Exception)e.fillInStackTrace();
    }
  }

  /**
   * Cap on the number of selected reviewers a single picker submission
   * can carry. Sane upper bound — ACLs that grant APPROVE_OTHERS to
   * groups larger than this number still pass through the legacy "DM
   * everyone" path because the picker can't fit them all on screen
   * anyway.
   */
  static final int SELECTED_REVIEWERS_MAX = 50;

  /**
   * Strict shape validator for picker-submitted reviewer emails.
   *
   * <p>Wavemm fork P2-9: {@link EndUserId#parse}'s canonical regex
   * {@code ^user:(.+)@(.+)$} is too permissive — it accepts
   * {@code "@@@@"} (greedy {@code .+} eats {@code @}), strings with
   * embedded whitespace, and multi-{@code @} forms. We need a tighter
   * shape check at the REST boundary because anything that gets past
   * here ends up in audit logs, Slack DM addressing, and the
   * downstream subset-of-qualified-peers check (which is set-based
   * and would silently drop the bogus value rather than alert).
   *
   * <p>Pattern rationale:
   * <ul>
   *   <li>local-part: one or more chars, none of which are {@code @}
   *       or whitespace;
   *   <li>exactly one {@code @};
   *   <li>domain: at least one label, a dot, and a TLD label, with no
   *       {@code @} or whitespace anywhere.
   * </ul>
   * That's deliberately stricter than RFC 5321 (we don't bother with
   * IP-literal hosts, quoted local parts, etc.) — Wave's IdP only
   * issues domain-shaped emails and the picker only ever submits
   * those.
   */
  private static final java.util.regex.Pattern SELECTED_REVIEWER_EMAIL_PATTERN =
    java.util.regex.Pattern.compile(
      "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  /**
   * Parse the {@code selectedReviewers} multi-value form field into a
   * {@code Set<EndUserId>}. Empty/missing → null (no filter, default
   * behaviour). Bounded at {@link #SELECTED_REVIEWERS_MAX} to keep a
   * hostile client from forcing arbitrary memory and Cloud Identity
   * lookups.
   *
   * <p>Each value is shape-validated against {@link
   * #SELECTED_REVIEWER_EMAIL_PATTERN} (wavemm fork P2-9) instead of
   * the previous "contains @" check. Anything that doesn't match —
   * empty local-part, multiple {@code @}, embedded whitespace, no TLD
   * — gets rejected with a 400 so the client can fix the form rather
   * than have it silently propagate as a malformed principal.
   */
  private static @Nullable Set<EndUserId> parseSelectedReviewers(
    @Nullable List<String> raw
  ) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    if (raw.size() > SELECTED_REVIEWERS_MAX) {
      throw new BadRequestException(
        "Too many reviewer selections (got " + raw.size()
          + ", max " + SELECTED_REVIEWERS_MAX + ")");
    }
    var parsed = new java.util.HashSet<EndUserId>();
    for (var value : raw) {
      if (value == null || value.isBlank()) {
        continue;
      }
      var trimmed = value.trim();
      if (!SELECTED_REVIEWER_EMAIL_PATTERN.matcher(trimmed).matches()) {
        throw new BadRequestException(
          "selectedReviewers contains a value that is not a valid email: '"
            + trimmed + "'");
      }
      parsed.add(new EndUserId(trimmed));
    }
    return parsed.isEmpty() ? null : parsed;
  }

  private static boolean parseNotifyReviewers(@Nullable List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return true;
    }
    // Last value wins (browsers may submit duplicates from a checkbox).
    return Boolean.parseBoolean(raw.get(raw.size() - 1));
  }

  /**
   * Per-user rate limit on {@link #getReviewers}. Each {@link
   * ReviewerCandidates#compute} call is expensive — one
   * {@code listMembershipsByUser} on the requester plus a parallel
   * {@code listMemberships} fan-out across every group they belong to.
   * Without a cap, a hostile (or buggy) frontend that polls the picker
   * could trivially burn through the JIT App Engine SA's Cloud Identity
   * quota and DoS legitimate elevation requests.
   *
   * <p>The bucket size and refill rate are chosen so a normal user
   * filling out the picker (a few searches, maybe a refresh) sees no
   * throttling, while a script polling at &gt;1 req/s gets 429s within
   * a couple of seconds. Buckets evict after 5 min idle to bound
   * memory in the face of many distinct callers.
   */
  static final double REVIEWERS_RATE_PER_SECOND = 1.0;
  private static final Cache<String, RateLimiter> REVIEWERS_RATE_LIMITERS =
    CacheBuilder.newBuilder()
      .expireAfterAccess(Duration.ofMinutes(5))
      .maximumSize(10_000)
      .build();

  // Package-private for tests; the cache lives in the same JVM, so
  // tests that exercise it must reset state through clearReviewerLimiters().
  static void clearReviewerRateLimiters() {
    REVIEWERS_RATE_LIMITERS.invalidateAll();
  }

  static @NotNull RateLimiter rateLimiterFor(@NotNull String userKey) {
    try {
      // Guava's SmoothBursty RateLimiter accumulates up to one second of
      // permits when idle — close enough to the burst pattern we want
      // (one full picker open + a few quick edits). We don't try to
      // hand-tune the burst here; the empirical floor for noisy clients
      // is set by REVIEWERS_RATE_PER_SECOND once the burst is drained.
      return REVIEWERS_RATE_LIMITERS.get(
        userKey,
        () -> RateLimiter.create(REVIEWERS_RATE_PER_SECOND));
    }
    catch (java.util.concurrent.ExecutionException e) {
      // CacheLoader throwing is impossible here (lambda doesn't throw);
      // defensive fallback uses a fresh limiter.
      return RateLimiter.create(REVIEWERS_RATE_PER_SECOND);
    }
  }

  /**
   * List candidate reviewers for the picker UX (wavemm fork). Returns
   * the qualified peers from the policy ACL (after group expansion),
   * with a {@code suggested} flag for those who share an approver group
   * with the requester — i.e. likely-teammates.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}/systems/{system}/groups/{name}/reviewers")
  public @NotNull ReviewersInfo getReviewers(
    @PathParam("environment") @NotNull String environment,
    @PathParam("system") @NotNull String system,
    @PathParam("name") @NotNull String name
  ) throws Exception {
    try {
      var groupId = new JitGroupId(environment, system, name);
      var group = this.catalog
        .group(groupId)
        .orElseThrow(() -> NOT_FOUND);

      // Compute the qualified peers the same way JoinOperation.propose
      // does — union of principals with APPROVE_OTHERS, minus the
      // requester (filtered at principal level here; ReviewerCandidates
      // also filters at email level after group expansion).
      var requester = this.subject.user();

      // Per-user rate limit. Failing to acquire surfaces as a 429 so a
      // polling client backs off rather than silently DoSing the Cloud
      // Identity quota. Authenticated principal email is the bucket
      // key — IAP guarantees this is the actual end user, not a
      // header-set value.
      if (!rateLimiterFor(requester.email).tryAcquire()) {
        this.logger.warn(
          EventIds.API_VIEW_GROUPS,
          "Reviewer-picker rate limit exceeded by %s on %s",
          requester.email, groupId);
        throw new WebApplicationException(
          "Too many requests; please retry shortly.", 429);
      }
      var qualified = group.policy().effectiveAccessControlList()
        .allowedPrincipals(PolicyPermission.APPROVE_OTHERS.toMask())
        .stream()
        .filter(p -> !p.equals(requester))
        .filter(p -> p instanceof com.google.solutions.jitaccess.auth.IamPrincipalId)
        .map(p -> (com.google.solutions.jitaccess.auth.IamPrincipalId) p)
        .collect(Collectors.toCollection(HashSet::new));

      var resolver = new GroupResolver(this.groupsClient, this.executor);
      var helper = new ReviewerCandidates(resolver, this.groupsClient, this.executor);

      List<ReviewerCandidates.Candidate> candidates;
      try {
        candidates = helper.compute(requester, qualified);
      }
      catch (com.google.solutions.jitaccess.apis.clients.AccessException
        | java.io.IOException e) {
        // Picker is best-effort: a Cloud Identity outage or a missing
        // group-membership permission must not block the elevation
        // request. Return the degraded shape and let the frontend
        // render an explicit notice.
        this.logger.warn(
          EventIds.API_VIEW_GROUPS,
          "Reviewer suggestions degraded for %s on %s: %s",
          requester.email, groupId, e);
        return new ReviewersInfo(List.of(), true);
      }

      return new ReviewersInfo(
        candidates.stream()
          .map(c -> new CandidateInfo(c.email(), c.displayName(), c.suggested()))
          .toList(),
        false);
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_VIEW_GROUPS, e);
      throw (Exception) e.fillInStackTrace();
    }
  }

  /**
   * Return a link to one of the consoles.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}/systems/{system}/groups/{name}/link/{target}")
  public @NotNull ExternalLinkInfo linkTo(
    @PathParam("environment") @NotNull String environment,
    @PathParam("system") @NotNull String system,
    @PathParam("name") @NotNull String name,
    @PathParam("target") @NotNull String target
  ) throws Exception {
    try {
      var groupId = new JitGroupId(environment, system, name);

      //
      // Lookup group, implicitly verifying VIEW access.
      //
      var group = this.catalog
        .group(groupId)
        .orElseThrow(() -> NOT_FOUND);

      //
      // Lookup the group key, this may return empty if the group
      // hasn't been created yet.
      //
      var groupKey = group
        .cloudIdentityGroupKey()
        .orElseThrow(() -> new NotFoundException("The group has not been created yet"));

      var targetUri = Optional
        .ofNullable(switch (target) {
          case "cloud-console" -> this.consoles.cloudConsole().groupDetails(groupKey);
          case "admin-console" -> this.consoles.adminConsole().groupDetails(groupKey);
          case "groups-console" -> this.consoles.groupsConsole().groupDetails(group.cloudIdentityGroupId());
          case "cloud-logging" -> this.consoles.cloudConsole().groupAuditLogs(
            groupId,
            Instant.now().minus(Duration.ofDays(7)));
          default -> null;
        })
        .orElseThrow(() -> new NotFoundException("Unknown console"));

      return new ExternalLinkInfo(
        new Link(
          "environments/%s/systems/%s/groups/%s/link/%s",
          groupId.environment(),
          groupId.system(),
          groupId.name(),
          target),
        new Link(targetUri));
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_VIEW_GROUPS, e);
      throw (Exception)e.fillInStackTrace();
    }
  }

  //---------------------------------------------------------------------------
  // Payload records.
  //---------------------------------------------------------------------------

  public record GroupInfo(
    @NotNull Link self,
    @NotNull String id,
    @NotNull String name,
    @NotNull String displayName,
    @NotNull String description,
    @NotNull String cloudIdentityGroup,
    @NotNull List<PrivilegeInfo> privileges,
    @NotNull EnvironmentsResource.EnvironmentInfo environment,
    @NotNull SystemsResource.SystemInfo system,
    @Nullable JoinInfo join
  ) implements MediaInfo {
    static GroupInfo create(
      @NotNull JitGroupContext g,
      @NotNull JoinInfo joinInfo) {
      return new GroupInfo(
        new Link(
          "environments/%s/systems/%s/groups/%s",
          g.policy().id().environment(),
          g.policy().id().system(),
          g.policy().id().name()),
        g.policy().id().toString(),
        g.policy().name(),
        g.policy().displayName(),
        g.policy().description(),
        g.cloudIdentityGroupId().email,
        g.policy()
          .privileges()
          .stream()
          .map(PrivilegeInfo::fromPrivilege)
          .toList(),
        EnvironmentsResource.EnvironmentInfo.createSummary(g.policy().system().environment()),
        SystemsResource.SystemInfo.createSummary(g.policy().system()), // Don't list nested groups.
        joinInfo);
    }
  }

  public record PrivilegeInfo(
    @NotNull String description,
    @NotNull String resourceName,
    boolean hasResourceCondition
  ) {
    static @NotNull PrivilegeInfo fromPrivilege(@NotNull Privilege p) {
      return new PrivilegeInfo(
        Coalesce.nonEmpty(p.description(), p.name()),
        p.resourceName(),
        p.hasResourceCondition());
    }
  }

  public record JoinInfo(
    @NotNull JoinStatusInfo status,
    @NotNull MembershipInfo membership,
    @NotNull List<ConstraintInfo> satisfiedConstraints,
    @NotNull List<ConstraintInfo> unsatisfiedConstraints,
    @NotNull List<InputInfo> input,
    /** JWT-bearing approval URL — non-null only when status is
     *  JOIN_PROPOSED and SLACK_COPY_LINK_ENABLED is on, so the requester
     *  can copy/share it manually. */
    @Nullable String approvalUrl,
    /** Mirrors the SLACK_COPY_LINK_ENABLED env flag. The picker UI uses
     *  this to decide whether to render the "Notify reviewers in Slack"
     *  checkbox and the copy-approval-link affordance. */
    boolean copyLinkEnabled
  ) {
    static @NotNull GroupsResource.JoinInfo forJoinAnalysis(
      @NotNull JitGroupContext g,
      boolean copyLinkEnabled
    ) {
      var joinOp = g.join();
      var analysis = joinOp.dryRun();

      JoinStatusInfo status;
      if (analysis.activeMembership().isPresent()) {
        status = JoinStatusInfo.JOINED;
      }
      else if (!analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS)) {
        status = JoinStatusInfo.JOIN_DISALLOWED;
      }
      else if (joinOp.requiresApproval()) {
        status = JoinStatusInfo.JOIN_ALLOWED_WITH_APPROVAL;
      }
      else {
        status = JoinStatusInfo.JOIN_ALLOWED_WITHOUT_APPROVAL;
      }

      return new JoinInfo(
        status,
        new MembershipInfo(
          analysis.activeMembership().isPresent(),
          analysis.activeMembership()
            .map(p -> p.expiry() != null ? p.expiry().getEpochSecond(): null)
            .orElse(null)),
        analysis.satisfiedConstraints().stream()
          .map(c -> new ConstraintInfo(c.name(), c.displayName()))
          .toList(),
        analysis.unsatisfiedConstraints().stream()
          .map(c -> new ConstraintInfo(c.name(), c.displayName()))
          .toList(),
        analysis.input().stream()
          .sorted(Comparator.comparing(p -> p.name()))
          .map(InputInfo::fromProperty)
          .toList(),
        null,
        copyLinkEnabled);
    }

    static @NotNull GroupsResource.JoinInfo forCompletedJoin(
      @NotNull Principal principal,
      @NotNull List<Property> input
    ) {
      return new JoinInfo(
        JoinStatusInfo.JOIN_COMPLETED,
        new MembershipInfo(
          true,
          Optional.ofNullable(principal.expiry())
            .map(Instant::getEpochSecond)
            .get()),
        List.of(), // Don't repeat constraints
        List.of(), // Don't repeat constraints
        input
          .stream()
          .sorted(Comparator.comparing(p -> p.name()))
          .map(InputInfo::fromProperty)
          .toList(),
        null,
        false);
    }

    static @NotNull GroupsResource.JoinInfo forProposal(
      @NotNull List<Property> input
    ) {
      return forProposal(input, null, false);
    }

    static @NotNull GroupsResource.JoinInfo forProposal(
      @NotNull List<Property> input,
      @Nullable String approvalUrl,
      boolean copyLinkEnabled
    ) {
      return new JoinInfo(
        JoinStatusInfo.JOIN_PROPOSED,
        new MembershipInfo(false, null),
        List.of(), // Don't repeat constraints
        List.of(), // Don't repeat constraints
        input
          .stream()
          .sorted(Comparator.comparing(p -> p.name()))
          .map(InputInfo::fromProperty)
          .toList(),
        approvalUrl,
        copyLinkEnabled);
    }
  }

  /**
   * Configuration for this resource. Produced as a CDI singleton in
   * {@code Application.java} from the corresponding
   * {@link ApplicationConfiguration} fields.
   */
  public record Options(
    boolean slackCopyLinkEnabled
  ) {}

  /**
   * Response payload of {@code GET /reviewers}.
   *
   * <p>{@code degraded} means the candidates list is unavailable (Cloud
   * Identity Groups API was down or the JIT SA was missing a
   * permission on a group). The frontend uses this to render an
   * explicit "Reviewer suggestions unavailable" notice instead of
   * silently submitting with an empty selection.
   *
   * <p>The 200 + {@code degraded: true} contract is preferred over a
   * 500 because the picker is best-effort: a failed peer lookup must
   * not block the elevation request itself.
   *
   * <p>Not a {@link MediaInfo} — it's a sub-resource of the group, the
   * frontend requests it explicitly and doesn't dispatch on {@code _type}.
   */
  public record ReviewersInfo(
    @NotNull List<CandidateInfo> candidates,
    boolean degraded
  ) {}

  /**
   * One picker candidate. {@code suggested} marks teammates the
   * requester shares an approver group with — the UI highlights these.
   */
  public record CandidateInfo(
    @NotNull String email,
    @NotNull String displayName,
    boolean suggested
  ) {}

  public record ExternalLinkInfo(
    @NotNull Link self,
    @NotNull Link location
  ) implements MediaInfo {}

  public enum JoinStatusInfo {
    /**
     * The current user lacks permissions to join this group.
     */
    JOIN_DISALLOWED,

    /**
     * The current user is allowed to join this group and doesn't require
     * approval.
     */
    JOIN_ALLOWED_WITHOUT_APPROVAL,

    /**
     * The current user is allowed to join this group, but requires approval.
     */
    JOIN_ALLOWED_WITH_APPROVAL,

    /**
     * The join has been proposed for approval.
     */
    JOIN_PROPOSED,

    /**
     * Join completed, the current user is now a member of the group.
     */
    JOIN_COMPLETED,

    /**
     * The current user is already a member of this group.
     */
    JOINED
  }

  public record MembershipInfo(
    boolean active,
    @Nullable Long expiry
  ) {}


  public record ConstraintInfo(
    @NotNull String name,
    @NotNull String description
  ) {}

  public record InputInfo(
    @NotNull String name,
    @NotNull String description,
    @NotNull String type,
    boolean isRequired,
    @Nullable String value,
    @Nullable String minInclusive,
    @Nullable String maxInclusive
  ) {
    static InputInfo fromProperty(@NotNull Property i) {
      return new InputInfo(
        i.name(),
        i.displayName(),
        i.type().getSimpleName(),
        i.isRequired(),
        i.get(),
        i.minInclusive().orElse(null),
        i.maxInclusive().orElse(null));
    }
  }
}
