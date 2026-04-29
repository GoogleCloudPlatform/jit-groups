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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Thin wrapper around the Slack Web API.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Hold the cached bot token (resolved once at startup from Secret Manager).
 *   <li>Resolve email → Slack user id via {@code users.lookupByEmail}.
 *   <li>Post Block-Kit DMs and update them in place.
 * </ul>
 *
 * <p>All public methods are async — Slack outages must not block JIT request
 * threads. Failures are returned as failed futures and logged at
 * {@code WARNING}; the caller (SlackProposalHandler) decides whether to
 * surface or swallow them.
 *
 * <p>Phase 1: skeleton. Methods log "would call Slack" and return successful
 * stub futures so the DI graph wires up and integration tests can compile.
 * Phase 2 replaces the bodies with real {@code com.slack.api} calls.
 */
public class SlackClient {
  private final @NotNull String botToken;
  private final @NotNull Executor executor;
  private final @NotNull Logger logger;

  public SlackClient(
    @NotNull String botToken,
    @NotNull Executor executor,
    @NotNull Logger logger
  ) {
    Preconditions.checkArgument(!botToken.isBlank(), "botToken must not be blank");
    this.botToken = botToken;
    this.executor = executor;
    this.logger = logger;
  }

  /**
   * Look up a Slack user id by email. Returns null if the user is not in the
   * workspace (e.g. external collaborators, or the email is not registered).
   *
   * @param email user's primary email, case-insensitive on Slack's side.
   */
  public @NotNull CompletableFuture<@Nullable String> lookupUserByEmail(
    @NotNull String email
  ) {
    return CompletableFuture.supplyAsync(() -> {
      // TODO(phase-2): MethodsClient.usersLookupByEmail(...).getUser().getId()
      this.logger.info(
        "slack.lookupByEmail.stub",
        "Phase 1 stub: would resolve %s",
        email);
      return null;
    }, this.executor);
  }

  /**
   * Open a DM channel with the user and post a message. Returns the
   * channel id and message timestamp so the caller can later
   * {@link #updateMessage update} it in place.
   *
   * @param slackUserId resolved via {@link #lookupUserByEmail}
   * @param blocksJson  Block Kit blocks array, serialised to JSON
   * @param fallbackText plain-text fallback for notifications and a11y
   */
  public @NotNull CompletableFuture<PostedMessage> postDirectMessage(
    @NotNull String slackUserId,
    @NotNull String blocksJson,
    @NotNull String fallbackText
  ) {
    return CompletableFuture.supplyAsync(() -> {
      // TODO(phase-2):
      //   1. conversations.open(users=[slackUserId]) → channel.id
      //   2. chat.postMessage(channel, blocks, text=fallbackText) → ts
      this.logger.info(
        "slack.postDirectMessage.stub",
        "Phase 1 stub: would DM %s with %d blocks",
        slackUserId,
        blocksJson.length());
      return new PostedMessage("STUB_CHANNEL", "0000000000.000000");
    }, this.executor);
  }

  /**
   * Replace the blocks of an already-posted message. Used to mark sibling
   * reviewer DMs as "approved by X" without removing the original thread.
   */
  public @NotNull CompletableFuture<Void> updateMessage(
    @NotNull String channelId,
    @NotNull String messageTs,
    @NotNull String blocksJson,
    @NotNull String fallbackText
  ) {
    return CompletableFuture.runAsync(() -> {
      // TODO(phase-2): chat.update(channel, ts, blocks, text=fallbackText)
      this.logger.info(
        "slack.updateMessage.stub",
        "Phase 1 stub: would update %s/%s",
        channelId,
        messageTs);
    }, this.executor);
  }

  /**
   * A successfully posted Slack message, identified by (channel, timestamp).
   * Slack's chat.update requires both fields.
   */
  public record PostedMessage(
    @NotNull String channelId,
    @NotNull String messageTs
  ) {}

  /**
   * Construction-time options for the Slack client.
   *
   * <p>Phase 1 only carries the bot token; Phase 2 will likely add timeouts,
   * retry counts, and a deduplication window for chat.postMessage (Slack
   * can deliver the same Block Kit interaction more than once).
   */
  public record Options(
    @NotNull String botToken
  ) {
    public Options {
      Preconditions.checkArgument(!botToken.isBlank(), "botToken must not be blank");
    }

    /**
     * Helper to bundle the construction parameters that callers other than
     * Application.java may want.
     */
    public List<String> debugFields() {
      // Never include the token itself.
      return List.of("botToken=<redacted>");
    }
  }
}
