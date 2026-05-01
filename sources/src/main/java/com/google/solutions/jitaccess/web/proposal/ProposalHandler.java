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

package com.google.solutions.jitaccess.web.proposal;

import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.auth.EndUserId;
import com.google.solutions.jitaccess.auth.IamPrincipalId;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.Proposal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.function.Function;

/**
 * Tracks the state of proposals.
 */
public interface ProposalHandler {
  /**
   * Express the intent to join a group and solicit approval
   * from an authorized user.
   */
  default @NotNull ProposalHandler.ProposalToken propose(
    @NotNull JitGroupContext.JoinOperation joinOperation,
    @NotNull Function<String, URI> buildActionUri
  ) throws AccessException {
    return propose(joinOperation, buildActionUri, ProposeOptions.DEFAULT);
  }

  /**
   * Variant accepting per-request options:
   * <ul>
   *   <li>{@link ProposeOptions#reviewerFilter()} — narrow the set of
   *       qualified peers to a subset selected by the requester (the
   *       picker UX in the wavemm fork). Null = no filter.
   *   <li>{@link ProposeOptions#notifyReviewers()} — when false, the
   *       handler skips out-of-band notification (Slack DMs / email) but
   *       still generates and signs the JWT, returning the
   *       {@link ProposalToken} to the caller. Used by the "copy
   *       approval link" flow where the requester shares the link
   *       manually.
   * </ul>
   */
  @NotNull ProposalHandler.ProposalToken propose(
    @NotNull JitGroupContext.JoinOperation joinOperation,
    @NotNull Function<String, URI> buildActionUri,
    @NotNull ProposeOptions options
  ) throws AccessException;

  /**
   * Per-request options for {@link #propose}.
   *
   * @param reviewerFilter when non-null, narrows the recipients to
   *                       individuals selected by the requester
   * @param notifyReviewers when false, the proposal token is generated
   *                        but no Slack/email notification is delivered
   */
  record ProposeOptions(
    @Nullable Set<EndUserId> reviewerFilter,
    boolean notifyReviewers
  ) {
    public static final @NotNull ProposeOptions DEFAULT =
      new ProposeOptions(null, true);
  }

  /**
   * Accept a proposal.
   */
  @NotNull Proposal accept(
    @NotNull String proposalToken
  ) throws AccessException;

  /**
   * Token that encodes all information about a proposal in a tamper-proof
   * way, suitable for exchanging in URLs and/or email messages.
   */
  record ProposalToken(
    @NotNull String value,
    @NotNull Set<IamPrincipalId> audience,
    @NotNull Instant expiryTime
  ) {
    @Override
    public String toString() {
      return this.value;
    }
  }
}
