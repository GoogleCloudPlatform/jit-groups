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

package com.google.solutions.jitaccess.catalog;

import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.auth.EndUserId;
import com.google.solutions.jitaccess.auth.IamPrincipalId;
import com.google.solutions.jitaccess.auth.JitGroupId;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public interface Proposal {
  /**
   * User that initiated the proposal.
   */
  @NotNull EndUserId user();

  /**
   * Get group that the user is trying to join.
   */
  @NotNull JitGroupId group();

  /**
   * Users that the operation was proposed to.
   */
  @NotNull Set<IamPrincipalId> recipients();

  /**
   * Expiry for the proposal.
   */
  @NotNull Instant expiry();

  /**
   * Input provided by user.
   */
  @NotNull Map<String, String> input();

  /**
   * Wavemm fork: whether the requester opted in to having the proposal
   * handler deliver out-of-band notification (Slack DMs / email) to
   * reviewers, vs. opting out to copy and share the approval URL
   * manually.
   *
   * <p>When false:
   * <ul>
   *   <li>{@code AbstractProposalHandler.propose} skips the
   *       {@code onOperationProposed} hook — no DMs, no Firestore
   *       registry entry.
   *   <li>{@code SlackProposalHandler.onProposalApproved} short-
   *       circuits the sibling-update path (there are no siblings to
   *       update because no DMs were sent), keeping only the
   *       beneficiary-confirmation DM. The previous "no Slack
   *       registry entry" warning becomes a routine INFO log.
   * </ul>
   *
   * <p>The flag is encoded as a JWT claim so it survives the
   * propose → accept round-trip and the approver doesn't need to
   * re-derive the requester's intent from the request side.
   *
   * <p>Default: {@code true}, matching upstream behaviour where
   * notification is always attempted.
   */
  default boolean notifyReviewers() {
    return true;
  }

  /**
   * Invoked when the proposal was completed successfully.
   */
  void onCompleted(
    @NotNull JitGroupContext.ApprovalOperation op
  ) throws AccessException, IOException;
}
