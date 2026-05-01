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
    // Wavemm fork P2-11: requestKey is now an instance method (the
    // HMAC salt lives on the registry). Stub it to return a stable
    // string so downstream calls through anyString()/eq() matchers
    // don't see Mockito's default null and trip NullPointer in
    // SlackProposalHandler.onOperationProposed.
    when(registry.requestKey(anyString(), anyString(), anyList()))
      .thenReturn("test-fingerprint-key");
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
    // Default: notify reviewers (= upstream behaviour). Tests covering
    // the opt-out short-circuit override this with thenReturn(false).
    // Explicitly stubbing here is required because Proposal#notifyReviewers
    // is a Java default method, and Mockito returns boolean default
    // (false) for unstubbed default methods on mocked interfaces.
    when(p.notifyReviewers()).thenReturn(true);
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
    when(registry.requestKey(anyString(), anyString(), anyList()))
      .thenReturn("test-fingerprint-key");
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
    when(registry.requestKey(anyString(), anyString(), anyList()))
      .thenReturn("test-fingerprint-key");
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

  /**
   * P1-2 regression: when the requester opted out of automated DM
   * delivery (notifyReviewers=false), no DMs were sent at propose-time
   * and no Firestore registry entry was ever written. On approval,
   * the handler must SHORT-CIRCUIT — no registry lookup, no sibling
   * updates — and only DM the beneficiary. The previous (Phase 2a)
   * behaviour spuriously WARN-logged "no Slack registry entry" because
   * the lookup miss looked like a Firestore failure mode.
   */
  @Test
  public void onProposalApproved_optOutShortCircuitsRegistryLookup() throws Exception {
    var slack = slackClientHappyPath();
    var registry = mock(SlackMessageRegistry.class);
    when(registry.requestKey(anyString(), anyString(), anyList()))
      .thenReturn("test-fingerprint-key");
    var handler = newHandler(slack, registry, groupResolverPassthrough());

    var approval = mock(JitGroupContext.ApprovalOperation.class);
    when(approval.user()).thenReturn(BOB);

    var proposal = proposalFor(ALICE, Set.<IamPrincipalId>of(BOB, CAROL));
    when(proposal.notifyReviewers()).thenReturn(false);  // opt-out path

    handler.onProposalApproved(approval, proposal);

    // Critical: no registry round-trip on the opt-out path. The
    // entry never existed — querying Firestore would just produce
    // noise.
    verify(registry, never()).lookup(anyString());
    verify(registry, never()).delete(anyString());

    // Sibling updates are inherently skipped (no entries to update),
    // so updateMessage must not fire.
    verify(slack, never()).updateMessage(anyString(), anyString(), anyList(), anyString());

    // Beneficiary still gets a confirmation DM — that's independent
    // of how the original notification was delivered.
    verify(slack).lookupUserByEmail(eq("alice@example.com"));
    verify(slack).postDirectMessage(anyString(), anyList(), anyString());
  }

  // ---------------------------------------------------------------------
  // Options — fan-out concurrency cap (wavemm fork P1-4)
  // ---------------------------------------------------------------------

  @Test
  public void options_singleArgConstructor_appliesDefaultFanOutCap() {
    var opts = new SlackProposalHandler.Options(ZoneId.of("UTC"));
    assertEquals(
      SlackProposalHandler.Options.DEFAULT_MAX_CONCURRENT_FAN_OUT,
      opts.maxConcurrentFanOut(),
      "single-arg ctor must default to DEFAULT_MAX_CONCURRENT_FAN_OUT "
        + "so existing call sites get a sane cap without code change");
  }

  @Test
  public void options_rejectsNonPositiveFanOutCap() {
    // A 0 or negative cap would cause the Semaphore to deadlock the
    // first acquire — fail fast at construction time instead.
    assertThrows(IllegalArgumentException.class,
      () -> new SlackProposalHandler.Options(ZoneId.of("UTC"), 0));
    assertThrows(IllegalArgumentException.class,
      () -> new SlackProposalHandler.Options(ZoneId.of("UTC"), -1));
  }

  /**
   * P1-4 regression: a policy ACL granting APPROVE_OTHERS to a 50+
   * member group used to launch one parallel Slack call chain per
   * member, capable of saturating Slack's chat.postMessage Tier 4
   * quota. With the Semaphore in {@link SlackProposalHandler}, in-flight
   * call chains are bounded — verify that with 30 reviewers the call
   * still completes (no deadlock) and every reviewer ultimately gets
   * a DM (no permits leak).
   */
  @Test
  public void onOperationProposed_fanOutHonoursConcurrencyCap() throws Exception {
    var slack = slackClientHappyPath();
    var registry = registryHappyPath();
    var handler = newHandler(slack, registry, groupResolverPassthrough());

    Set<IamPrincipalId> reviewers = new java.util.HashSet<>();
    for (int i = 0; i < 30; i++) {
      reviewers.add(new EndUserId("reviewer-" + i + "@example.com"));
    }

    // Call into onOperationProposed directly to mirror the existing
    // tests in this class (the parent's `propose()` exercises the
    // tokenSigner mock too, which would return null and NPE — out of
    // scope here).
    handler.onOperationProposed(
      operationFor(ALICE),
      proposalFor(ALICE, reviewers),
      tokenFor(reviewers),
      ACTION_URI);

    // Every reviewer must receive exactly one DM — proves the
    // Semaphore-gated fan-out doesn't drop or duplicate work and
    // that permits are returned even on the happy path.
    verify(slack, times(30))
      .postDirectMessage(anyString(), anyList(), anyString());

    var entriesCaptor = ArgumentCaptor.forClass(List.class);
    verify(registry).record(anyString(), entriesCaptor.capture(), any());
    assertEquals(30, entriesCaptor.getValue().size(),
      "registry must record one entry per reviewer regardless of "
        + "fan-out concurrency cap");
  }
}
