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

import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.auth.*;
import com.google.solutions.jitaccess.catalog.policy.*;
import com.google.solutions.jitaccess.common.cel.EvaluationException;
import com.google.solutions.jitaccess.common.cel.Expression;
import com.google.solutions.jitaccess.catalog.provisioning.Provisioner;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TestJitGroupContext {
  private static final EndUserId SAMPLE_USER = new EndUserId("user@example.com");
  private static final EndUserId SAMPLE_APPROVER_1 = new EndUserId("approver-1@example.com");
  private static final EndUserId SAMPLE_APPROVER_2 = new EndUserId("approver-2@example.com");
  private static final GroupId SAMPLE_APPROVER_GROUP = new GroupId("approvers@example.com");

  private static EnvironmentPolicy createEnvironmentPolicy() {
    return new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new Policy.Metadata("test", Instant.EPOCH));
  }

  private static Constraint createFailingConstraint(@NotNull String name) throws EvaluationException {
    var check = Mockito.mock(Constraint.Check.class);
    when(check.evaluate())
      .thenThrow(new IllegalStateException("Mock"));
    when(check.addContext(anyString()))
      .thenReturn(Mockito.mock(Expression.Context.class));

    var constraint = Mockito.mock(Constraint.class);
    when(constraint.name())
      .thenReturn(name);
    when(constraint.createCheck())
      .thenReturn(check);

    when(check.constraint())
      .thenReturn(constraint);

    return constraint;
  }

  private static Constraint createUnsatisfiedConstraint(@NotNull String name) {
    return new CelConstraint(name, name, List.of(), "false");
  }

  private static Constraint createSatisfiedConstraint(@NotNull String name) {
    return new CelConstraint(name, name, List.of(), "true");
  }

  private record MockProposal(
    @NotNull EndUserId user,
    @NotNull JitGroupId group,
    @NotNull Set<IamPrincipalId> recipients,
    @NotNull Instant expiry,
    @NotNull Map<String, String> input
  ) implements Proposal {
    @Override
    public void onCompleted(
      @NotNull JitGroupContext.ApprovalOperation op
    ) {
    }
  }

  // -------------------------------------------------------------------------
  // join/dryRun.
  // -------------------------------------------------------------------------

  @Test
  public void join_dryRun_whenNotAllowed() {
    var subject = Subjects.create(SAMPLE_USER);
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.VIEW.toMask()) // missing JOIN
        .build());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var group = new JitGroupContext(
      groupPolicy,
      subject,
      Mockito.mock(Provisioner.class));

    var joinOp = group.join();
    assertTrue(joinOp.requiresApproval());
    assertFalse(joinOp
      .dryRun()
      .isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
  }

  @Test
  public void join_dryRun_whenAllowedWithoutApprovalButConstraintFails() throws Exception {
    var subject = Subjects.create(SAMPLE_USER);
    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .allow(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN, List.of(createFailingConstraint("join")),
        Policy.ConstraintClass.APPROVE, List.of(createFailingConstraint("approve"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(group));

    var joinOp = new JitGroupContext(
      group,
      subject,
      Mockito.mock(Provisioner.class)).join();
    assertFalse(joinOp.requiresApproval());

    var analysis = joinOp.dryRun();
    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertFalse(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
    assertEquals(0, analysis.satisfiedConstraints().size());
    assertEquals(2, analysis.unsatisfiedConstraints().size()); // JOIN + APPROVE
    assertEquals(2, analysis.failedConstraints().size());      // JOIN + APPROVE
  }

  @Test
  public void join_dryRun_whenAllowedWithApprovalButConstraintFails() throws Exception {
    var subject = Subjects.create(SAMPLE_USER);
    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN, List.of(createFailingConstraint("join")),
        Policy.ConstraintClass.APPROVE, List.of(createFailingConstraint("approve"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(group));

    var joinOp = new JitGroupContext(
      group,
      subject,
      Mockito.mock(Provisioner.class)).join();
    assertTrue(joinOp.requiresApproval());

    var analysis = joinOp.dryRun();
    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertFalse(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
    assertEquals(0, analysis.satisfiedConstraints().size());
    assertEquals(1, analysis.unsatisfiedConstraints().size());
    assertEquals(1, analysis.failedConstraints().size());
  }

  @Test
  public void join_dryRun_whenAllowedWithApprovalButConstraintUnsatisfied() throws Exception {
    var subject = Subjects.create(SAMPLE_USER);
    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN, List.of(createUnsatisfiedConstraint("join"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(group));

    var joinOp = new JitGroupContext(
      group,
      subject,
      Mockito.mock(Provisioner.class)).join();
    assertTrue(joinOp.requiresApproval());

    var analysis = joinOp.dryRun();
    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertFalse(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
    assertEquals(0, analysis.satisfiedConstraints().size());
    assertEquals(1, analysis.unsatisfiedConstraints().size());
    assertEquals(0, analysis.failedConstraints().size());
  }

  @Test
  public void join_dryRun() {
    var subject = Subjects.create(SAMPLE_USER);
    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN, List.of(createSatisfiedConstraint("join"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(group));

    var joinOp = new JitGroupContext(
      group,
      subject,
      Mockito.mock(Provisioner.class)).join();
    assertTrue(joinOp.requiresApproval());

    var analysis = joinOp.dryRun();

    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
    assertEquals(1, analysis.satisfiedConstraints().size());
    assertEquals(0, analysis.unsatisfiedConstraints().size());
    assertEquals(0, analysis.failedConstraints().size());
  }

  // -------------------------------------------------------------------------
  // join/execute.
  // -------------------------------------------------------------------------

  @Test
  public void join_execute_whenNotAllowed() {
    var subject = Subjects.create(SAMPLE_USER);
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.VIEW.toMask()) // missing JOIN
        .build());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var group = new JitGroupContext(
      groupPolicy,
      subject,
      Mockito.mock(Provisioner.class));

    var joinOp = group.join();

    assertThrows(
      AccessDeniedException.class,
      () -> joinOp.execute());
  }

  @Test
  public void join_execute_whenAllowedWithoutApprovalButConstraintFails() throws Exception {
    var subject = Subjects.create(SAMPLE_USER);
    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .allow(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN, List.of(createFailingConstraint("join")),
        Policy.ConstraintClass.APPROVE, List.of(createFailingConstraint("approve"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(group));

    var joinOp = new JitGroupContext(
      group,
      subject,
      Mockito.mock(Provisioner.class)).join();

    assertThrows(
      PolicyAnalysis.ConstraintFailedException.class,
      () -> joinOp.execute());
  }

  @Test
  public void join_execute_whenExpiryConstraintMissing() {
    var subject = Subjects.create(SAMPLE_USER);
    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .allow(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN, List.of(),
        Policy.ConstraintClass.APPROVE, List.of()),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(group));

    var joinOp = new JitGroupContext(
      group,
      subject,
      Mockito.mock(Provisioner.class)).join();

    assertThrows(
      UnsupportedOperationException.class,
      () -> joinOp.execute());
  }

  @Test
  public void join_execute_whenExpiryConstraintInherited() throws Exception {
    var subject = Subjects.create(SAMPLE_USER);
    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .allow(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask())
        .build());

    var system = new SystemPolicy(
      "system-1",
      "System",
      null,
      Map.of(
        Policy.ConstraintClass.JOIN, List.of(new ExpiryConstraint(Duration.ofMinutes(1))),
        Policy.ConstraintClass.APPROVE, List.of()));

    createEnvironmentPolicy()
      .add(system.add(group));

    var provisioner = Mockito.mock(Provisioner.class);
    var joinOp = new JitGroupContext(group, subject, provisioner).join();

    var principal = joinOp.execute();
    assertEquals(group.id(), principal.id());
    assertNotNull(principal.expiry());
    assertTrue(principal.isValid());
    assertTrue(principal.expiry().isAfter(Instant.now()));
    assertTrue(principal.expiry().isBefore(Instant.now().plusSeconds(61)));

    verify(provisioner, times(1)).provisionMembership(
      eq(group),
      eq(SAMPLE_USER),
      any());
  }

  @Test
  public void join_execute() throws Exception {
    var subject = Subjects.create(SAMPLE_USER);
    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .allow(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN, List.of(new ExpiryConstraint(Duration.ofMinutes(1))),
        Policy.ConstraintClass.APPROVE, List.of()),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(group));

    var provisioner = Mockito.mock(Provisioner.class);
    var joinOp = new JitGroupContext(group, subject, provisioner).join();

    var principal = joinOp.execute();
    assertEquals(group.id(), principal.id());
    assertNotNull(principal.expiry());
    assertTrue(principal.isValid());
    assertTrue(principal.expiry().isAfter(Instant.now()));
    assertTrue(principal.expiry().isBefore(Instant.now().plusSeconds(61)));

    verify(provisioner, times(1)).provisionMembership(
      eq(group),
      eq(SAMPLE_USER),
      any());
  }

  // -------------------------------------------------------------------------
  // join/propose.
  // -------------------------------------------------------------------------

  @Test
  public void join_propose_whenNoApprovalRequired() throws Exception {
    var subject = Mockito.mock(Subject.class);
    when(subject.user())
      .thenReturn(SAMPLE_USER);
    when(subject.principals())
      .thenReturn(Set.of(new Principal(SAMPLE_USER)));

    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .allow(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask())
        .build(),
      Map.of(),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(group));

    var joinOp = new JitGroupContext(
      group,
      subject,
      Mockito.mock(Provisioner.class)).join();
    assertFalse(joinOp.requiresApproval());

    assertThrows(
      IllegalStateException.class,
      () -> joinOp.propose(Instant.now()));
  }

  @Test
  public void join_propose_whenNoApproversAvailable() throws Exception {
    var subject = Mockito.mock(Subject.class);
    when(subject.user())
      .thenReturn(SAMPLE_USER);
    when(subject.principals())
      .thenReturn(Set.of(new Principal(SAMPLE_USER)));

    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(group));

    var joinOp = new JitGroupContext(
      group,
      subject,
      Mockito.mock(Provisioner.class)).join();
    assertTrue(joinOp.requiresApproval());

    assertThrows(
      AccessDeniedException.class,
      () -> joinOp.propose(Instant.now()));
  }

  @Test
  public void join_propose_whenAllowedWithApprovalButConstraintUnsatisfied() throws Exception {
    var subject = Mockito.mock(Subject.class);
    when(subject.user())
      .thenReturn(SAMPLE_USER);
    when(subject.principals())
      .thenReturn(Set.of(new Principal(SAMPLE_USER)));

    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .allow(SAMPLE_USER, PolicyPermission.APPROVE_OTHERS.toMask())
        .allow(SAMPLE_APPROVER_1, PolicyPermission.APPROVE_OTHERS.toMask())
        .allow(SAMPLE_APPROVER_2, PolicyPermission.APPROVE_OTHERS.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(
          new CelConstraint(
            "join-constraint",
            "join-constraint",
            List.of(),
            "false"))), // Unsatisfied
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(group));

    var expiry = Instant.now().plusSeconds(60);
    var join = new JitGroupContext(
      group,
      subject,
      Mockito.mock(Provisioner.class))
      .join();

    assertThrows(
      PolicyAnalysis.ConstraintUnsatisfiedException.class,
      () -> join.propose(expiry));
  }

  @Test
  public void join_propose() throws Exception {
    var subject = Subjects.create(SAMPLE_USER);
    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .allow(SAMPLE_USER, PolicyPermission.APPROVE_OTHERS.toMask())
        .allow(SAMPLE_APPROVER_1, PolicyPermission.APPROVE_OTHERS.toMask())
        .allow(SAMPLE_APPROVER_2, PolicyPermission.APPROVE_OTHERS.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(
          new CelConstraint(
            "join-constraint",
            "join-constraint",
            List.of(
              new CelConstraint.BooleanVariable("bool", "bool"),
              new CelConstraint.StringVariable("string", "String", 0, 10),
              new CelConstraint.LongVariable("long", "long", 0L, 200L)),
            "true")), // Satisfied

        Policy.ConstraintClass.APPROVE,
        List.of(createUnsatisfiedConstraint("approve-constraint"))), // Unsatisfied -> does not matter here
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(group));

    var expiry = Instant.now().plusSeconds(60);
    var join = new JitGroupContext(
      group,
      subject,
      Mockito.mock(Provisioner.class))
      .join();

    join.input().get(0).set("true");
    join.input().get(1).set("a string");
    join.input().get(2).set("123");

    var proposal = join.propose(expiry);

    assertEquals(group.id(), proposal.group());
    assertEquals(SAMPLE_USER, proposal.user());
    assertEquals(expiry, proposal.expiry());
    assertEquals(Set.of(SAMPLE_APPROVER_1, SAMPLE_APPROVER_2), proposal.recipients());

    assertEquals(3, proposal.input().size());
    assertEquals("true", proposal.input().get("bool"));
    assertEquals("a string", proposal.input().get("string"));
    assertEquals("123", proposal.input().get("long"));
  }

  // -------------------------------------------------------------------------
  // approve.
  // -------------------------------------------------------------------------


  @Test
  public void approve_whenProposalExpired() {
    var subject = Subjects.create(SAMPLE_APPROVER_1);
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1");

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var group = new JitGroupContext(
      groupPolicy,
      subject,
      Mockito.mock(Provisioner.class));

    var proposal = new MockProposal(
      SAMPLE_USER,
      group.policy().id(),
      Set.of(SAMPLE_APPROVER_1),
      Instant.now().minusSeconds(10),
      Map.of());

    assertThrows(
      IllegalArgumentException.class,
      () -> group.approve(proposal));
  }

  // -------------------------------------------------------------------------
  // approve/dryRun.
  // -------------------------------------------------------------------------

  @Test
  public void approve_dryRun_whenNotAllowed() {
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_APPROVER_1, PolicyPermission.VIEW.toMask()) // missing APPROVE_OTHERS
        .build());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var subject = Subjects.create(SAMPLE_APPROVER_1);
    var group = new JitGroupContext(
      groupPolicy,
      subject,
      Mockito.mock(Provisioner.class));

    var proposal = new MockProposal(
      SAMPLE_USER,
      group.policy().id(),
      Set.of(SAMPLE_APPROVER_1),
      Instant.now().plusSeconds(10),
      Map.of());

    var approveOp = group.approve(proposal);
    assertFalse(approveOp
      .dryRun()
      .isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
  }

  @Test
  public void approve_dryRun_whenSameUserAndSelfApprovalAllowed() {
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.APPROVE, List.of(createSatisfiedConstraint("approve"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var subject = Subjects.create(SAMPLE_USER);
    var group = new JitGroupContext(
      groupPolicy,
      subject,
      Mockito.mock(Provisioner.class));

    var proposal = new MockProposal(
      SAMPLE_USER,
      group.policy().id(),
      Set.of(SAMPLE_APPROVER_1),
      Instant.now().plusSeconds(10),
      Map.of());

    var approveOp = group.approve(proposal);
    var analysis = approveOp.dryRun();

    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
    assertEquals(1, analysis.satisfiedConstraints().size());
    assertEquals(0, analysis.unsatisfiedConstraints().size());
    assertEquals(0, analysis.failedConstraints().size());
  }

  @Test
  public void approve_dryRun_whenSameUserAndSelfApprovalNotAllowed() {
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.APPROVE, List.of(createSatisfiedConstraint("approve"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var subject = Subjects.create(SAMPLE_USER);
    var group = new JitGroupContext(
      groupPolicy,
      subject,
      Mockito.mock(Provisioner.class));

    var proposal = new MockProposal(
      SAMPLE_USER,
      group.policy().id(),
      Set.of(SAMPLE_APPROVER_1),
      Instant.now().plusSeconds(10),
      Map.of());

    var approveOp = group.approve(proposal);
    var analysis = approveOp.dryRun();

    assertFalse(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertFalse(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
    assertEquals(1, analysis.satisfiedConstraints().size());
    assertEquals(0, analysis.unsatisfiedConstraints().size());
    assertEquals(0, analysis.failedConstraints().size());
  }

  @Test
  public void approve_dryRun_whenAllowedViaGroupAndUserNotAmongRecipients() throws Exception {
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_APPROVER_GROUP, PolicyPermission.APPROVE_OTHERS.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.APPROVE, List.of(createFailingConstraint("failing"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var subject = Subjects.createWithPrincipalIds(
      SAMPLE_APPROVER_1,
      Set.of(SAMPLE_APPROVER_GROUP));
    var group = new JitGroupContext(
      groupPolicy,
      subject,
      Mockito.mock(Provisioner.class));

    var proposal = new MockProposal(
      SAMPLE_USER,
      group.policy().id(),
      Set.of(SAMPLE_APPROVER_GROUP),
      Instant.now().plusSeconds(10),
      Map.of());

    var approveOp = group.approve(proposal);
    var analysis = approveOp.dryRun();
    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertFalse(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
    assertEquals(0, analysis.satisfiedConstraints().size());
    assertEquals(1, analysis.unsatisfiedConstraints().size());
    assertEquals(1, analysis.failedConstraints().size());
  }

  @Test
  public void approve_dryRun_whenAllowedButConstraintFails() throws Exception {
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_APPROVER_1, PolicyPermission.APPROVE_OTHERS.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.APPROVE, List.of(createFailingConstraint("failing"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var subject = Subjects.create(SAMPLE_APPROVER_1);
    var group = new JitGroupContext(
      groupPolicy,
      subject,
      Mockito.mock(Provisioner.class));

    var proposal = new MockProposal(
      SAMPLE_USER,
      group.policy().id(),
      Set.of(SAMPLE_APPROVER_1),
      Instant.now().plusSeconds(10),
      Map.of());

    var approveOp = group.approve(proposal);
    var analysis = approveOp.dryRun();
    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertFalse(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
    assertEquals(0, analysis.satisfiedConstraints().size());
    assertEquals(1, analysis.unsatisfiedConstraints().size());
    assertEquals(1, analysis.failedConstraints().size());
  }

  @Test
  public void approve_dryRun_whenAllowedButConstraintUnsatisfied() {
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_APPROVER_1, PolicyPermission.APPROVE_OTHERS.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.APPROVE, List.of(createUnsatisfiedConstraint("failing"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var subject = Subjects.create(SAMPLE_APPROVER_1);
    var group = new JitGroupContext(
      groupPolicy,
      subject,
      Mockito.mock(Provisioner.class));

    var proposal = new MockProposal(
      SAMPLE_USER,
      group.policy().id(),
      Set.of(SAMPLE_APPROVER_1),
      Instant.now().plusSeconds(10),
      Map.of());

    var approveOp = group.approve(proposal);
    var analysis = approveOp.dryRun();

    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertFalse(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
    assertEquals(0, analysis.satisfiedConstraints().size());
    assertEquals(1, analysis.unsatisfiedConstraints().size());
    assertEquals(0, analysis.failedConstraints().size());
  }

  @Test
  public void approve_dryRun() {
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_APPROVER_1, PolicyPermission.APPROVE_OTHERS.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.APPROVE, List.of(createSatisfiedConstraint("approve"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var subject = Subjects.create(SAMPLE_APPROVER_1);
    var group = new JitGroupContext(
      groupPolicy,
      subject,
      Mockito.mock(Provisioner.class));

    var proposal = new MockProposal(
      SAMPLE_USER,
      group.policy().id(),
      Set.of(SAMPLE_APPROVER_1),
      Instant.now().plusSeconds(10),
      Map.of());

    var approveOp = group.approve(proposal);
    var analysis = approveOp.dryRun();

    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
    assertEquals(1, analysis.satisfiedConstraints().size());
    assertEquals(0, analysis.unsatisfiedConstraints().size());
    assertEquals(0, analysis.failedConstraints().size());
  }

  // -------------------------------------------------------------------------
  // approve/execute.
  // -------------------------------------------------------------------------

  @Test
  public void approve_execute_whenNotAllowed() {
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_APPROVER_1, PolicyPermission.VIEW.toMask()) // missing APPROVE_OTHERS
        .build());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var subject = Subjects.create(SAMPLE_APPROVER_1);
    var group = new JitGroupContext(
      groupPolicy,
      subject,
      Mockito.mock(Provisioner.class));

    var proposal = new MockProposal(
      SAMPLE_USER,
      group.policy().id(),
      Set.of(SAMPLE_APPROVER_1),
      Instant.now().plusSeconds(10),
      Map.of());

    var approveOp = group.approve(proposal);
    assertThrows(
      AccessDeniedException.class,
      () -> approveOp.execute());
  }

  @Test
  public void approve_execute_whenAllowedButConstraintFails() throws Exception {
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_APPROVER_1, PolicyPermission.APPROVE_OTHERS.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.APPROVE, List.of(createFailingConstraint("failing"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var subject = Subjects.create(SAMPLE_APPROVER_1);
    var group = new JitGroupContext(
      groupPolicy,
      subject,
      Mockito.mock(Provisioner.class));

    var proposal = new MockProposal(
      SAMPLE_USER,
      group.policy().id(),
      Set.of(SAMPLE_APPROVER_1),
      Instant.now().plusSeconds(10),
      Map.of());

    var approveOp = group.approve(proposal);
    assertThrows(
      PolicyAnalysis.ConstraintFailedException.class,
      () -> approveOp.execute());
  }

  @Test
  public void approve_execute_whenAllowedButConstraintUnsatisfied() {
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_APPROVER_1, PolicyPermission.APPROVE_OTHERS.toMask())
        .build(),
      Map.of(Policy.ConstraintClass.APPROVE, List.of(createUnsatisfiedConstraint("failing"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var subject = Subjects.create(SAMPLE_APPROVER_1);
    var group = new JitGroupContext(
      groupPolicy,
      subject,
      Mockito.mock(Provisioner.class));

    var proposal = new MockProposal(
      SAMPLE_USER,
      group.policy().id(),
      Set.of(SAMPLE_APPROVER_1),
      Instant.now().plusSeconds(10),
      Map.of());

    var approveOp = group.approve(proposal);
    assertThrows(
      PolicyAnalysis.ConstraintUnsatisfiedException.class,
      () -> approveOp.execute());
  }

  @Test
  public void approve_execute() throws Exception {
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_APPROVER_1, PolicyPermission.APPROVE_OTHERS.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN, List.of(new ExpiryConstraint(Duration.ofSeconds(30), Duration.ofMinutes(2))),
        Policy.ConstraintClass.APPROVE, List.of(createSatisfiedConstraint("approve"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(groupPolicy));

    var provisioner = Mockito.mock(Provisioner.class);
    var subject = Subjects.create(SAMPLE_APPROVER_1);
    var group = new JitGroupContext(
      groupPolicy,
      subject,
      provisioner);

    var proposal = new MockProposal(
      SAMPLE_USER,
      group.policy().id(),
      Set.of(SAMPLE_APPROVER_1),
      Instant.now().plusSeconds(10),
      Map.of(ExpiryConstraint.NAME, "PT1M"));

    var approveOp = group.approve(proposal);

    var principal = approveOp.execute();
    assertEquals(groupPolicy.id(), principal.id());
    assertNotNull(principal.expiry());
    assertTrue(principal.isValid());
    assertTrue(principal.expiry().isAfter(Instant.now()));
    assertTrue(principal.expiry().isBefore(Instant.now().plusSeconds(61)));

    verify(provisioner, times(1)).provisionMembership(
      eq(groupPolicy),
      eq(SAMPLE_USER),
      any());
  }
}
