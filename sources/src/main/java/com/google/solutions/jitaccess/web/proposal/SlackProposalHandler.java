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
    var key = SlackMessageRegistry.requestKey(beneficiary, groupId, reviewerEmails);
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
    // Resolve users + post DMs in parallel. Aggregate failures: if at least
    // one DM lands, the approval flow is viable, so we record the
    // successful subset and warn on the rest. If zero land, we throw —
    // the requester needs to know nobody got the message.
    //
    var posted = new ArrayList<ReviewerMessage>();
    var failures = new ArrayList<String>();

    for (var email : fp.reviewerEmails()) {
      try {
        String userId = this.slackClient.lookupUserByEmail(email).join();
        if (userId == null) {
          this.logger.warn(
            "slack.lookupByEmail.notFound",
            "Reviewer %s is not in the Slack workspace; skipping",
            email);
          failures.add(email);
          continue;
        }
        SlackClient.PostedMessage message = this.slackClient
          .postDirectMessage(userId, blocks, fallback)
          .join();
        posted.add(new ReviewerMessage(
          email, userId, message.channelId(), message.messageTs()));
      }
      catch (RuntimeException e) {
        var cause = e.getCause() != null ? e.getCause() : e;
        this.logger.warn(
          "slack.dm.failed",
          "Failed to DM reviewer %s for %s: %s",
          email, fp.groupId(), cause.getMessage());
        failures.add(email);
      }
    }

    if (posted.isEmpty()) {
      this.logger.error(
        "slack.allDmsFailed",
        "Slack DM delivery failed for every one of %d reviewer(s) on %s. "
          + "See preceding slack.dm.failed entries for the per-reviewer "
          + "cause (typical: missing Slack scope, invalid bot token, or "
          + "user not in the workspace).",
        fp.reviewerEmails().size(), fp.groupId());
      throw new IOException(
        "Slack DM delivery failed for every reviewer (" + fp.reviewerEmails().size()
          + ") on " + fp.groupId());
    }

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
   * @param notificationTimeZone Time zone used to render expiry timestamps in DMs.
   */
  public record Options(
    @NotNull ZoneId notificationTimeZone
  ) {
    public Options {
      Preconditions.checkArgument(
        notificationTimeZone != null,
        "notificationTimeZone must not be null");
    }
  }
}
