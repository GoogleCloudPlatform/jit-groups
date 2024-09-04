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

import com.google.solutions.jitaccess.apis.IamRole;
import com.google.solutions.jitaccess.apis.ProjectId;
import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.catalog.*;
import com.google.solutions.jitaccess.catalog.auth.IamPrincipalId;
import com.google.solutions.jitaccess.catalog.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.auth.Principal;
import com.google.solutions.jitaccess.catalog.auth.EndUserId;
import com.google.solutions.jitaccess.catalog.policy.*;
import com.google.solutions.jitaccess.web.EventIds;
import com.google.solutions.jitaccess.web.proposal.ProposalHandler;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TestProposalResource {
  private static final EndUserId SAMPLE_USER = new EndUserId("user@example.com");
  private static final JitGroupId SAMPLE_JITGROUP_ID =  new JitGroupId("env", "sys", "group-1");

  private static Catalog createCatalog(JitGroupPolicy group) {
    return new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(group.system().environment()));
  }

  private static ProposalHandler createProposalHandler(
    JitGroupId group,
    EndUserId proposingUser,
    Set<IamPrincipalId> recipients
  ) throws IOException, AccessException {

    var proposalHandler = Mockito.mock(ProposalHandler.class);

    var proposal = Mockito.mock(Proposal.class);
    when(proposal.user())
      .thenReturn(proposingUser);
    when(proposal.group())
      .thenReturn(group);
    when(proposal.expiry())
      .thenReturn(Instant.MAX);
    when(proposal.recipients())
      .thenReturn(recipients);
    when(proposalHandler.accept(anyString()))
      .thenReturn(proposal);

    return proposalHandler;
  }

  //---------------------------------------------------------------------------
  // get.
  //---------------------------------------------------------------------------

  @Test
  public void get_whenTokenInvalid() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = Mockito.mock(ProposalHandler.class);

    when(resource.proposalHandler.accept(anyString()))
        .thenThrow(new AccessDeniedException("mock"));

    assertThrows(
      AccessDeniedException.class,
      () -> resource.get("env", "token"));
  }

  @Test
  public void get_whenTokenDoesNotMatchEnvironment() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = Mockito.mock(ProposalHandler.class);

    var proposal = Mockito.mock(Proposal.class);
    when(proposal.group())
      .thenReturn(SAMPLE_JITGROUP_ID);
    when(resource.proposalHandler.accept(anyString()))
      .thenReturn(proposal);

    assertThrows(
      ForbiddenException.class,
      () -> resource.get("wrong", "token"));
  }

  @Test
  public void get_whenGroupNotFound() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = Mockito.mock(ProposalHandler.class);

    var proposal = Mockito.mock(Proposal.class);
    when(proposal.group())
      .thenReturn(SAMPLE_JITGROUP_ID);
    when(resource.proposalHandler.accept(anyString()))
      .thenReturn(proposal);

    assertThrows(
      AccessDeniedException.class,
      () -> resource.get(SAMPLE_JITGROUP_ID.environment(), "token"));

    verify(resource.logger, times(1)).warn(
      eq(EventIds.API_APPROVE_JOIN),
      any(Exception.class));
  }

  @Test
  public void get_whenUserNotAllowedToApprove() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(),
      List.of(
        new IamRoleBinding(new ProjectId("project-1"), new IamRole("roles/role-1")),
        new IamRoleBinding(new ProjectId("project-1"), new IamRole("roles/role-1"), "description", null)));

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = createProposalHandler(
      group.id(),
      SAMPLE_USER,
      Set.of(SAMPLE_USER));

    var proposalInfo = resource.get(group.id().environment(), "token");
    assertEquals(ProposalResource.ApprovalStatusInfo.APPROVAL_DISALLOWED, proposalInfo.approval().status());

    var groupInfo = proposalInfo.approval().group();
    assertEquals(group.name(), groupInfo.name());
    assertEquals(group.description(), groupInfo.description());
    assertEquals(2, groupInfo.privileges().size());
    assertEquals("roles/role-1 on projects/project-1", groupInfo.privileges().get(0).description());
    assertEquals("description", groupInfo.privileges().get(1).description());
    assertEquals(group.system().name(), groupInfo.system().name());
    assertEquals(group.system().description(), groupInfo.system().description());
    assertEquals(group.system().environment().name(), groupInfo.environment().name());
    assertEquals(group.system().environment().description(), groupInfo.environment().description());
    assertNull(groupInfo.system().environment());
    assertNull(groupInfo.system().groups());
  }

  @Test
  public void get_whenUserAllowedToApprove() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, -1)
        .build(),
      Map.of(),
      List.of(
        new IamRoleBinding(new ProjectId("project-1"), new IamRole("roles/role-1")),
        new IamRoleBinding(new ProjectId("project-1"), new IamRole("roles/role-1"), "description", null)));

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = createProposalHandler(
      group.id(),
      new EndUserId("other@example.com"),
      Set.of(SAMPLE_USER));

    var proposalInfo = resource.get(group.id().environment(), "token");
    assertEquals(ProposalResource.ApprovalStatusInfo.APPROVAL_ALLOWED, proposalInfo.approval().status());

    var groupInfo = proposalInfo.approval().group();
    assertEquals(group.name(), groupInfo.name());
    assertEquals(group.description(), groupInfo.description());
    assertEquals(2, groupInfo.privileges().size());
    assertEquals("roles/role-1 on projects/project-1", groupInfo.privileges().get(0).description());
    assertEquals("description", groupInfo.privileges().get(1).description());
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
  public void post_whenTokenInvalid() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = Mockito.mock(ProposalHandler.class);

    when(resource.proposalHandler.accept(anyString()))
      .thenThrow(new AccessDeniedException("mock"));

    assertThrows(
      AccessDeniedException.class,
      () -> resource.post("env", "token", new MultivaluedHashMap<>()));
  }

  @Test
  public void post_whenGroupNotFound() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = createProposalHandler(
      group.id(),
      SAMPLE_USER,
      Set.of(SAMPLE_USER));

    assertThrows(
      AccessDeniedException.class,
      () -> resource.post(
        group.id().environment(),
        "token",
        new MultivaluedHashMap<>()));
  }

  @Test
  public void post_whenGroupNotAllowed() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .deny(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of());

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = createProposalHandler(
      group.id(),
      SAMPLE_USER,
      Set.of(SAMPLE_USER));

    assertThrows(
      AccessDeniedException.class,
      () -> resource.post(
        group.id().environment(),
        "token",
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

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = createProposalHandler(
      group.id(),
      SAMPLE_USER,
      Set.of(SAMPLE_USER));

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.post(
        group.id().environment(),
        "token",
        new MultivaluedHashMap<>()));
  }

  @Test
  public void post_whenRequiredInputMissingInProposal() throws Exception {
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

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = createProposalHandler(
      group.id(),
      new EndUserId("other@example.com"),
      Set.of(SAMPLE_USER));

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.post(
        group.id().environment(),
        "token",
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
        Policy.ConstraintClass.APPROVE,
        List.of(new CelConstraint(
          "c-1",
          "",
          List.of(new CelConstraint.BooleanVariable("var1", "")),
          "input.var1==true"))
      ));

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = createProposalHandler(
      group.id(),
      new EndUserId("other@example.com"),
      Set.of(SAMPLE_USER));

    var input = new MultivaluedHashMap<String, String>();
    input.putSingle("var1", "False");

    assertThrows(
      PolicyAnalysis.ConstraintUnsatisfiedException.class,
      () -> resource.post(
        group.id().environment(),
        "token",
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
        Policy.ConstraintClass.APPROVE,
        List.of(new CelConstraint(
          "c-1",
          "",
          List.of(new CelConstraint.BooleanVariable("var1", "")),
          "invalid CEL expression"))
      ));

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = createProposalHandler(
      group.id(),
      new EndUserId("other@example.com"),
      Set.of(SAMPLE_USER));

    var input = new MultivaluedHashMap<String, String>();
    input.putSingle("var1", "False");

    var exception = assertThrows(
      AccessDeniedException.class,
      () -> resource.post(
        group.id().environment(),
        "token",
        input));
    assertInstanceOf(PolicyAnalysis.ConstraintFailedException.class, exception.getCause());
  }

  @Test
  public void post_whenApprovalAllowed() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.APPROVE_OTHERS.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.JOIN, List.of(new ExpiryConstraint(Duration.ofMinutes(1)))));

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = createProposalHandler(
      group.id(),
      new EndUserId("other@example.com"),
      Set.of(SAMPLE_USER));

    var proposalInfo = resource.post(
      group.id().environment(),
      "token",
      new MultivaluedHashMap<>());

    verify(resource.auditTrail, times(1)).joinExecuted(
      any(JitGroupContext.ApprovalOperation.class),
      any(Principal.class));

    assertEquals(ProposalResource.ApprovalStatusInfo.APPROVAL_COMPLETED, proposalInfo.approval().status());
    assertEquals(0, proposalInfo.approval().satisfiedConstraints().size());
    assertEquals(0, proposalInfo.approval().unsatisfiedConstraints().size());
  }

  @Test
  public void post_whenApprovalNotAllowed() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.JOIN, List.of(new ExpiryConstraint(Duration.ofMinutes(1)))));

    var resource = new ProposalResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.auditTrail = Mockito.mock(OperationAuditTrail.class);
    resource.catalog = createCatalog(group);
    resource.proposalHandler = createProposalHandler(
      group.id(),
      new EndUserId("other@example.com"),
      Set.of(SAMPLE_USER));

    assertThrows(
      AccessDeniedException.class,
      () -> resource.post(
        group.id().environment(),
        "token",
        new MultivaluedHashMap<>()));
  }
}
