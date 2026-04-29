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
import com.google.solutions.jitaccess.auth.IamPrincipalId;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.Proposal;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Proposal handler that delivers approval requests via Slack DMs instead of
 * email.
 *
 * <p>Replaces (does not compose with) {@link MailProposalHandler} when Slack
 * is configured — see SLACK_INTEGRATION.md for the architectural rationale.
 *
 * <p>Phase 1 (this iteration): the handler is wired into the DI graph,
 * receives proposal events, and logs structured "would notify" lines. The
 * approval flow continues to work end-to-end; reviewers just don't actually
 * receive Slack messages yet. This lets us validate config plumbing,
 * feature-flag behaviour, and the JIT submodule deploy on staging before
 * enabling real Slack traffic in Phase 2.
 *
 * <p>Phase 2 will replace the stubs with real {@link SlackClient} +
 * {@link SlackMessageRegistry} calls. The contract of this class — its
 * constructor signature, the abstract methods it overrides, and the
 * threading model (async, must not throw on Slack errors) — is intended to
 * remain stable across phases.
 */
public class SlackProposalHandler extends AbstractProposalHandler {
  private final @NotNull SlackClient slackClient;
  private final @NotNull SlackMessageRegistry registry;
  private final @NotNull Logger logger;
  private final @NotNull Options slackOptions;

  public SlackProposalHandler(
    @NotNull TokenSigner tokenSigner,
    @NotNull SlackClient slackClient,
    @NotNull SlackMessageRegistry registry,
    @NotNull Logger logger,
    @NotNull AbstractProposalHandler.Options baseOptions,
    @NotNull Options slackOptions
  ) {
    super(tokenSigner, new Random(), baseOptions);
    this.slackClient = slackClient;
    this.registry = registry;
    this.logger = logger;
    this.slackOptions = slackOptions;
  }

  @Override
  void onOperationProposed(
    @NotNull JitGroupContext.JoinOperation operation,
    @NotNull Proposal proposal,
    @NotNull ProposalHandler.ProposalToken token,
    @NotNull URI actionUri
  ) throws AccessException, IOException {
    var recipientEmails = proposal.recipients().stream()
      .filter(EndUserId.class::isInstance)
      .map(IamPrincipalId::value)
      .sorted()
      .collect(Collectors.toUnmodifiableList());

    var requestKey = SlackMessageRegistry.requestKey(
      proposal.user().value(),
      operation.group().toString(),
      recipientEmails);

    // TODO(phase-2):
    //  1. for each recipient: slackClient.lookupUserByEmail → postDirectMessage
    //     (Block Kit: requester, group, justification, expiry, "Approve in JIT" link)
    //  2. registry.record(requestKey, postedMessages, token.expiryTime())
    //  3. all async; failures logged but not thrown.
    this.logger.info(
      "slack.onOperationProposed.stub",
      "Phase 1 stub: requester=%s group=%s recipients=%d key=%s actionUri=%s",
      proposal.user().value(),
      operation.group(),
      recipientEmails.size(),
      requestKey,
      actionUri);
  }

  @Override
  void onProposalApproved(
    @NotNull JitGroupContext.ApprovalOperation operation,
    @NotNull Proposal proposal
  ) throws AccessException, IOException {
    var recipientEmails = proposal.recipients().stream()
      .filter(EndUserId.class::isInstance)
      .map(IamPrincipalId::value)
      .sorted()
      .collect(Collectors.toUnmodifiableList());

    var requestKey = SlackMessageRegistry.requestKey(
      proposal.user().value(),
      proposal.group().toString(),
      recipientEmails);

    // TODO(phase-2):
    //  1. registry.lookup(requestKey)
    //  2. for each posted message NOT belonging to the approver:
    //       slackClient.updateMessage(channel, ts, "✅ Approved by <approver>")
    //  3. for the beneficiary:
    //       slackClient.lookupUserByEmail(proposal.user())
    //       slackClient.postDirectMessage(...) — single result DM.
    //  4. registry.delete(requestKey)
    //  All async; failures logged but not thrown.
    this.logger.info(
      "slack.onProposalApproved.stub",
      "Phase 1 stub: requester=%s group=%s key=%s",
      proposal.user().value(),
      proposal.group(),
      requestKey);
  }

  /**
   * Slack-specific options, complementary to {@link AbstractProposalHandler.Options}.
   *
   * @param notificationTimeZone Time zone used to render expiry timestamps in DMs.
   */
  public record Options(
    @NotNull java.time.ZoneId notificationTimeZone
  ) {
    public Options {
      Preconditions.checkArgument(
        notificationTimeZone != null,
        "notificationTimeZone must not be null");
    }
  }
}
