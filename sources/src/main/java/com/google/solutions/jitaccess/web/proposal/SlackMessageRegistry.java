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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * In-flight registry of Slack DMs sent to reviewers, keyed by a stable
 * fingerprint of the join request.
 *
 * <p>Why this exists: when one reviewer approves in JIT, we need to update
 * the sibling reviewers' DMs in place (chat.update) to "✅ Approved by X".
 * The JIT JWT contains the recipients but not their Slack message
 * timestamps, so we record (channel, ts) at notification time and look it up
 * at approval time.
 *
 * <p>Storage: a named Firestore database in the same GCP project as JIT.
 * The collection is {@value #COLLECTION}; each document key is a SHA-256 of
 * (beneficiary, group, sorted recipients). Documents carry a TTL field
 * {@code expiresAt} aligned with the JWT expiry; Firestore TTL deletes
 * stale entries best-effort within ~24 h.
 *
 * <p>Phase 1: all methods are stubs that log + return empty. Phase 2 wires
 * in the real Firestore client.
 */
public class SlackMessageRegistry {
  static final String COLLECTION = "requests";

  private final @NotNull String databaseId;
  private final @NotNull Executor executor;
  private final @NotNull Logger logger;

  public SlackMessageRegistry(
    @NotNull String databaseId,
    @NotNull Executor executor,
    @NotNull Logger logger
  ) {
    Preconditions.checkArgument(!databaseId.isBlank(), "databaseId must not be blank");
    this.databaseId = databaseId;
    this.executor = executor;
    this.logger = logger;
  }

  /**
   * Compute the stable correlation key used to join {@code RequestActivation}
   * (where we record the Slack messages) and {@code Approval} (where we look
   * them back up).
   *
   * <p>The fields chosen are present and unchanged across both events:
   * <ul>
   *   <li>{@code beneficiary} — the requester's email
   *   <li>{@code group} — the JitGroupId being joined
   *   <li>sorted recipient emails — the reviewer set selected at submission
   * </ul>
   *
   * <p>Justification is intentionally excluded (not part of approval event).
   * Time fields are excluded because they have second-precision drift
   * between propose and accept and are not the canonical identity of the
   * request.
   */
  public static @NotNull String requestKey(
    @NotNull String beneficiary,
    @NotNull String groupId,
    @NotNull List<String> recipientEmails
  ) {
    var sorted = recipientEmails.stream().sorted().toList();
    var canonical = String.join("|",
      beneficiary,
      groupId,
      String.join(",", sorted));
    try {
      var digest = MessageDigest.getInstance("SHA-256")
        .digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    }
    catch (NoSuchAlgorithmException e) {
      // SHA-256 is mandatory in every JRE; this branch is unreachable.
      throw new AssertionError("SHA-256 not available", e);
    }
  }

  /**
   * Persist the Slack messages posted for a request. Caller (the proposal
   * handler) builds the entry list once all reviewer DMs have been posted.
   */
  public @NotNull CompletableFuture<Void> record(
    @NotNull String requestKey,
    @NotNull List<ReviewerMessage> reviewerMessages,
    @NotNull Instant expiresAt
  ) {
    return CompletableFuture.runAsync(() -> {
      // TODO(phase-2): Firestore batch write with TTL field.
      this.logger.info(
        "slackRegistry.record.stub",
        "Phase 1 stub: would persist %d entries for key=%s in db=%s",
        reviewerMessages.size(),
        requestKey,
        this.databaseId);
    }, this.executor);
  }

  /**
   * Retrieve previously recorded messages for a request, or an empty
   * Optional if none exist (no Slack DMs were sent, or the entry has
   * already been deleted / expired).
   */
  public @NotNull CompletableFuture<Optional<List<ReviewerMessage>>> lookup(
    @NotNull String requestKey
  ) {
    return CompletableFuture.supplyAsync(() -> {
      // TODO(phase-2): Firestore document read.
      this.logger.info(
        "slackRegistry.lookup.stub",
        "Phase 1 stub: would lookup key=%s in db=%s",
        requestKey,
        this.databaseId);
      return Optional.<List<ReviewerMessage>>empty();
    }, this.executor);
  }

  /**
   * Remove a request entry once the approval flow is complete. Idempotent;
   * a missing entry is not an error (TTL may have already deleted it).
   */
  public @NotNull CompletableFuture<Void> delete(
    @NotNull String requestKey
  ) {
    return CompletableFuture.runAsync(() -> {
      // TODO(phase-2): Firestore document delete.
      this.logger.info(
        "slackRegistry.delete.stub",
        "Phase 1 stub: would delete key=%s in db=%s",
        requestKey,
        this.databaseId);
    }, this.executor);
  }

  /**
   * One reviewer's posted Slack DM, sufficient for chat.update later.
   *
   * @param email     reviewer email (for sibling-update messages that name them)
   * @param userId    Slack user id, for re-resolution if needed
   * @param channelId DM channel returned by conversations.open
   * @param messageTs message timestamp returned by chat.postMessage
   */
  public record ReviewerMessage(
    @NotNull String email,
    @NotNull String userId,
    @NotNull String channelId,
    @NotNull String messageTs
  ) {}
}
