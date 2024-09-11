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

import com.google.api.client.json.GenericJson;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.auth.EndUserId;
import com.google.solutions.jitaccess.auth.IamPrincipalId;
import com.google.solutions.jitaccess.auth.JitGroupId;
import com.google.solutions.jitaccess.auth.PrincipalId;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.Proposal;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractProposalHandler implements ProposalHandler {
  private final @NotNull TokenSigner tokenSigner;
  private final @NotNull Random jwtIdGenerator;
  private final @NotNull Options options;

  protected AbstractProposalHandler(
    @NotNull TokenSigner tokenSigner,
    @NotNull Random jwtIdGenerator,
    @NotNull Options options
  ) {
    this.tokenSigner = tokenSigner;
    this.jwtIdGenerator = jwtIdGenerator;
    this.options = options;
  }

  /**
   * Notify relevant users about a proposal.
   */
  abstract void onOperationProposed(
    @NotNull JitGroupContext.JoinOperation operation,
    @NotNull Proposal proposal,
    @NotNull ProposalHandler.ProposalToken token,
    @NotNull URI actionUri
    ) throws AccessException, IOException;

  /**
   * Notify relevant users about the completion of a proposal.
   */
  abstract void onProposalApproved(
    @NotNull JitGroupContext.ApprovalOperation operation,
    @NotNull Proposal proposal
  ) throws AccessException, IOException;

  //---------------------------------------------------------------------------
  // ProposalHandler.
  //---------------------------------------------------------------------------

  @Override
  public @NotNull ProposalHandler.ProposalToken propose(
    @NotNull JitGroupContext.JoinOperation joinOperation,
    @NotNull Function<String, URI> buildActionUri
  ) throws AccessException {

    var proposal = joinOperation.propose(
      Instant.now().plus(this.options.tokenExpiry));

    Preconditions.checkArgument(
      !proposal.recipients().isEmpty(),
      "Recipients must not be empty");
    Preconditions.checkArgument(
      !proposal.recipients().contains(proposal.user()),
      "Recipients must not contain the requesting user");

    //
    // Encode all inputs into a token and sign it.
    //
    var inputs = new GenericJson();
    proposal.input()
      .entrySet()
      .forEach(p -> inputs.set(p.getKey(), p.getValue()));

    var jwtId = new byte[6];
    this.jwtIdGenerator.nextBytes(jwtId);

    var payload = new JsonWebToken.Payload()
      .setJwtId(Base64.getEncoder().encodeToString(jwtId))
      .set(Claims.RECIPIENT, proposal.recipients()
        .stream()
        .sorted()
        .map(PrincipalId::toString)
        .toArray())
      .set(Claims.GROUP_ID, joinOperation.group().toString())
      .set(Claims.USER_ID, proposal.user().toString())
      .set(Claims.INPUT, inputs);

    try {
      var signedToken = this.tokenSigner.sign(
        payload,
        proposal.expiry());
      var proposalToken = new ProposalToken(
        signedToken.token(),
        proposal.recipients(),
        signedToken.expiryTime());

      onOperationProposed(
        joinOperation,
        proposal,
        proposalToken,
        buildActionUri.apply(proposalToken.value()));

      return proposalToken;
    }
    catch (AccessException | IOException e) {
      throw new AccessDeniedException(
        "Creating a proposal failed", e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public @NotNull Proposal accept(
    @NotNull String proposalToken
  ) throws AccessException {

    JsonWebToken.Payload payload;
    try {
      payload = this.tokenSigner.verify(proposalToken);
    }
    catch (TokenVerifier.VerificationException e) {
      throw new AccessDeniedException("The proposal token is invalid", e);
    }

    var user = EndUserId
      .parse((String)payload.get(Claims.USER_ID))
      .orElseThrow(() -> new IllegalArgumentException("Invalid user ID"));

    var group = JitGroupId
      .parse((String)payload.get(Claims.GROUP_ID))
      .orElseThrow(() ->
        new AccessDeniedException("The group does not exist or access is denied"));

    var recipients = ((List<String>)payload.get(Claims.RECIPIENT))
      .stream()
      .flatMap(p -> IamPrincipalId.parse(p).stream())
      .collect(Collectors.toSet());

    var input = ((Map<String, Object>)payload.get(Claims.INPUT))
      .entrySet()
      .stream()
      .collect(Collectors.toMap(e -> e.getKey(), e-> (String)e.getValue()));

    return new Proposal() {
      @Override
      public @NotNull EndUserId user() {
        return user;
      }

      @Override
      public @NotNull JitGroupId group() {
        return group;
      }

      @Override
      public @NotNull Set<IamPrincipalId> recipients() {
        return recipients;
      }

      @Override
      public @NotNull Instant expiry() {
        return Instant.ofEpochSecond(payload.getExpirationTimeSeconds());
      }

      @Override
      public @NotNull Map<String, String> input() {
        return input;
      }

      @Override
      public void onCompleted(
        @NotNull JitGroupContext.ApprovalOperation op
      ) throws AccessException, IOException {
        onProposalApproved(op, this);
      }
    };
  }

  static class Claims {
    static final String RECIPIENT = "rcp";
    static final String GROUP_ID = "grp";
    static final String USER_ID = "usr";
    static final String INPUT = "inp";
  }

  public record Options(
    @NotNull Duration tokenExpiry
  ) {
    public Options {
      Preconditions.checkArgument(
        !tokenExpiry.isNegative() && !tokenExpiry.isZero(),
        "Expiry must be positive");
    }
  }
}
