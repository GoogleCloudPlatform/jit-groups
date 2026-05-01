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

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.auth.EndUserId;
import com.google.solutions.jitaccess.auth.GroupResolver;
import com.google.solutions.jitaccess.auth.PrincipalId;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.Proposal;
import com.google.solutions.jitaccess.web.proposal.SlackMessageRegistry.ReviewerMessage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * Proposal handler that delivers approval requests via Slack DMs instead
 * of email. Replaces (does not compose with) {@link MailProposalHandler}
 * when Slack is configured — see SLACK_INTEGRATION.md.
 *
 * <p>Flow on {@link #onOperationProposed}:
 * <ol>
 *   <li>Expand {@code proposal.recipients()} from a mix of users + groups
 *       into a flat set of individual users via {@link GroupResolver}
 *       (one round of expansion — non-recursive, same behaviour as
 *       the rest of JIT).
 *   <li>Resolve each user's email to a Slack user ID.
 *   <li>DM each resolved user with a Block Kit "review request" carrying
 *       the JWT-bearing {@code action_uri}.
 *   <li>Persist {(channel, ts)} per reviewer to Firestore so siblings can
 *       be updated when one reviewer approves.
 * </ol>
 *
 * <p>Flow on {@link #onProposalApproved}:
 * <ol>
 *   <li>Look up the registry entry by request key.
 *   <li>For each non-approver sibling, {@code chat.update} the original DM
 *       to "Already approved by X — no action needed".
 *   <li>DM the beneficiary "Your elevation was approved by X".
 *   <li>Delete the registry entry.
 * </ol>
 *
 * <p>All Slack and Firestore calls are async. Failures are logged at WARN
 * but never propagated to the JIT request thread — a Slack outage must
 * not block legitimate elevation requests. The exception is the initial
 * {@code onOperationProposed}: if every recipient fails to be DM'd we
 * surface the error so the requester knows the request didn't land
 * anywhere actionable.
 */
public class SlackProposalHandler extends AbstractProposalHandler {
  private final @NotNull SlackClient slackClient;
  private final @NotNull SlackMessageRegistry registry;
  private final @NotNull GroupResolver groupResolver;
  private final @NotNull Logger logger;
  private final @NotNull Options slackOptions;

  /**
   * Hard ceiling on the number of in-flight Slack API call chains during
   * the per-reviewer fan-out in {@link #onOperationProposed}. A policy
   * with a 200-member approver group would otherwise launch 200 parallel
   * lookup→postMessage chains and risk tripping Slack's per-workspace
   * Tier 4 quota (chat.postMessage ~100/min). With a Semaphore in front
   * of each chain, peak in-flight calls are bounded regardless of how
   * many reviewers a policy expands to. Permits are released in
   * {@code .whenComplete} so failures don't leak the slot.
   */
  private final @NotNull Semaphore fanOutLimiter;

  public SlackProposalHandler(
    @NotNull TokenSigner tokenSigner,
    @NotNull SlackClient slackClient,
    @NotNull SlackMessageRegistry registry,
    @NotNull GroupResolver groupResolver,
    @NotNull Logger logger,
    @NotNull AbstractProposalHandler.Options baseOptions,
    @NotNull Options slackOptions
  ) {
    // Crypto-random for JWT IDs — these are activation token nonces, must
    // be unpredictable to prevent enumeration. Matches MailProposalHandler
    // and DebugProposalHandler.
    super(tokenSigner, new SecureRandom(), baseOptions);
    this.slackClient = slackClient;
    this.registry = registry;
    this.groupResolver = groupResolver;
    this.logger = logger;
    this.slackOptions = slackOptions;
    this.fanOutLimiter = new Semaphore(slackOptions.maxConcurrentFanOut(), /*fair*/ true);
  }

  /**
   * Compute the registry fingerprint of a proposal — beneficiary, group,
   * resolved reviewer emails, and the SHA-256 key derived from those.
   *
   * <p>Used by both {@link #onOperationProposed} (where we record the entry)
   * and {@link #onProposalApproved} (where we look it back up). Computing
   * it in a single helper avoids drift between the two sides — they must
   * compute the same key or the lookup misses and siblings don't get
   * updated.
   *
   * <p>Group expansion is intentionally part of the fingerprint: if the
   * policy ACL names a group, the propose-side and accept-side both pass
   * through {@link GroupResolver#expand}, ending up with the same flat
   * email set assuming Cloud Identity returns the same membership for
   * both calls (which it should within the JWT validity window).
   */
  private @NotNull RegistryFingerprint fingerprint(
    @NotNull Proposal proposal
  ) throws AccessException {
    var beneficiary = proposal.user().email;
    var groupId = proposal.group().toString();
    Set<PrincipalId> expanded = this.groupResolver.expand(
      new HashSet<>(proposal.recipients()));
    var reviewerEmails = expanded.stream()
      .filter(EndUserId.class::isInstance)
      .map(p -> ((EndUserId) p).email)
      // Drop the requester from the expanded reviewer set. The upstream
      // `JoinOperation.propose` filter excludes the requester at *principal*
      // level (group ≠ user), so when the policy ACL names a group that the
      // requester also belongs to (the typical "team approves team" pattern),
      // the requester sneaks back in after group expansion. Without this we'd
      // DM the requester their own approval request.
      .filter(email -> !email.equalsIgnoreCase(beneficiary))
      .distinct()
      .sorted()
      .toList();
    var key = this.registry.requestKey(beneficiary, groupId, reviewerEmails);
    return new RegistryFingerprint(beneficiary, groupId, reviewerEmails, key);
  }

  private record RegistryFingerprint(
    @NotNull String beneficiary,
    @NotNull String groupId,
    @NotNull List<String> reviewerEmails,
    @NotNull String key
  ) {}

  @Override
  void onOperationProposed(
    @NotNull JitGroupContext.JoinOperation operation,
    @NotNull Proposal proposal,
    @NotNull ProposalHandler.ProposalToken token,
    @NotNull URI actionUri
  ) throws AccessException, IOException {
    RegistryFingerprint fp;
    try {
      fp = fingerprint(proposal);
    }
    catch (AccessException e) {
      // Surface the underlying cause; AbstractProposalHandler will wrap
      // this into AccessDeniedException("Creating a proposal failed", e)
      // which the user sees as a 403 with no detail. Logging here makes
      // sure the actual reason (typically a Cloud Identity Groups API
      // permission gap) is visible in Cloud Logging.
      this.logger.error(
        "slack.fingerprint.failed",
        "Resolving reviewers via GroupResolver failed for requester=%s "
          + "group=%s — likely a Cloud Identity Groups API permission gap "
          + "on the JIT service account.",
        proposal.user().email,
        proposal.group().toString(),
        e);
      throw e;
    }

    if (fp.reviewerEmails().isEmpty()) {
      this.logger.error(
        "slack.noReviewers",
        "Group %s expanded to zero individual users (after excluding the "
          + "requester %s). Either the policy ACL is misconfigured or the "
          + "approver group has no listable members.",
        fp.groupId(), fp.beneficiary());
      throw new IOException(
        "No qualified reviewers resolved to individual users for " + fp.groupId());
    }

    var justification = proposal.input().getOrDefault("justification", "");

    var blocks = SlackMessages.reviewRequest(
      fp.beneficiary(),
      fp.groupId(),
      justification,
      token.expiryTime(),
      actionUri,
      this.slackOptions.notificationTimeZone());
    var fallback = SlackMessages.reviewRequestFallback(fp.beneficiary(), fp.groupId());

    //
    // Resolve users + post DMs in parallel. Per-reviewer the work is two
    // sequential Slack calls (lookupByEmail → conversations.open +
    // chat.postMessage), but the per-reviewer chains run concurrently so
    // total wallclock latency is one round-trip pair rather than N. Each
    // chain produces an Optional<ReviewerMessage>: empty when the user
    // is not in the workspace, populated when DM landed. Failures end up
    // as exceptionally-completed futures we sweep up with .handle().
    //
    //
    // Per-reviewer outcome enum so the post-loop can distinguish three
    // failure modes that the upstream lump-everything error didn't:
    //   POSTED        — DM landed
    //   NOT_IN_SLACK  — users.lookupByEmail returned null (not an
    //                   error; the email simply isn't a Slack workspace
    //                   member). Common for external-collaborator
    //                   policies; not worth alarming on.
    //   API_FAILURE   — an exception bubbled out of either Slack call.
    //                   Typical: invalid token, missing scope, 5xx.
    //                   Worth alerting on.
    // Tracking these separately means the "all failed" error message
    // tells the operator whether to fix Slack config or fix the policy.
    //
    //
    // Per-reviewer chains are gated through fanOutLimiter so we never
    // have more than slackOptions.maxConcurrentFanOut() in-flight Slack
    // API call pairs at once. A 200-member approver-group policy fans
    // out at the cap rate instead of slamming the workspace Tier 4
    // budget. Permits are released in .whenComplete so the slot frees
    // even when the chain failed exceptionally.
    //
    var perReviewerFutures = fp.reviewerEmails().stream()
      .map(email -> {
        try {
          this.fanOutLimiter.acquire();
        }
        catch (InterruptedException ie) {
          // Caller's thread was interrupted while we were holding the
          // line; surface as a NOT_IN_SLACK-equivalent skip rather than
          // crashing the whole propose call. The interrupt status is
          // restored so upstream code can react.
          Thread.currentThread().interrupt();
          return CompletableFuture.completedFuture(
            new DmOutcome(email, null, ReviewerOutcome.API_FAILURE));
        }
        return this.slackClient.lookupUserByEmail(email)
          .thenCompose(userId -> {
            if (userId == null) {
              this.logger.warn(
                "slack.lookupByEmail.notFound",
                "Reviewer %s is not in the Slack workspace; skipping",
                email);
              return CompletableFuture.completedFuture(
                new DmOutcome(email, null, ReviewerOutcome.NOT_IN_SLACK));
            }
            return this.slackClient.postDirectMessage(userId, blocks, fallback)
              .thenApply(msg -> new DmOutcome(
                email,
                new ReviewerMessage(email, userId, msg.channelId(), msg.messageTs()),
                ReviewerOutcome.POSTED));
          })
          .handle((outcome, ex) -> {
            if (ex != null) {
              var cause = (ex.getCause() != null) ? ex.getCause() : ex;
              this.logger.warn(
                "slack.dm.failed",
                "Failed to DM reviewer %s for %s: %s",
                email, fp.groupId(), cause.getMessage());
              return new DmOutcome(email, null, ReviewerOutcome.API_FAILURE);
            }
            return outcome;
          })
          // Free the concurrency slot on every completion path
          // (success, NOT_IN_SLACK, API_FAILURE). Use whenComplete so
          // the value passes through unchanged.
          .whenComplete((__, ___) -> this.fanOutLimiter.release());
      })
      .toList();

    var posted = new ArrayList<ReviewerMessage>();
    var notInSlack = new ArrayList<String>();
    var apiFailures = new ArrayList<String>();
    for (var future : perReviewerFutures) {
      DmOutcome outcome;
      try {
        outcome = future.join();
      }
      catch (RuntimeException e) {
        // .handle() above already converts failures to API_FAILURE
        // outcomes, so this catch is defensive only.
        outcome = null;
      }
      if (outcome == null || outcome.outcome() == ReviewerOutcome.API_FAILURE) {
        apiFailures.add(outcome != null ? outcome.email() : "<unknown>");
      } else if (outcome.outcome() == ReviewerOutcome.NOT_IN_SLACK) {
        notInSlack.add(outcome.email());
      } else {
        posted.add(outcome.message());
      }
    }

    if (posted.isEmpty()) {
      if (apiFailures.isEmpty()) {
        // All recipients are policy-defined emails that aren't in the
        // Slack workspace — typically a misconfigured policy ACL
        // rather than an outage. Surface a different code so it
        // alerts on a different runbook.
        this.logger.error(
          "slack.allReviewersNotInWorkspace",
          "Every one of %d reviewer(s) on %s is unknown to the Slack "
            + "workspace. Likely the policy ACL grants APPROVE_OTHERS to "
            + "principals who aren't Slack users. emails=%s",
          fp.reviewerEmails().size(), fp.groupId(), notInSlack);
        throw new IOException(
          "None of the " + fp.reviewerEmails().size()
            + " qualified reviewers on " + fp.groupId()
            + " are in the Slack workspace");
      }
      this.logger.error(
        "slack.allDmsFailed",
        "Slack DM delivery failed for every one of %d reviewer(s) on %s. "
          + "See preceding slack.dm.failed entries for the per-reviewer "
          + "cause (typical: missing Slack scope, invalid bot token, "
          + "5xx). apiFailures=%d notInSlack=%d",
        fp.reviewerEmails().size(), fp.groupId(),
        apiFailures.size(), notInSlack.size());
      throw new IOException(
        "Slack DM delivery failed for every reviewer (" + fp.reviewerEmails().size()
          + ") on " + fp.groupId());
    }
    var failures = new ArrayList<String>();
    failures.addAll(notInSlack);
    failures.addAll(apiFailures);

    try {
      this.registry.record(fp.key(), posted, token.expiryTime()).join();
    }
    catch (RuntimeException e) {
      // Registry write failure is bad — siblings won't update on approval —
      // but the approval can still proceed via the live DM links. Log loud.
      this.logger.error(
        "slackRegistry.record.failed",
        "Failed to persist Slack message registry for key=%s; sibling "
          + "updates will not fire on approval. requester=%s group=%s",
        fp.key(), fp.beneficiary(), fp.groupId(), e);
    }

    this.logger.info(
      "slack.onOperationProposed",
      "Posted %d/%d Slack DMs for %s requesting %s (key=%s, failures=%s)",
      posted.size(), fp.reviewerEmails().size(), fp.beneficiary(), fp.groupId(),
      fp.key(), failures);
  }

  @Override
  void onProposalApproved(
    @NotNull JitGroupContext.ApprovalOperation operation,
    @NotNull Proposal proposal
  ) throws AccessException, IOException {
    var fp = fingerprint(proposal);
    var approverEmail = operation.user().email;

    //
    // Wavemm fork: when the requester opted out of automated
    // notification (notifyReviewers=false), no DMs were sent and no
    // Firestore registry entry was ever written. There are no sibling
    // DMs to update — only the beneficiary needs a confirmation that
    // their request was approved. Skip the registry round-trip
    // entirely; logging the absence as INFO instead of WARN avoids
    // false-positive alerts on the operator side.
    //
    if (!proposal.notifyReviewers()) {
      this.logger.info(
        "slack.onProposalApproved.optOut",
        "Approving request key=%s with notifyReviewers=false: no "
          + "registry to update, only DMing the beneficiary. "
          + "requester=%s group=%s approver=%s",
        fp.key(), fp.beneficiary(), fp.groupId(), approverEmail);
      notifyBeneficiary(fp.beneficiary(), fp.groupId(), approverEmail);
      return;
    }

    var entriesOpt = this.registry.lookup(fp.key()).join();
    if (entriesOpt.isEmpty()) {
      this.logger.warn(
        "slackRegistry.lookup.miss",
        "No Slack registry entry for approved request key=%s; siblings "
          + "won't be updated. requester=%s group=%s approver=%s",
        fp.key(), fp.beneficiary(), fp.groupId(), approverEmail);
      // Still notify the beneficiary directly.
      notifyBeneficiary(fp.beneficiary(), fp.groupId(), approverEmail);
      return;
    }

    var siblingBlocks = SlackMessages.reviewerSiblingUpdate(
      fp.beneficiary(), fp.groupId(), approverEmail);
    var siblingFallback = SlackMessages.reviewerSiblingUpdateFallback(approverEmail);

    for (var entry : entriesOpt.get()) {
      if (entry.email().equalsIgnoreCase(approverEmail)) {
        // The approver doesn't need a "you approved" update — they did it.
        continue;
      }
      try {
        this.slackClient.updateMessage(
          entry.channelId(), entry.messageTs(), siblingBlocks, siblingFallback).join();
      }
      catch (RuntimeException e) {
        var cause = e.getCause() != null ? e.getCause() : e;
        this.logger.warn(
          "slack.siblingUpdate.failed",
          "Failed to chat.update sibling DM %s/%s for %s: %s",
          entry.channelId(), entry.messageTs(), entry.email(), cause.getMessage());
      }
    }

    notifyBeneficiary(fp.beneficiary(), fp.groupId(), approverEmail);

    try {
      this.registry.delete(fp.key()).join();
    }
    catch (RuntimeException e) {
      // Best-effort; TTL will reap.
    }

    this.logger.info(
      "slack.onProposalApproved",
      "Updated %d sibling DM(s) for approved request key=%s (approver=%s)",
      Math.max(0, entriesOpt.get().size() - 1), fp.key(), approverEmail);
  }

  private void notifyBeneficiary(
    @NotNull String beneficiary,
    @NotNull String groupId,
    @NotNull String approverEmail
  ) {
    try {
      String userId = this.slackClient.lookupUserByEmail(beneficiary).join();
      if (userId == null) {
        this.logger.warn(
          "slack.lookupByEmail.notFound",
          "Beneficiary %s is not in the Slack workspace; skipping confirmation DM",
          beneficiary);
        return;
      }
      this.slackClient.postDirectMessage(
        userId,
        SlackMessages.beneficiaryApproved(groupId, approverEmail),
        SlackMessages.beneficiaryApprovedFallback(groupId, approverEmail)).join();
    }
    catch (RuntimeException e) {
      var cause = e.getCause() != null ? e.getCause() : e;
      this.logger.warn(
        "slack.beneficiaryDM.failed",
        "Failed to DM beneficiary %s for approved %s: %s",
        beneficiary, groupId, cause.getMessage());
    }
  }

  /**
   * Slack-specific options.
   *
   * @param notificationTimeZone time zone used to render expiry
   *                             timestamps in DMs
   * @param maxConcurrentFanOut  hard ceiling on concurrent
   *                             {@code lookupUserByEmail+postMessage}
   *                             chains during {@link
   *                             #onOperationProposed}. Defaults via
   *                             {@link #DEFAULT_MAX_CONCURRENT_FAN_OUT}
   *                             to a value sized below Slack's Tier 4
   *                             quota when policies have many
   *                             reviewers; tune downward only on
   *                             quota-pressure incidents.
   */
  public record Options(
    @NotNull ZoneId notificationTimeZone,
    int maxConcurrentFanOut
  ) {
    /**
     * Default chosen so that even a 200-reviewer approver-group
     * fan-out completes inside the typical request timeout while
     * staying well under chat.postMessage Tier 4 (≈100/min/workspace
     * sustained, with short bursts allowed). 8 in-flight × ~200 ms
     * round-trip ≈ 40 req/s peak, settling around 1 req/s after
     * Slack's leaky-bucket smoothing.
     */
    public static final int DEFAULT_MAX_CONCURRENT_FAN_OUT = 8;

    public Options {
      Preconditions.checkArgument(
        notificationTimeZone != null,
        "notificationTimeZone must not be null");
      Preconditions.checkArgument(
        maxConcurrentFanOut > 0,
        "maxConcurrentFanOut must be > 0");
    }

    /** Convenience constructor that uses {@link #DEFAULT_MAX_CONCURRENT_FAN_OUT}. */
    public Options(@NotNull ZoneId notificationTimeZone) {
      this(notificationTimeZone, DEFAULT_MAX_CONCURRENT_FAN_OUT);
    }
  }

  /**
   * Per-reviewer outcome of the parallel DM fan-out in {@link
   * #onOperationProposed}. Lets the post-loop distinguish "user isn't
   * in Slack" from "Slack API broke" so the catastrophic
   * "everybody-failed" error message can name the right culprit.
   */
  private enum ReviewerOutcome {
    POSTED,
    NOT_IN_SLACK,
    API_FAILURE
  }

  private record DmOutcome(
    @NotNull String email,
    ReviewerMessage message,
    @NotNull ReviewerOutcome outcome
  ) {}
}
