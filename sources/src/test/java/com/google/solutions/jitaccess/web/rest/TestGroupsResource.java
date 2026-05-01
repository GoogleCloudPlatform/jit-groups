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

package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.apis.OrganizationId;
import com.google.solutions.jitaccess.apis.ProjectId;
import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.apis.clients.GroupKey;
import com.google.solutions.jitaccess.auth.*;
import com.google.solutions.jitaccess.catalog.*;
import com.google.solutions.jitaccess.catalog.policy.*;
import com.google.solutions.jitaccess.web.Consoles;
import com.google.solutions.jitaccess.web.EventIds;
import com.google.solutions.jitaccess.web.OperationAuditTrail;
import com.google.solutions.jitaccess.web.proposal.ProposalHandler;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


public class TestGroupsResource {
  private static final EndUserId SAMPLE_USER = new EndUserId("user@example.com");
  private static final EndUserId SAMPLE_APPROVING_USER = new EndUserId("approver@example.com");

  private static Catalog createCatalog(JitGroupPolicy group, Subject subject) {
    return new Catalog(
      subject,
      Environments.create(group.system().environment()));
  }

  private static Catalog createCatalog(JitGroupPolicy group) {
    return createCatalog(group, Subjects.create(SAMPLE_USER));
  }

  //---------------------------------------------------------------------------
  // get.
  //---------------------------------------------------------------------------

  @Test
  public void get_whenGroupIdInvalid() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.get(group.id().environment(), null, group.name()));
    assertThrows(
      IllegalArgumentException.class,
      () -> resource.get(group.id().environment(), group.id().system(), null));
  }

  @Test
  public void get_whenGroupNotFound() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      AccessDeniedException.class,
      () -> resource.get(
        group.id().environment(),
        group.id().system(),
        "notfound"));

    verify(resource.logger, times(1)).warn(
      eq(EventIds.API_VIEW_GROUPS),
      any(Exception.class));
  }

  @Test
  public void get() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, -1)
        .build(),
      Map.of(),
      List.of(
        new IamRoleBinding(new ProjectId("project-1"), new IamRole("roles/role-1")),
        new IamRoleBinding(new ProjectId("project-1"), new IamRole("roles/role-1"), "description", "condition")));

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);

    var groupInfo = resource.get(group.id().environment(), group.id().system(), group.id().name());
    assertEquals(GroupsResource.JoinStatusInfo.JOIN_ALLOWED_WITHOUT_APPROVAL, groupInfo.join().status());
    assertEquals(group.name(), groupInfo.name());
    assertEquals(group.description(), groupInfo.description());

    assertEquals(2, groupInfo.privileges().size());
    assertEquals("roles/role-1", groupInfo.privileges().get(0).description());
    assertEquals("projects/project-1", groupInfo.privileges().get(0).resourceName());
    assertFalse(groupInfo.privileges().get(0).hasResourceCondition());

    assertEquals("description", groupInfo.privileges().get(1).description());
    assertEquals("projects/project-1", groupInfo.privileges().get(1).resourceName());
    assertTrue(groupInfo.privileges().get(1).hasResourceCondition());

    assertEquals(group.system().name(), groupInfo.system().name());
    assertEquals(group.system().description(), groupInfo.system().description());
    assertEquals(group.system().environment().name(), groupInfo.environment().name());
    assertEquals(group.system().environment().description(), groupInfo.environment().description());
    assertNull(groupInfo.system().environment());
    assertNull(groupInfo.system().groups());
  }

  //---------------------------------------------------------------------------
  // post.
  //---------------------------------------------------------------------------

  @Test
  public void post_whenGroupIdInvalid() {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        null,
        new MultivaluedHashMap<>()));
    assertThrows(
      IllegalArgumentException.class,
      () -> resource.post(
        group.id().environment(),
        null,
        group.id().name(),
        new MultivaluedHashMap<>()));
  }

  @Test
  public void post_whenGroupNotFound() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      AccessDeniedException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        "notfound", new MultivaluedHashMap<>()));
  }

  @Test
  public void post_whenGroupNotAllowed() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .deny(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of());

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      AccessDeniedException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        group.id().name(),
        new MultivaluedHashMap<>()));
  }

  @Test
  public void post_whenRequiredInputMissing() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, -1)
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(new CelConstraint(
          "c-1",
          "",
          List.of(new CelConstraint.BooleanVariable("var1", "")),
          "input.var1==true"))
      ));

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        group.id().name(),
        new MultivaluedHashMap<>()));
  }

  @Test
  public void post_whenConstraintUnsatisfied() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, -1)
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(new CelConstraint(
          "c-1",
          "",
          List.of(new CelConstraint.BooleanVariable("var1", "")),
          "input.var1==true"))
      ));

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);

    var input = new MultivaluedHashMap<String, String>();
    input.putSingle("var1", "False");

    assertThrows(
      PolicyAnalysis.ConstraintUnsatisfiedException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        group.id().name(),
        input));
  }

  @Test
  public void post_whenConstraintFailed() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, -1)
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(new CelConstraint(
          "c-1",
          "",
          List.of(new CelConstraint.BooleanVariable("var1", "")),
          "invalid CEL expression"))
      ));

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);

    var input = new MultivaluedHashMap<String, String>();
    input.putSingle("var1", "False");

    var exception = assertThrows(
      AccessDeniedException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        group.id().name(),
        input));
    assertInstanceOf(PolicyAnalysis.ConstraintFailedException.class, exception.getCause());
  }

  @Test
  public void post_whenExpiryConstraintMissing() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .allow(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask())
        .build(),
      Map.of());

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      UnsupportedOperationException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        group.id().name(),
        new MultivaluedHashMap<>()));
  }

  @Test
  public void post_whenJoinAllowedWithoutApproval() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .allow(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.JOIN, List.of(new ExpiryConstraint(Duration.ofMinutes(1)))));

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = Mockito.mock(ProposalHandler.class);

    var groupInfo = resource.post(
      group.id().environment(),
      group.id().system(),
      group.id().name(),
      new MultivaluedHashMap<>());

    verify(resource.proposalHandler, never()).propose(any(), any(), any());
    verify(resource.auditTrail, times(1)).joinExecuted(
      any(JitGroupContext.JoinOperation.class),
      any(Principal.class));

    assertEquals(GroupsResource.JoinStatusInfo.JOIN_COMPLETED, groupInfo.join().status());
    assertTrue(groupInfo.join().membership().active());
    assertEquals(0, groupInfo.join().satisfiedConstraints().size());
    assertEquals(0, groupInfo.join().unsatisfiedConstraints().size());
  }

  @Test
  public void post_whenJoinAllowedWithApproval() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.JOIN, List.of(new ExpiryConstraint(Duration.ofMinutes(1)))));

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = Mockito.mock(ProposalHandler.class);

    when(resource.proposalHandler.propose(any(), any(), any()))
      .thenReturn(new ProposalHandler.ProposalToken(
        "token",
        Set.of(SAMPLE_APPROVING_USER),
        Instant.MAX));

    var groupInfo = resource.post(
      group.id().environment(),
      group.id().system(),
      group.id().name(),
      new MultivaluedHashMap<>());

    verify(resource.proposalHandler, times(1)).propose(any(), any(), any());
    // Wavemm fork P1-8/P2-10: 3-arg overload carries the
    // notifyReviewers boolean so the audit log can split copy-link
    // from the regular DM-everyone flow. Unchecked POST → defaults
    // to true, matching upstream semantics.
    verify(resource.auditTrail, times(1)).joinProposed(
      any(JitGroupContext.JoinOperation.class),
      any(ProposalHandler.ProposalToken.class),
      eq(true));

    assertEquals(GroupsResource.JoinStatusInfo.JOIN_PROPOSED, groupInfo.join().status());
    assertFalse(groupInfo.join().membership().active());
    assertEquals(0, groupInfo.join().satisfiedConstraints().size());
    assertEquals(0, groupInfo.join().unsatisfiedConstraints().size());
  }

  // -------------------------------------------------------------------------
  // Wavemm fork: picker-UX tests (Phase 2b/3).
  // -------------------------------------------------------------------------

  /**
   * P0-1 regression: a hostile requester submits {@code selectedReviewers}
   * naming an email that's NOT in the qualified-peer set. The validation
   * step in {@code post} must reject before {@code propose} is called.
   */
  @Test
  public void post_whenSelectedReviewersIncludeUnauthorisedEmail_rejectsWith403()
    throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .allow(SAMPLE_APPROVING_USER, PolicyPermission.APPROVE_OTHERS.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.JOIN, List.of(new ExpiryConstraint(Duration.ofMinutes(1)))));

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = Mockito.mock(ProposalHandler.class);
    resource.subject = Subjects.create(SAMPLE_USER);
    resource.executor = Runnable::run;
    resource.groupsClient = Mockito.mock(
      com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient.class);
    when(resource.groupsClient.listMembershipsByUser(any()))
      .thenReturn(java.util.List.of());

    var inputs = new MultivaluedHashMap<String, String>();
    inputs.put(GroupsResource.FIELD_SELECTED_REVIEWERS,
      List.of("attacker@external.com"));

    var thrown = assertThrows(AccessDeniedException.class, () ->
      resource.post(
        group.id().environment(),
        group.id().system(),
        group.id().name(),
        inputs));
    assertTrue(thrown.getMessage().contains("attacker@external.com"));

    // Critical: never reach the proposal handler when validation fails.
    verify(resource.proposalHandler, never()).propose(any(), any(), any());
  }

  @Test
  public void parseSelectedReviewers_capsAtFifty() {
    // Above the cap → BadRequestException; private-method coverage via
    // the public POST since parseSelectedReviewers is package-private.
    var raw = new java.util.ArrayList<String>();
    for (int i = 0; i < 51; i++) raw.add("user-" + i + "@example.com");

    var inputs = new MultivaluedHashMap<String, String>();
    inputs.put(GroupsResource.FIELD_SELECTED_REVIEWERS, raw);

    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.JOIN, List.of(new ExpiryConstraint(Duration.ofMinutes(1)))));
    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);

    assertThrows(jakarta.ws.rs.BadRequestException.class, () ->
      resource.post(
        group.id().environment(),
        group.id().system(),
        group.id().name(),
        inputs));
  }

  /**
   * Wavemm fork P2-9: malformed email entries in the picker submission
   * (e.g. "@@@@", "user@", "spaces in@email.com") used to slip through
   * the old "contains @" check and become {@code EndUserId} instances
   * that propagated to audit logs and downstream ACL diffs. The
   * stricter regex-backed parse rejects them at the REST boundary.
   */
  @ParameterizedTest
  @ValueSource(strings = {
    "@@@@",
    "user@",
    "@example.com",
    "no-at-sign",
    "spaces in@example.com",
    "user@host with spaces"
  })
  public void parseSelectedReviewers_rejectsMalformedEmails(String hostile) {
    var inputs = new MultivaluedHashMap<String, String>();
    inputs.put(GroupsResource.FIELD_SELECTED_REVIEWERS, List.of(hostile));

    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.JOIN, List.of(new ExpiryConstraint(Duration.ofMinutes(1)))));
    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);

    var ex = assertThrows(
      jakarta.ws.rs.BadRequestException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        group.id().name(),
        inputs));
    assertTrue(ex.getMessage().contains("selectedReviewers"),
      "rejection message must name the field; got: " + ex.getMessage());
  }

  @Test
  public void post_whenNotifyReviewersFalse_passesOptionToProposeAndReturnsApprovalUrl()
    throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.JOIN, List.of(new ExpiryConstraint(Duration.ofMinutes(1)))));

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(true);  // copy-link mode on
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = Mockito.mock(ProposalHandler.class);
    // copy-link mode reaches through buildActionUri.apply(...) to build
    // the approvalUrl in the response, so linkBuilder + uriInfo need
    // working stubs unlike the non-copy-link tests above.
    resource.linkBuilder = uriInfo -> jakarta.ws.rs.core.UriBuilder
      .fromUri("https://example.test/");
    resource.uriInfo = Mockito.mock(jakarta.ws.rs.core.UriInfo.class);

    when(resource.proposalHandler.propose(any(), any(), any()))
      .thenReturn(new ProposalHandler.ProposalToken(
        // TokenObfuscator.encode requires a JWT-shaped string ("ey..." +
        // dot-segments), so a literal "stub-jwt" trips its preconditions
        // when copy-link mode actually applies the buildActionUri.
        "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ0ZXN0In0.",
        Set.of(SAMPLE_APPROVING_USER), Instant.MAX));

    var inputs = new MultivaluedHashMap<String, String>();
    // Hidden-then-checkbox pattern: only the hidden's "false" present
    // ⇒ user unchecked the toggle ⇒ notifyReviewers must propagate
    // false to the handler.
    inputs.put(GroupsResource.FIELD_NOTIFY_REVIEWERS, List.of("false"));

    var groupInfo = resource.post(
      group.id().environment(),
      group.id().system(),
      group.id().name(),
      inputs);

    var captor = org.mockito.ArgumentCaptor.forClass(
      ProposalHandler.ProposeOptions.class);
    verify(resource.proposalHandler).propose(any(), any(), captor.capture());
    assertFalse(captor.getValue().notifyReviewers(),
      "ProposeOptions.notifyReviewers must reflect the form field");

    // Wavemm fork P1-8/P2-10: the audit-trail event must carry
    // notifyReviewers=false so the BigQuery sink can slice copy-link
    // out of the regular DM-everyone propose flow.
    verify(resource.auditTrail).joinProposed(
      any(JitGroupContext.JoinOperation.class),
      any(ProposalHandler.ProposalToken.class),
      eq(false));

    assertNotNull(groupInfo.join().approvalUrl(),
      "copy-link mode + JOIN_PROPOSED must surface the approval URL");
  }

  //---------------------------------------------------------------------------
  // getReviewers — rate limit (wavemm fork P1-4).
  //---------------------------------------------------------------------------

  /**
   * The reviewer picker calls into ReviewerCandidates which itself
   * does one listMembershipsByUser + a parallel listMemberships
   * fan-out. A polling client could therefore burn through the JIT
   * SA's Cloud Identity quota and DoS legitimate elevations. We
   * cap to {@link GroupsResource#REVIEWERS_RATE_PER_SECOND} per
   * second per authenticated user, with a burst allowance — verify
   * here that exhausting the burst and then asking for one more
   * permit fails (drives the 429 path in the resource method).
   */
  @Test
  public void getReviewers_rateLimitTripsOnPolling() {
    GroupsResource.clearReviewerRateLimiters();
    var limiter = GroupsResource.rateLimiterFor("ratelimit-test@example.com");

    // The Guava SmoothBursty limiter starts with one permit available;
    // additional permits are accrued at REVIEWERS_RATE_PER_SECOND. The
    // first acquire must succeed (covers the "single picker open"
    // case), and a tight loop right after must trip the limit (covers
    // the "polling client" case). Without sleeps in the test we don't
    // assert the *exact* burst capacity — only that the limiter
    // rejects sustained polling, which is the security-relevant
    // property.
    assertTrue(limiter.tryAcquire(),
      "first call after a quiet period must always succeed");

    boolean rejected = false;
    for (int i = 0; i < 200; i++) {
      if (!limiter.tryAcquire()) {
        rejected = true;
        break;
      }
    }
    assertTrue(rejected,
      "a tight 200-call loop must hit the rate limit at least once "
        + "— otherwise a polling frontend can exhaust Cloud Identity quota");
  }

  @Test
  public void getReviewers_rateLimitIsPerUser() {
    GroupsResource.clearReviewerRateLimiters();
    var alice = GroupsResource.rateLimiterFor("alice@example.com");
    var bob = GroupsResource.rateLimiterFor("bob@example.com");

    // Drain alice's permits
    while (alice.tryAcquire()) { /* burn */ }

    assertTrue(bob.tryAcquire(),
      "bob's bucket must be independent of alice's — otherwise one "
        + "noisy user can DoS every other user's picker");
  }

  //---------------------------------------------------------------------------
  // linkTo.
  //---------------------------------------------------------------------------

  @Test
  public void linkTo_whenGroupIdInvalid() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.linkTo(group.id().environment(), null, group.name(), "cloud-console"));
    assertThrows(
      IllegalArgumentException.class,
      () -> resource.linkTo(group.id().environment(), group.id().system(), null, "cloud-console"));
  }

  @Test
  public void linkTo_whenGroupNotFound() throws Exception {
    var groupId = new JitGroupId("env-1", "system-1", "group-1");

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = Mockito.mock(Catalog.class);
    when(resource.catalog.group(groupId))
      .thenReturn(Optional.empty());

    assertThrows(
      AccessDeniedException.class,
      () -> resource.linkTo(
        groupId.environment(),
        groupId.system(),
        groupId.name(),
        "cloud-console"));
  }

  @Test
  public void linkTo_whenGroupNotCreatedYet() throws Exception {
    var groupId = new JitGroupId("env-1", "system-1", "group-1");
    var group = Mockito.mock(JitGroupContext.class);
    when(group.cloudIdentityGroupKey())
      .thenReturn(Optional.empty());

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = Mockito.mock(Catalog.class);
    when(resource.catalog.group(groupId))
      .thenReturn(Optional.of(group));

    assertThrows(
      NotFoundException.class,
      () -> resource.linkTo(
        groupId.environment(),
        groupId.system(),
        groupId.name(),
        "cloud-console"));
  }

  @Test
  public void linkTo_whenConsoleNotFound() throws Exception {
    var groupId = new JitGroupId("env-1", "system-1", "group-1");
    var group = Mockito.mock(JitGroupContext.class);
    when(group.cloudIdentityGroupKey())
      .thenReturn(Optional.of(new GroupKey("abc")));

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.consoles = new Consoles(new OrganizationId("123"));
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = Mockito.mock(Catalog.class);
    when(resource.catalog.group(groupId))
      .thenReturn(Optional.of(group));

    assertThrows(
      NotFoundException.class,
      () -> resource.linkTo(
        groupId.environment(),
        groupId.system(),
        groupId.name(),
        "invalid-console"));
  }

  @ParameterizedTest
  @ValueSource(
    strings = {"cloud-console", "admin-console", "groups-console", "cloud-logging"}
  )
  public void linkTo_whenGroupCreated_thenReturnsLink(String console) throws Exception {
    var groupId = new JitGroupId("env-1", "system-1", "group-1");
    var group = Mockito.mock(JitGroupContext.class);
    when(group.cloudIdentityGroupKey())
      .thenReturn(Optional.of(new GroupKey("abc")));
    when(group.cloudIdentityGroupId())
      .thenReturn(new GroupId("group@example.com"));

    var resource = new GroupsResource();
    resource.options = new GroupsResource.Options(false);
    resource.consoles = new Consoles(new OrganizationId("123"));
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = Mockito.mock(Catalog.class);
    when(resource.catalog.group(groupId))
      .thenReturn(Optional.of(group));

    var link = resource.linkTo(
      groupId.environment(),
      groupId.system(),
      groupId.name(),
      console);

    assertEquals("ExternalLinkInfo", link.type());
    assertNotNull(link.location().target());
  }
}
