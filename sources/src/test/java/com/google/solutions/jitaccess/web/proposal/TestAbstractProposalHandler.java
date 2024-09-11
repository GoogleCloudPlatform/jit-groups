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

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.auth.EndUserId;
import com.google.solutions.jitaccess.auth.GroupId;
import com.google.solutions.jitaccess.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.Proposal;
import jakarta.ws.rs.core.UriBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestAbstractProposalHandler {
  private static final EndUserId SAMPLE_USER_1 = new EndUserId("user-1@example.com");
  private static final EndUserId SAMPLE_USER_2 = new EndUserId("user-2@example.com");
  private static final EndUserId SAMPLE_USER_3 = new EndUserId("user-3@example.com");
  private static final GroupId SAMPLE_GROUP = new GroupId("group@example.com");
  private static final JitGroupId SAMPLE_JITGROUP_ID =  new JitGroupId("env", "sys", "group-1");

  private class SampleProposalHandler extends AbstractProposalHandler {
    public SampleProposalHandler(
      @NotNull TokenSigner tokenSigner) {
      super(
        tokenSigner,
        Mockito.mock(Random.class),
        new Options(Duration.ofMinutes(1)));
    }

    @Override
    void onOperationProposed(
      @NotNull JitGroupContext.JoinOperation operation,
      @NotNull Proposal proposal,
      @NotNull ProposalHandler.ProposalToken token,
      @NotNull URI actionUri
    ) {
    }

    @Override
    void onProposalApproved(
      @NotNull JitGroupContext.ApprovalOperation operation,
      @NotNull Proposal proposal
    ) {
    }
  }

  private class PseudoSigner implements TokenSigner {
    @Override
    public @NotNull TokenWithExpiry sign(
      @NotNull JsonWebToken.Payload payload,
      @NotNull Instant expiry
    ) {
      if (payload.getFactory() == null) {
        payload.setFactory(new GsonFactory());
      }

      return new ServiceAccountSigner.TokenWithExpiry(
        payload.toString(),
        Instant.now(),
        expiry);
    }

    @Override
    public JsonWebToken.Payload verify(
      @NotNull String token
    ) throws TokenVerifier.VerificationException {
      try {
        return new GsonFactory()
          .createJsonParser(token)
          .parse(JsonWebToken.Payload.class);
      }
      catch (IOException e) {
        throw new TokenVerifier.VerificationException(e.getMessage());
      }
    }
  }

  //---------------------------------------------------------------------------
  // propose.
  //---------------------------------------------------------------------------

  @Test
  public void propose_whenInputEmpty() throws Exception {
    var proposal = Mockito.mock(Proposal.class);
    when(proposal.user())
      .thenReturn(SAMPLE_USER_1);
    when(proposal.expiry())
      .thenReturn(Instant.now().plusSeconds(60));
    when(proposal.recipients())
      .thenReturn(Set.of(SAMPLE_USER_2, SAMPLE_USER_3, SAMPLE_GROUP));

    var operation = Mockito.mock(JitGroupContext.JoinOperation.class);
    when(operation.user())
      .thenReturn(SAMPLE_USER_1);
    when(operation.group())
      .thenReturn(SAMPLE_JITGROUP_ID);
    when(operation.propose(any()))
      .thenReturn(proposal);

    var signer = new PseudoSigner();
    var handler = new SampleProposalHandler(signer);
    var token = handler.propose(operation, t -> UriBuilder.newInstance().path("/").build());

    assertEquals(
      "{\"jti\":\"AAAAAAAA\"," +
        "\"rcp\":[\"group:group@example.com\",\"user:user-2@example.com\",\"user:user-3@example.com\"]," +
        "\"grp\":\"jit-group:env.sys.group-1\",\"usr\":\"user:user-1@example.com\",\"inp\":{}}",
      token.value());
  }

  @Test
  public void propose_whenInputNotEmpty() throws Exception {
    var proposal = Mockito.mock(Proposal.class);
    when(proposal.user())
      .thenReturn(SAMPLE_USER_1);
    when(proposal.expiry())
      .thenReturn(Instant.now().plusSeconds(60));
    when(proposal.recipients())
      .thenReturn(Set.of(SAMPLE_USER_2, SAMPLE_USER_3, SAMPLE_GROUP));
    when(proposal.input())
      .thenReturn(Map.of("prop1", "value1"));
    
    var operation = Mockito.mock(JitGroupContext.JoinOperation.class);
    when(operation.user())
      .thenReturn(SAMPLE_USER_1);
    when(operation.group())
      .thenReturn(SAMPLE_JITGROUP_ID);
    when(operation.propose(any()))
      .thenReturn(proposal);

    var signer = new PseudoSigner();
    var handler = new SampleProposalHandler(signer);
    var token = handler.propose(operation, t -> UriBuilder.newInstance().path("/").build());

    assertEquals(
      "{\"jti\":\"AAAAAAAA\"," +
        "\"rcp\":[\"group:group@example.com\",\"user:user-2@example.com\",\"user:user-3@example.com\"]," +
        "\"grp\":\"jit-group:env.sys.group-1\",\"usr\":\"user:user-1@example.com\",\"inp\":{\"prop1\":\"value1\"}}",
      token.value());
  }

  //---------------------------------------------------------------------------
  // accept.
  //---------------------------------------------------------------------------

  @Test
  public void accept_whenTokenVerificationFails() throws Exception {
    var signer = Mockito.mock(TokenSigner.class);
    when(signer.verify(anyString()))
      .thenThrow(new TokenVerifier.VerificationException("mock"));

    var proposalHandler = new SampleProposalHandler(signer);

    assertThrows(
      AccessDeniedException.class,
      () -> proposalHandler.accept("token"));
  }

  @Test
  public void accept_whenInputEmpty() throws Exception {
    var token = "{\"rcp\":[\"user:user-2@example.com\",\"group:group@example.com\"]," +
      "\"grp\":\"jit-group:env.sys.group-1\"," +
      "\"usr\":\"user:user-1@example.com\",\"inp\":{}}";

    var signer = new PseudoSigner();
    var proposalHandler = new SampleProposalHandler(signer);
    var proposal = proposalHandler.accept(token);

     assertEquals(SAMPLE_USER_1, proposal.user());
    assertEquals(SAMPLE_JITGROUP_ID, proposal.group());
     assertEquals(2, proposal.recipients().size());
     assertTrue(proposal.recipients().contains(SAMPLE_USER_2));
     assertTrue(proposal.recipients().contains(SAMPLE_GROUP));
     assertTrue(proposal.input().isEmpty());
  }

  @Test
  public void accept_whenInputNotEmpty() throws Exception {
    var token = "{\"rcp\":[\"user:user-2@example.com\",\"group:group@example.com\"]," +
      "\"grp\":\"jit-group:env.sys.group-1\"," +
      "\"usr\":\"user:user-1@example.com\",\"inp\":{\"prop1\":\"value1\"}}";

    var signer = new PseudoSigner();
    var proposalHandler = new SampleProposalHandler(signer);
    var proposal = proposalHandler.accept(token);

    assertEquals(SAMPLE_USER_1, proposal.user());
    assertEquals(SAMPLE_JITGROUP_ID, proposal.group());
    assertEquals(2, proposal.recipients().size());
    assertTrue(proposal.recipients().contains(SAMPLE_USER_2));
    assertTrue(proposal.recipients().contains(SAMPLE_GROUP));
    assertFalse(proposal.input().isEmpty());
    assertEquals(1, proposal.input().size());
    assertEquals("value1", proposal.input().get("prop1"));
  }
}
