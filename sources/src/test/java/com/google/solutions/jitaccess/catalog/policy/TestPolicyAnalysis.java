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

package com.google.solutions.jitaccess.catalog.policy;

import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.catalog.auth.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class TestPolicyAnalysis {
  private static final UserId SAMPLE_USER = new UserId("user@example.com");
  private static final JitGroupId SAMPLE_GROUPID = new JitGroupId("env-1", "system-1", "group-1");


  private static class SamplePolicy extends AbstractPolicy {
    public SamplePolicy() {
      super("Test", "Test", null, Map.of());
    }

    public SamplePolicy(
      @Nullable AccessControlList acl
    ) {
      super("Test", "Test", acl, Map.of());
    }

    public SamplePolicy(
      @NotNull Map<ConstraintClass, Collection<Constraint>> constraints
    ) {
      super("Test", "Test", null, constraints);
    }

    public SamplePolicy(
      @Nullable AccessControlList acl,
      @NotNull Map<ConstraintClass, Collection<Constraint>> constraints
    ) {
      super("Test", "Test", acl, constraints);
    }
  }

  private static Subject createSubject(
    UserId user,
    Set<PrincipalId> otherPrincipals
  ) {
    var subject = Mockito.mock(Subject.class);
    when(subject.user()).thenReturn(user);
    when(subject.principals()).thenReturn(
        Stream.concat(otherPrincipals.stream(), Stream.<PrincipalId>of(user))
          .map(p -> new Principal(p))
          .collect(Collectors.toSet()));

    return subject;
  }

  //---------------------------------------------------------------------------
  // activeMembership.
  //---------------------------------------------------------------------------

  @Test
  public void isMembershipActive_whenSubjectLacksPrincipal() {
    var policy = new SamplePolicy();
    var subject = createSubject(SAMPLE_USER, Set.of());

    var check = new PolicyAnalysis(
      policy,
      subject,
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN));

    var result = check.execute();
    assertFalse(result.activeMembership().isPresent());
  }

  @Test
  public void isMembershipActive_whenSubjectHasPrincipal() {
    var policy = new SamplePolicy();
    var subject = createSubject(SAMPLE_USER, Set.of(SAMPLE_GROUPID));

    var check = new PolicyAnalysis(
      policy,
      subject,
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN));

    var result = check.execute();
    assertTrue(result.activeMembership().isPresent());
    assertSame(SAMPLE_GROUPID, result.activeMembership().get().id());
  }

  //---------------------------------------------------------------------------
  // Constraints check.
  //---------------------------------------------------------------------------

  @Test
  public void constraints_whenPolicyHasNoConstraints() {
    var policy = new SamplePolicy();
    var check = new PolicyAnalysis(
      policy,
      createSubject(SAMPLE_USER, Set.of()),
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN));
    var result = check.execute();

    assertTrue(result.satisfiedConstraints().isEmpty());
    assertTrue(result.unsatisfiedConstraints().isEmpty());
    assertTrue(result.failedConstraints().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "subject.email=='user@example.com'",
    "size(subject.principals) > 0",
    "group.environment == 'env-1'",
    "group.system == 'system-1'",
    "group.name == 'group-1'",
    "input.testInt == 42",
    "input.testString == 'sample'",
    "input.testBoolean"
  })
  public void constraints_whenConstraintSatisfied(String expression) {
    var constraint = new CelConstraint(
      "cel",
      "",
      List.of(
        new CelConstraint.LongVariable("testInt", "", 41L, 42L),
        new CelConstraint.StringVariable("testString", "", 0 ,10),
        new CelConstraint.BooleanVariable("testBoolean", "")),
      expression);

    var policy = new SamplePolicy(Map.of(
      Policy.ConstraintClass.JOIN,
      List.of(constraint)));

    var check = new PolicyAnalysis(
      policy,
      createSubject(SAMPLE_USER, Set.of()),
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN));
    check.applyConstraints(Policy.ConstraintClass.JOIN);
    check.input().get(0).set("42");
    check.input().get(1).set("sample");
    check.input().get(2).set("True");

    var result = check.execute();

    assertEquals(1, result.satisfiedConstraints().size());
    assertTrue(result.unsatisfiedConstraints().isEmpty());
    assertTrue(result.failedConstraints().isEmpty());
  }

  @Test
  public void constraints_whenConstraintUnsatisfied() {
    var constraint = new CelConstraint(
      "cel",
      "",
      List.of(),
      "false");

    var policy = new SamplePolicy(Map.of(
      Policy.ConstraintClass.JOIN,
      List.of(constraint)));

    var check = new PolicyAnalysis(
      policy,
      createSubject(SAMPLE_USER, Set.of()),
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN));
    check.applyConstraints(Policy.ConstraintClass.JOIN);
    var result = check.execute();

    assertTrue(result.satisfiedConstraints().isEmpty());
    assertEquals(1, result.unsatisfiedConstraints().size());
    assertTrue(result.failedConstraints().isEmpty());
  }

  @Test
  public void constraints_whenConstraintFails() {
    var constraint = new CelConstraint(
      "cel",
      "",
      List.of(),
      "syntax error(");

    var policy = new SamplePolicy(Map.of(
      Policy.ConstraintClass.JOIN,
      List.of(constraint)));

    var check = new PolicyAnalysis(
      policy,
      createSubject(SAMPLE_USER, Set.of()),
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN));
    check.applyConstraints(Policy.ConstraintClass.JOIN);
    var result = check.execute();

    assertTrue(result.satisfiedConstraints().isEmpty());
    assertEquals(1, result.unsatisfiedConstraints().size());
    assertEquals(1, result.failedConstraints().size());
    assertNotNull(result.failedConstraints().get(constraint));
  }

  @Test
  public void evaluateConstraint_whenInputMissing() {
    var constraint = new CelConstraint(
      "cel",
      "",
      List.of(new CelConstraint.StringVariable("test", "", 0, 10)),
      "true");

    var policy = new SamplePolicy(Map.of(
      Policy.ConstraintClass.JOIN,
      List.of(constraint)));

    var check = new PolicyAnalysis(
      policy,
      createSubject(SAMPLE_USER, Set.of()),
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN));

    check.applyConstraints(Policy.ConstraintClass.JOIN);
    var result = check.execute();

    assertTrue(result.satisfiedConstraints().isEmpty());
    assertEquals(1, result.unsatisfiedConstraints().size());
    assertEquals(1, result.failedConstraints().size());
    assertNotNull(result.failedConstraints().get(constraint));
  }

  //---------------------------------------------------------------------------
  // isAccessAllowed.
  //---------------------------------------------------------------------------

  @Test
  public void isAccessAllowed_whenPolicyDeniesAccess() {
    var subject = createSubject(SAMPLE_USER, Set.of());
    var policy = new SamplePolicy(
      new AccessControlList.Builder()
        .deny(subject.user(), PolicyPermission.JOIN.toMask())
        .build());

    var result = new PolicyAnalysis(
      policy,
      subject,
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN)).execute();

    assertFalse(result.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertFalse(result.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
  }

  @Test
  public void isAccessAllowed_whenMembershipActiveButPolicyDeniesAccess() {
    var subject = createSubject(SAMPLE_USER, Set.of(SAMPLE_GROUPID));
    var policy = new SamplePolicy(
      new AccessControlList.Builder()
        .deny(subject.user(), PolicyPermission.JOIN.toMask())
        .build());

    var result = new PolicyAnalysis(
      policy,
      subject,
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN)).execute();

    assertTrue(result.activeMembership().isPresent());

    assertFalse(result.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertFalse(result.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
  }

  @Test
  public void isAccessAllowed_whenPolicyGrantsAccessButConstraintUnsatisfied() {
    var subject = createSubject(SAMPLE_USER, Set.of());
    var policy = new SamplePolicy(
      new AccessControlList.Builder()
        .allow(subject.user(), PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(new CelConstraint("unsatisfied", "", List.of(), "false"))));

    var result = new PolicyAnalysis(
      policy,
      subject,
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN))
      .applyConstraints(Policy.ConstraintClass.JOIN)
      .execute();

    assertTrue(result.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertFalse(result.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
  }

  @Test
  public void isAccessAllowed_whenPolicyGrantsAccessAndConstraintSatisfied() {
    var subject = createSubject(SAMPLE_USER, Set.of());
    var policy = new SamplePolicy(
      new AccessControlList.Builder()
        .allow(subject.user(), PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(new CelConstraint("satisfied", "", List.of(), "true"))));

    var result = new PolicyAnalysis(
      policy,
      subject,
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN)).execute();

    assertTrue(result.isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
    assertTrue(result.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
  }
  
  //---------------------------------------------------------------------------
  // verifyAccessAllowed.
  //---------------------------------------------------------------------------

  @Test
  public void verifyAccessAllowed_whenPolicyDeniesAccess() {
    var subject = createSubject(SAMPLE_USER, Set.of());
    var policy = new SamplePolicy(
      new AccessControlList.Builder()
        .deny(subject.user(), PolicyPermission.JOIN.toMask())
        .build());

    var result = new PolicyAnalysis(
      policy,
      subject,
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN)).execute();

    assertThrows(
      AccessDeniedException.class,
      () -> result.verifyAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertThrows(
      AccessDeniedException.class,
      () -> result.verifyAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
  }

  @Test
  public void verifyAccessAllowed_whenMembershipActiveButPolicyDeniesAccess() {
    var subject = createSubject(SAMPLE_USER, Set.of(SAMPLE_GROUPID));
    var policy = new SamplePolicy(
      new AccessControlList.Builder()
        .deny(subject.user(), PolicyPermission.JOIN.toMask())
        .build());

    var result = new PolicyAnalysis(
      policy,
      subject,
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN)).execute();

    assertTrue(result.activeMembership().isPresent());

    assertThrows(
      AccessDeniedException.class,
      () -> result.verifyAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertThrows(
      AccessDeniedException.class,
      () -> result.verifyAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT));
  }

  @Test
  public void verifyAccessAllowed_whenPolicyGrantsAccessButConstraintUnsatisfied() throws Exception {
    var subject = createSubject(SAMPLE_USER, Set.of());
    var policy = new SamplePolicy(
      new AccessControlList.Builder()
        .allow(subject.user(), PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(new CelConstraint("unsatisfied", "message", List.of(), "false"))));

    var result = new PolicyAnalysis(
      policy,
      subject,
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN))
      .applyConstraints(Policy.ConstraintClass.JOIN)
      .execute();

    result.verifyAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS);
    try {
      result.verifyAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT);
      fail();
    }
    catch (AccessDeniedException e) {
      assertEquals("message", e.getMessage());
    }
  }

  @Test
  public void verifyAccessAllowed_whenPolicyGrantsAccessButConstraintFails() throws Exception {
    var subject = createSubject(SAMPLE_USER, Set.of());
    var policy = new SamplePolicy(
      new AccessControlList.Builder()
        .allow(subject.user(), PolicyPermission.JOIN.toMask())
        .build(),
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(new CelConstraint("invalid", "", List.of(), "(;invalid"))));

    var result = new PolicyAnalysis(
      policy,
      subject,
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN))
      .applyConstraints(Policy.ConstraintClass.JOIN)
      .execute();

    result.verifyAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS);
    try {
      result.verifyAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT);
      fail();
    }
    catch (PolicyAnalysis.ConstraintFailedException e) {
      assertEquals(1, e.exceptions().size());
    }
  }

  @Test
  public void verifyAccessAllowed_whenPolicyGrantsAccessAndConstraintSatisfied() throws Exception {
    var subject = createSubject(SAMPLE_USER, Set.of());
    var policy = Mockito.mock(Policy.class);
    when(policy.isAllowedByAccessControlList(subject, EnumSet.of(PolicyPermission.JOIN)))
      .thenReturn(true);
    when(policy.constraints(eq(Policy.ConstraintClass.JOIN)))
      .thenReturn(List.of(new CelConstraint("satisfied", "", List.of(), "true")));

    var result = new PolicyAnalysis(
      policy,
      subject,
      SAMPLE_GROUPID,
      EnumSet.of(PolicyPermission.JOIN)).execute();

    result.verifyAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT);
    result.verifyAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS);
  }
}
