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

import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.auth.EndUserId;
import com.google.solutions.jitaccess.auth.GroupResolver;
import com.google.solutions.jitaccess.auth.IamPrincipalId;
import com.google.solutions.jitaccess.auth.JitGroupId;
import com.google.solutions.jitaccess.auth.PrincipalId;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.Proposal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestSlackProposalHandler {

  private static final EndUserId ALICE = new EndUserId("alice@example.com");
  private static final EndUserId BOB = new EndUserId("bob@example.com");
  private static final EndUserId CAROL = new EndUserId("carol@example.com");
  private static final JitGroupId GROUP = new JitGroupId("env", "sys", "grp");
  private static final URI ACTION_URI = URI.create(
    "https://pam.wavemm.net/?activation=jwt-stub");

  private SlackProposalHandler newHandler(
    SlackClient slackClient,
    SlackMessageRegistry registry,
    GroupResolver groupResolver
  ) {
    return new SlackProposalHandler(
      mock(TokenSigner.class),
      slackClient,
      registry,
      groupResolver,
      mock(Logger.class),
      new AbstractProposalHandler.Options(Duration.ofMinutes(60)),
      new SlackProposalHandler.Options(ZoneId.of("UTC")));
  }

  private static GroupResolver groupResolverPassthrough() {
    var resolver = mock(GroupResolver.class);
    try {
      when(resolver.expand(any())).thenAnswer(inv -> {
        Set<PrincipalId> in = inv.getArgument(0);
        return Set.copyOf(in);  // no group expansion in tests
      });
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return resolver;
  }

  private static SlackClient slackClientHappyPath() {
    var client = mock(SlackClient.class);
    when(client.lookupUserByEmail(anyString()))
      .thenAnswer(inv -> CompletableFuture.completedFuture(
        "U-" + inv.<String>getArgument(0).hashCode()));
    when(client.postDirectMessage(anyString(), anyList(), anyString()))
      .thenAnswer(inv -> CompletableFuture.completedFuture(
        new SlackClient.PostedMessage(
          "C-" + inv.<String>getArgument(0),
          "12345.67890")));
    when(client.updateMessage(anyString(), anyString(), anyList(), anyString()))
      .thenReturn(CompletableFuture.completedFuture(null));
    return client;
  }

  private static SlackMessageRegistry registryHappyPath() {
    var registry = mock(SlackMessageRegistry.class);
    when(registry.record(anyString(), anyList(), any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    when(registry.lookup(anyString()))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    when(registry.delete(anyString()))
      .thenReturn(CompletableFuture.completedFuture(null));
    return registry;
  }

  private static JitGroupContext.JoinOperation operationFor(EndUserId user) {
    var op = mock(JitGroupContext.JoinOperation.class);
    when(op.group()).thenReturn(GROUP);
    when(op.user()).thenReturn(user);
    return op;
  }

  private static Proposal proposalFor(
    EndUserId user, Set<IamPrincipalId> recipients
  ) {
    var p = mock(Proposal.class);
    when(p.user()).thenReturn(user);
    when(p.recipients()).thenReturn(recipients);
    when(p.group()).thenReturn(GROUP);
    when(p.input()).thenReturn(Map.of("justification", "CASE-123"));
    when(p.expiry()).thenReturn(Instant.now().plus(Duration.ofMinutes(60)));
    return p;
  }

  private static ProposalHandler.ProposalToken tokenFor(Set<IamPrincipalId> audience) {
    return new ProposalHandler.ProposalToken(
      "stub-jwt",
      audience,
      Instant.now().plus(Duration.ofMinutes(60)));
  }

  // -------------------------------------------------------------------------
  // onOperationProposed.
  // -------------------------------------------------------------------------

  @Test
  public void onOperationProposed_dmsAllReviewersAndRecordsRegistry() throws Exception {
    var slack = slackClientHappyPath();
    var registry = registryHappyPath();
    var handler = newHandler(slack, registry, groupResolverPassthrough());

    var recipients = Set.<IamPrincipalId>of(BOB, CAROL);
    handler.onOperationProposed(
      operationFor(ALICE),
      proposalFor(ALICE, recipients),
      tokenFor(recipients),
      ACTION_URI);

    verify(slack).lookupUserByEmail(eq("bob@example.com"));
    verify(slack).lookupUserByEmail(eq("carol@example.com"));
    verify(slack, times(2)).postDirectMessage(anyString(), anyList(), anyString());

    var entriesCaptor = ArgumentCaptor.forClass(List.class);
    verify(registry).record(anyString(), entriesCaptor.capture(), any());
    var entries = entriesCaptor.getValue();
    assertEquals(2, entries.size());
  }

  @Test
  public void onOperationProposed_skipsReviewersWhenSlackUserNotFound() throws Exception {
    var slack = slackClientHappyPath();
    when(slack.lookupUserByEmail(eq("bob@example.com")))
      .thenReturn(CompletableFuture.completedFuture(null));
    var registry = registryHappyPath();
    var handler = newHandler(slack, registry, groupResolverPassthrough());

    handler.onOperationProposed(
      operationFor(ALICE),
      proposalFor(ALICE, Set.<IamPrincipalId>of(BOB, CAROL)),
      tokenFor(Set.<IamPrincipalId>of(BOB, CAROL)),
      ACTION_URI);

    verify(slack, times(1))
      .postDirectMessage(anyString(), anyList(), anyString());

    var entriesCaptor = ArgumentCaptor.forClass(List.class);
    verify(registry).record(anyString(), entriesCaptor.capture(), any());
    assertEquals(1, entriesCaptor.getValue().size(),
      "registry should only contain the reviewers we successfully DM'd");
  }

  @Test
  public void onOperationProposed_throwsWhenAllRecipientsFail() {
    var slack = slackClientHappyPath();
    when(slack.lookupUserByEmail(anyString()))
      .thenReturn(CompletableFuture.completedFuture(null));
    var registry = registryHappyPath();
    var handler = newHandler(slack, registry, groupResolverPassthrough());

    assertThrows(IOException.class, () ->
      handler.onOperationProposed(
        operationFor(ALICE),
        proposalFor(ALICE, Set.<IamPrincipalId>of(BOB, CAROL)),
        tokenFor(Set.<IamPrincipalId>of(BOB, CAROL)),
        ACTION_URI));

    verify(registry, never()).record(anyString(), anyList(), any());
  }

  @Test
  public void onOperationProposed_throwsWhenNoIndividualUsersAfterExpansion() throws Exception {
    var slack = slackClientHappyPath();
    var registry = registryHappyPath();
    var resolver = mock(GroupResolver.class);
    when(resolver.expand(any())).thenReturn(Set.of());  // group expanded to nothing
    var handler = newHandler(slack, registry, resolver);

    assertThrows(IOException.class, () ->
      handler.onOperationProposed(
        operationFor(ALICE),
        proposalFor(ALICE, Set.<IamPrincipalId>of(BOB)),
        tokenFor(Set.<IamPrincipalId>of(BOB)),
        ACTION_URI));
  }

  // -------------------------------------------------------------------------
  // onProposalApproved.
  // -------------------------------------------------------------------------

  @Test
  public void onProposalApproved_updatesSiblingsButNotApprover() throws Exception {
    var slack = slackClientHappyPath();
    var registry = mock(SlackMessageRegistry.class);
    var entries = List.of(
      new SlackMessageRegistry.ReviewerMessage(
        "bob@example.com", "U-BOB", "C-BOB", "111.111"),
      new SlackMessageRegistry.ReviewerMessage(
        "carol@example.com", "U-CAROL", "C-CAROL", "222.222"));
    when(registry.lookup(anyString()))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(entries)));
    when(registry.delete(anyString()))
      .thenReturn(CompletableFuture.completedFuture(null));

    var handler = newHandler(slack, registry, groupResolverPassthrough());

    var approval = mock(JitGroupContext.ApprovalOperation.class);
    when(approval.user()).thenReturn(BOB);  // Bob is approving

    handler.onProposalApproved(
      approval,
      proposalFor(ALICE, Set.<IamPrincipalId>of(BOB, CAROL)));

    // Carol's DM gets updated; Bob's does NOT (he just approved).
    verify(slack).updateMessage(eq("C-CAROL"), eq("222.222"), anyList(), anyString());
    verify(slack, never()).updateMessage(eq("C-BOB"), anyString(), anyList(), anyString());

    // Beneficiary (Alice) gets a confirmation DM.
    verify(slack).lookupUserByEmail(eq("alice@example.com"));
    verify(slack).postDirectMessage(anyString(), anyList(), anyString());

    // Registry entry deleted after handling.
    verify(registry).delete(anyString());
  }

  @Test
  public void onProposalApproved_stillNotifiesBeneficiaryOnRegistryMiss() throws Exception {
    var slack = slackClientHappyPath();
    var registry = mock(SlackMessageRegistry.class);
    when(registry.lookup(anyString()))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    var handler = newHandler(slack, registry, groupResolverPassthrough());

    var approval = mock(JitGroupContext.ApprovalOperation.class);
    when(approval.user()).thenReturn(BOB);

    handler.onProposalApproved(
      approval,
      proposalFor(ALICE, Set.<IamPrincipalId>of(BOB, CAROL)));

    // No siblings to update (we lost the registry), but Alice still gets her DM.
    verify(slack, never()).updateMessage(anyString(), anyString(), anyList(), anyString());
    verify(slack).lookupUserByEmail(eq("alice@example.com"));
    verify(slack).postDirectMessage(anyString(), anyList(), anyString());
  }
}
