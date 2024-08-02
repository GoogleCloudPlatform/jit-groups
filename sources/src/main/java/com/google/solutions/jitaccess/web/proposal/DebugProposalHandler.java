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

import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.Proposal;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Implements proposals by printing messages to the console.
 * For debug purposes only
 */
public class DebugProposalHandler extends AbstractProposalHandler {
  public DebugProposalHandler(
    @NotNull TokenSigner tokenSigner
  ) {
    super(tokenSigner, new SecureRandom(), new Options(Duration.ofMinutes(10)));
  }


  //---------------------------------------------------------------------------
  // Overrides.
  //---------------------------------------------------------------------------

  @Override
  void onOperationProposed(
    @NotNull JitGroupContext.JoinOperation operation,
    @NotNull Proposal proposal,
    @NotNull ProposalHandler.ProposalToken token,
    @NotNull URI actionUri
  ) {
    System.out.printf("Operation proposal\n" +
        "  User: %s\n" +
        "  Recipients: %s\n" +
        "  Expiry: %s\n" +
        "  Group: %s\n" +
        "  Token: %s\n" +
        "  Token expiry: %s\n" +
        "  Input:\n%s\n" +
        "  Action: %s\n",
      proposal.user().email,
      proposal.recipients(),
      proposal.expiry(),
      operation.group(),
      token.value(),
      token.expiryTime(),
      operation.input().stream()
        .map(p -> String.format("    %s: %s", p.name(), p.get()))
        .collect(Collectors.joining("\n")),
      actionUri);
  }

  @Override
  void onProposalApproved(
    @NotNull JitGroupContext.ApprovalOperation operation,
    @NotNull Proposal proposal
  ) {
    System.out.printf("Proposal operation completed\n" +
        "  User: %s\n" +
        "  Recipients: %s\n" +
        "  Expiry: %s\n" +
        "  Group: %s\n" +
        "  Input:\n%s\n",
      proposal.user().email,
      proposal.recipients(),
      proposal.expiry(),
      operation.group(),
      operation.input().stream()
        .map(p -> String.format("    %s: %s", p.name(), p.get()))
        .collect(Collectors.joining("\n")));
  }
}
