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

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.common.CompletableFutures;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * In-flight registry of Slack DMs sent to reviewers, keyed by a stable
 * fingerprint of the join request.
 *
 * <p>Why this exists: when one reviewer approves in JIT, we need to update
 * the sibling reviewers' DMs in place ({@code chat.update}) to "Approved
 * by X". The JIT JWT contains the recipients but not their Slack message
 * timestamps, so we record (channel, ts) at notification time and look it
 * up at approval time.
 *
 * <p>Storage: a named Firestore Native database in the same GCP project as
 * JIT. The collection is {@value #COLLECTION}; each document key is the
 * SHA-256 of (beneficiary, group, sorted recipient emails). Each document
 * carries a TTL field {@code expires_at} aligned with the JWT expiry; the
 * Firestore TTL policy (managed in wavemm-iam Terraform) auto-deletes
 * stale entries — best-effort, can lag up to 24h.
 */
public class SlackMessageRegistry {
  static final String COLLECTION = "requests";
  static final String FIELD_REVIEWERS = "reviewers";
  static final String FIELD_EXPIRES_AT = "expires_at";

  private final @NotNull Firestore firestore;
  private final @NotNull Executor executor;
  private final @NotNull Logger logger;

  public SlackMessageRegistry(
    @NotNull Firestore firestore,
    @NotNull Executor executor,
    @NotNull Logger logger
  ) {
    this.firestore = firestore;
    this.executor = executor;
    this.logger = logger;
  }

  private @NotNull CollectionReference collection() {
    return this.firestore.collection(COLLECTION);
  }

  /**
   * Compute the stable correlation key used to join {@code RequestActivation}
   * (where we record the Slack messages) and {@code ApprovalOperation}
   * (where we look them back up).
   *
   * <p>Fields chosen for stability across propose/accept:
   * <ul>
   *   <li>{@code beneficiary} — the requester's email
   *   <li>{@code groupId} — the JitGroupId being joined
   *   <li>sorted recipient emails — the reviewer set selected at submission
   * </ul>
   *
   * <p>Justification is intentionally excluded (large, unstable). Time
   * fields are excluded because they have second-precision drift between
   * propose and accept.
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
      // SHA-256 is mandatory in every JRE.
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
    return CompletableFutures.supplyAsync(() -> {
      var doc = new HashMap<String, Object>();
      doc.put(FIELD_EXPIRES_AT, Timestamp.ofTimeSecondsAndNanos(
        expiresAt.getEpochSecond(),
        expiresAt.getNano()));

      var reviewers = new ArrayList<Map<String, Object>>(reviewerMessages.size());
      for (var entry : reviewerMessages) {
        var item = new HashMap<String, Object>();
        item.put("email", entry.email());
        item.put("user_id", entry.userId());
        item.put("channel_id", entry.channelId());
        item.put("message_ts", entry.messageTs());
        reviewers.add(item);
      }
      doc.put(FIELD_REVIEWERS, reviewers);

      try {
        collection().document(requestKey).set(doc).get();
      }
      catch (InterruptedException | ExecutionException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(
          "Failed to write Slack message registry entry " + requestKey, e);
      }
      return null;
    }, this.executor);
  }

  /**
   * Retrieve previously recorded messages for a request, or empty if the
   * entry is missing (no DMs were sent, or already deleted/expired).
   */
  @SuppressWarnings("unchecked")
  public @NotNull CompletableFuture<Optional<List<ReviewerMessage>>> lookup(
    @NotNull String requestKey
  ) {
    return CompletableFutures.supplyAsync(() -> {
      try {
        var snapshot = collection().document(requestKey).get().get();
        if (!snapshot.exists()) {
          return Optional.<List<ReviewerMessage>>empty();
        }
        var reviewers = (List<Map<String, Object>>) snapshot.get(FIELD_REVIEWERS);
        if (reviewers == null) {
          return Optional.<List<ReviewerMessage>>empty();
        }
        var entries = new ArrayList<ReviewerMessage>(reviewers.size());
        for (var item : reviewers) {
          entries.add(new ReviewerMessage(
            (String) item.get("email"),
            (String) item.get("user_id"),
            (String) item.get("channel_id"),
            (String) item.get("message_ts")));
        }
        return Optional.of(List.copyOf(entries));
      }
      catch (InterruptedException | ExecutionException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(
          "Failed to read Slack message registry entry " + requestKey, e);
      }
    }, this.executor);
  }

  /**
   * Remove a request entry once the approval flow is complete. Idempotent.
   */
  public @NotNull CompletableFuture<Void> delete(
    @NotNull String requestKey
  ) {
    return CompletableFutures.supplyAsync(() -> {
      try {
        collection().document(requestKey).delete().get();
      }
      catch (InterruptedException | ExecutionException e) {
        Thread.currentThread().interrupt();
        // Deletion is best-effort; TTL will reap if we lose this race.
        this.logger.warn(
          "slackRegistry.delete.failed",
          "Failed to delete registry entry %s (TTL will eventually reap): %s",
          requestKey, e.getMessage());
      }
      return null;
    }, this.executor);
  }

  /**
   * One reviewer's posted Slack DM, sufficient for chat.update later.
   */
  public record ReviewerMessage(
    @NotNull String email,
    @NotNull String userId,
    @NotNull String channelId,
    @NotNull String messageTs
  ) {}
}
