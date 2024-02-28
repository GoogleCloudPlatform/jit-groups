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

package com.google.solutions.jitaccess.core.catalog.policy;

import com.google.solutions.jitaccess.core.auth.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class TestPolicyDocument {
  private void assertPolicyIssue(
    PolicyIssue.Code firstExpectedCode,
    Executable action
  ) {
    try {
      action.execute();
      fail("Expected exception");
    }
    catch (PolicyException e) {
      assertFalse(e.getIssues().isEmpty());
      assertEquals(
        firstExpectedCode,
        e.getIssues().get(0).code(),
        e.getIssues().get(0).details());
    }
    catch (Throwable e) {
      throw new RuntimeException("Unexpected exception: " + e.getMessage(), e);
    }
  }

  private void assertPolicyIssue(
    PolicyIssue.Code firstExpectedCode,
    String policyJson
  ) {
    assertPolicyIssue(
      firstExpectedCode,
      () -> PolicyDocument.fromString(policyJson.replace('\'', '\"')));
  }

  //---------------------------------------------------------------------------
  // Document issues.
  //---------------------------------------------------------------------------

  @Test
  public void malformedJson() {
    assertPolicyIssue(
      PolicyIssue.Code.FILE_INVALID_SYNTAX,
      "{xx");
  }

  @Test
  public void emptyJson() {
    assertPolicyIssue(
      PolicyIssue.Code.FILE_INVALID_SYNTAX,
      " ");
  }

  @Test
  public void emptyArray() {
    assertPolicyIssue(
      PolicyIssue.Code.FILE_INVALID_SYNTAX,
      "[]");
  }

  //---------------------------------------------------------------------------
  // Policy issues.
  //---------------------------------------------------------------------------

  @Test
  public void unrecognizedField() {
    assertPolicyIssue(
      PolicyIssue.Code.FILE_INVALID_SYNTAX,
      "{'xx': false}");
  }

  @Test
  public void policyIdMissing() {
    assertPolicyIssue(
      PolicyIssue.Code.POLICY_INVALID_ID,
      "{}");
  }

  @Test
  public void policyIdInvalid() {
    assertPolicyIssue(
      PolicyIssue.Code.POLICY_INVALID_ID,
      "{'id': ' '}");
  }

  @Test
  public void policyNameMissing() {
    assertPolicyIssue(
      PolicyIssue.Code.POLICY_MISSING_NAME,
      "{'id': 'policy-1'}");
  }

  @Test
  public void policyWithDuplicateId() {
    assertPolicyIssue(
      PolicyIssue.Code.POLICY_DUPLICATE_ID,
      "[" +
        "  {" +
        "    'id': 'policy-1'," +
        "    'name': 'name-of-policy-1'," +
        "    'roles': [" +
        "      {" +
        "        'id': 'id-2'," +
        "        'name': 'name of id-2'" +
        "      }" +
        "    ]" +
        "  }," +
        "  {" +
        "    'id': 'policy-1'," +
        "    'name': 'name-of-policy-1'," +
        "    'roles': [" +
        "      {" +
        "        'id': 'id-2'," +
        "        'name': 'name of id-2'" +
        "      }" +
        "    ]" +
        "  }" +
        "]");
  }

  //---------------------------------------------------------------------------
  // Entitlement issues.
  //---------------------------------------------------------------------------

  @Test
  public void entitlementsMissing() {
    assertPolicyIssue(
      PolicyIssue.Code.POLICY_MISSING_ROLES,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'" +
        "}");

    assertPolicyIssue(
      PolicyIssue.Code.POLICY_MISSING_ROLES,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'roles': []" +
        "}");
  }

  @Test
  public void entitlementIdMissing() {
    assertPolicyIssue(
      PolicyIssue.Code.ROLE_INVALID_ID,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'roles': [{}]" +
        "}");
  }

  @Test
  public void entitlementIdInvalid() {
    assertPolicyIssue(
      PolicyIssue.Code.ROLE_INVALID_ID,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'roles': [" +
        "    {" +
        "      'id': 'not a valid id'" +
        "    }" +
        "  ]" +
        "}");
  }

  @Test
  public void entitlementNameMissing() {
    assertPolicyIssue(
      PolicyIssue.Code.ROLE_MISSING_NAME,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'roles': [" +
        "    {" +
        "      'id': 'id-2'" +
        "    }" +
        "  ]" +
        "}");
  }

  //---------------------------------------------------------------------------
  // Entitlement access issues.
  //---------------------------------------------------------------------------

  @Test
  public void entitlementAccessMissing() {
    assertPolicyIssue(
      PolicyIssue.Code.ROLE_MISSING_ACCESS,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'roles': [" +
        "    {" +
        "      'id': 'id-2'," +
        "      'name': 'id-2'," +
        "      'access': []" +
        "    }" +
        "  ]" +
        "}");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "user: ", "xx", "serviceAccount", "foo@example.com"})
  public void entitlementAccessContainsInvalidPrincipal(String value) {
    assertPolicyIssue(
      PolicyIssue.Code.ACCESS_INVALID_PRINCIPAL,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'roles': [" +
        "    {" +
        "      'id': 'id-2'," +
        "      'name': 'name of id-2'," +
        "      'access': [" +
        "        {" +
        "          'principal': '" + value + "'" +
        "        }" +
        "      ]" +
        "    }" +
        "  ]" +
        "}");
  }

  @Test
  public void entitlementAccessContainsInvalidEffect() {
    assertPolicyIssue(
      PolicyIssue.Code.ACCESS_INVALID_EFFECT,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'roles': [" +
        "    {" +
        "      'id': 'id-2'," +
        "      'name': 'name of id-2'," +
        "      'access': [" +
        "        {" +
        "          'principal': 'user:alice@example.com'," +
        "          'effect': 'maybe'" +
        "        }" +
        "      ]" +
        "    }" +
        "  ]" +
        "}");
  }

  @Test
  public void entitlementAccessContainsInvalidAction() {
    assertPolicyIssue(
      PolicyIssue.Code.ACCESS_INVALID_ACTION,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'roles': [" +
        "    {" +
        "      'id': 'id-2'," +
        "      'name': 'name of id-2'," +
        "      'access': [" +
        "        {" +
        "          'principal': 'user:alice@example.com'," +
        "          'effect': 'ALLOW'," +
        "          'action': 'invalid'" +
        "        }" +
        "      ]" +
        "    }" +
        "  ]" +
        "}");
  }

  //---------------------------------------------------------------------------
  // Entitlement constraints issues.
  //---------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {"default", "min", "max"})
  public void entitlementConstraintsContainInvalidDefaultActivationDuration(String field) {
    assertPolicyIssue(
      PolicyIssue.Code.CONSTRAINT_DURATION_CONSTRAINT_INVALID,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'roles': [" +
        "    {" +
        "      'id': 'id-2'," +
        "      'name': 'name of id-2'," +
        "      'access': [" +
        "        {" +
        "          'principal': 'user:alice@example.com'," +
        "          'effect': 'ALLOW'," +
        "          'action': 'REQUEST'" +
        "        }" +
        "      ]," +
        "      'constraints': {" +
        "        'activation_duration': {" +
        "          '" + field + "': 'invalid'" +
        "        }" +
        "      }" +
        "    }" +
        "  ]" +
        "}");
  }

  @ParameterizedTest
  @ValueSource(strings = {"minimum_peers_to_notify", "maximum_peers_to_notify"})
  public void entitlementConstraintsContainInvalidNumberOfPeersToNotify(String field) {
    assertPolicyIssue(
      PolicyIssue.Code.CONSTRAINT_APPROVAL_LIMITS_INVALID,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'roles': [" +
        "    {" +
        "      'id': 'id-2'," +
        "      'name': 'name of id-2'," +
        "      'access': [" +
        "        {" +
        "          'principal': 'user:alice@example.com'," +
        "          'effect': 'ALLOW'," +
        "          'action': 'REQUEST'" +
        "        }" +
        "      ]," +
        "      'constraints': {" +
        "        'approval': {" +
        "          '" + field + "': '0'" +
        "        }" +
        "      }" +
        "    }" +
        "  ]" +
        "}");
  }

  //---------------------------------------------------------------------------
  // toPolicySet.
  //---------------------------------------------------------------------------

  private static final PolicyDocument.ConstraintsNode DEFAULT_CONSTRAINTS =
    new PolicyDocument.ConstraintsNode(
      new PolicyDocument.ActivationDurationNode(
        "PT1M",
        "PT15M",
        "P1D"
      ),
      new PolicyDocument.ApprovalConstraintsNode(1, 2));

  private static final String MINIMAL_POLICY = (
    "[" +
      "{" +
      "  'id': 'policy-1'," +
      "  'name': 'name-of-policy-1'," +
      "  'roles': [" +
      "    {" +
      "      'id': 'entitlement-1'," +
      "      'name': 'name of entitlement-1'," +
      "      'access': [" +
      "        {" +
      "          'principal': 'user:alice@example.com'," +
      "          'action': 'REQUEST'" +
      "        }" +
      "      ]" +
      "    }" +
      "  ]" +
      "}" +
      "]").replace('\'', '"');

  private static final String FULL_POLICY = (
    "[" +
      "{" +
      "  'id': 'policy-1'," +
      "  'name': 'name-of-policy-1'," +
      "  'roles': [" +
      "    {" +
      "      'id': 'entitlement-1'," +
      "      'name': 'name of entitlement-1'," +
      "      'access': [" +
      "        {" +
      "          'principal': 'user:alice@example.com'," +
      "          'action': 'request'," +
      "          'effect': 'deny'" +
      "        }" +
      "      ]," +
      "      'constraints': {" +
      "        'activation_duration': {" +
      "          'min': 'P1D'," +
      "          'default': 'P2D'," +
      "          'max': 'P3D'" +
      "        }," +
      "        'approval': {" +
      "          'minimum_peers_to_notify': 3," +
      "          'maximum_peers_to_notify': 5" +
      "        }" +
      "      }" +
      "    }" +
      "  ]" +
      "}" +
      "]").replace('\'', '"');

  @Test
  public void whenDefaultConstraintsValid_ThenMinimalPolicySucceeds() throws Exception {
    var policyFile = PolicyDocument.fromString(MINIMAL_POLICY);
    assertTrue(policyFile.warnings().isEmpty());

    var policies = policyFile
      .toPolicySet(DEFAULT_CONSTRAINTS)
      .policies();

    assertEquals(1, policies.size());
    var policy = policies.get(0);

    assertEquals("policy-1", policy.id());
    assertEquals("name-of-policy-1", policy.name());
    assertEquals(1, policy.roles().size());

    var entitlement = policy.roles().get(0);
    assertEquals("[jit-role] policy-1/entitlement-1", entitlement.id().toString());
    assertEquals("name of entitlement-1", entitlement.name());

    assertEquals(1, entitlement.acl().entries().size());

    var ace = entitlement.acl().entries().stream().toList().get(0);
    assertEquals(new UserId("alice@example.com"), ace.principal);
    assertEquals(Policy.RoleAccessRights.REQUEST, ace.accessRights);

    assertNotNull(entitlement.constraints());
    assertNotNull(entitlement.constraints().approvalConstraints());
    assertEquals(
      Duration.ofMinutes(1),
      entitlement.constraints().defaultActivationDuration());
    assertEquals(
      Duration.ofMinutes(15),
      entitlement.constraints().minActivationDuration());
    assertEquals(
      Duration.ofDays(1),
      entitlement.constraints().maxActivationDuration());
    assertEquals(
      1,
      entitlement.constraints().approvalConstraints().minimumNumberOfPeersToNotify());
    assertEquals(
      2,
      entitlement.constraints().approvalConstraints().maximumNumberOfPeersToNotify());
  }


  @Test
  public void whenDefaultDurationConstraintsMissing_ThenMinimalPolicyThrowsException() throws Exception {
    var defaultConstraints = new PolicyDocument.ConstraintsNode(
      null,
      new PolicyDocument.ApprovalConstraintsNode(1, 2));

    assertPolicyIssue(
      PolicyIssue.Code.CONSTRAINT_DURATION_CONSTRAINTS_MISSING,
      () -> PolicyDocument.fromString(MINIMAL_POLICY).toPolicySet(defaultConstraints)
    );
  }

  @Test
  public void whenDefaultDurationConstraintEmpty_ThenMinimalPolicyThrowsException() throws Exception {
    var defaultConstraints = new PolicyDocument.ConstraintsNode(
      new PolicyDocument.ActivationDurationNode(
        "PT1M",
        "PT15M",
        ""
      ),
      new PolicyDocument.ApprovalConstraintsNode(1, 2));

    assertPolicyIssue(
      PolicyIssue.Code.CONSTRAINT_DURATION_CONSTRAINT_EMPTY,
      () -> PolicyDocument.fromString(MINIMAL_POLICY).toPolicySet(defaultConstraints)
    );
  }

  @Test
  public void whenApprovalConstraintsMissing_ThenMinimalPolicyThrowsException() throws Exception {
    var defaultConstraints = new PolicyDocument.ConstraintsNode(
      new PolicyDocument.ActivationDurationNode(
        "PT1M",
        "PT15M",
        "PT15M"
      ),
      null);

    assertPolicyIssue(
      PolicyIssue.Code.CONSTRAINT_APPROVAL_CONSTRAINTS_MISSING,
      () -> PolicyDocument.fromString(MINIMAL_POLICY).toPolicySet(defaultConstraints)
    );
  }

  @Test
  public void whenApprovalLimitEmpty_ThenMinimalPolicyThrowsException() throws Exception {
    var defaultConstraints = new PolicyDocument.ConstraintsNode(
      new PolicyDocument.ActivationDurationNode(
        "PT1M",
        "PT15M",
        "PT15M"
      ),
      new PolicyDocument.ApprovalConstraintsNode(null, 2));

    assertPolicyIssue(
      PolicyIssue.Code.CONSTRAINT_APPROVAL_LIMITS_MISSING,
      () -> PolicyDocument.fromString(MINIMAL_POLICY).toPolicySet(defaultConstraints)
    );
  }

  @Test
  public void fullPolicy() throws Exception {
    var policyFile = PolicyDocument.fromString(FULL_POLICY);
    assertTrue(policyFile.warnings().isEmpty());

    var policies = policyFile
      .toPolicySet(DEFAULT_CONSTRAINTS)
      .policies();

    assertEquals(1, policies.size());
    var policy = policies.get(0);

    assertEquals("policy-1", policy.id());
    assertEquals("name-of-policy-1", policy.name());
    assertEquals(1, policy.roles().size());

    var entitlement = policy.roles().get(0);
    assertEquals("[jit-role] policy-1/entitlement-1", entitlement.id().toString());
    assertEquals("name of entitlement-1", entitlement.name());

    var ace = entitlement.acl().entries().stream().toList().get(0);
    assertEquals(new UserId("alice@example.com"), ace.principal);
    assertEquals(Policy.RoleAccessRights.REQUEST, ace.accessRights);

    assertNotNull(entitlement.constraints());
    assertNotNull(entitlement.constraints().approvalConstraints());
    assertEquals(
      Duration.ofDays(2),
      entitlement.constraints().defaultActivationDuration());
    assertEquals(
      Duration.ofDays(1),
      entitlement.constraints().minActivationDuration());
    assertEquals(
      Duration.ofDays(3),
      entitlement.constraints().maxActivationDuration());
    assertEquals(
      3,
      entitlement.constraints().approvalConstraints().minimumNumberOfPeersToNotify());
    assertEquals(
      5,
      entitlement.constraints().approvalConstraints().maximumNumberOfPeersToNotify());
  }
}
