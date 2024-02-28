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
import com.google.solutions.jitaccess.catalog.Proposal;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.auth.IamPrincipalId;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
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
  @NotNull ProposalHandler.ProposalToken propose(
    @NotNull JitGroupContext.JoinOperation joinOperation,
    @NotNull Function<String, URI> buildActionUri
  ) throws AccessException, IOException;

  /**
   * Accept a proposal.
   */
  @NotNull Proposal accept(
    @NotNull String proposalToken
  ) throws AccessException, IOException;

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
