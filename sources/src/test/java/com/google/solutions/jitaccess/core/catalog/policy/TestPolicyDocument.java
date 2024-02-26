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

import com.google.solutions.jitaccess.core.auth.GroupEmail;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestPolicyDocument {
  public void assertPolicyIssue(
    PolicyIssue.Code firstExpectedCode,
    String policyJson
  ) {
    try {
      PolicyDocument.fromString(policyJson.replace('\'', '\"'));
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
        "    'entitlements': [" +
        "      {" +
        "        'id': 'id-2'," +
        "        'name': 'name of id-2'," +
        "        'expires_after': 'PT15M'," +
        "        'eligible': {" +
        "          'principals': [" +
        "            'user:foo@example.com'" +
        "          ]" +
        "        }," +
        "        'requirements': {" +
        "          'requirePeerApproval': {" +
        "            'minimum_peers_to_notify': 0" +
        "          }" +
        "        }" +
        "      }" +
        "    ]" +
        "  }," +
        "  {" +
        "    'id': 'policy-1'," +
        "    'name': 'name-of-policy-1'," +
        "    'entitlements': [" +
        "      {" +
        "        'id': 'id-2'," +
        "        'name': 'name of id-2'," +
        "        'expires_after': 'PT15M'," +
        "        'eligible': {" +
        "          'principals': [" +
        "            'user:foo@example.com'" +
        "          ]" +
        "        }," +
        "        'requirements': {" +
        "          'requirePeerApproval': {" +
        "            'minimum_peers_to_notify': 0" +
        "          }" +
        "        }" +
        "      }" +
        "    ]" +
        "  }" +
        "]");
  }

  //---------------------------------------------------------------------------
  // Entitlement issues.
  //---------------------------------------------------------------------------

  @Test
  public void entitlementIdMissing() {
    assertPolicyIssue(
      PolicyIssue.Code.ENTITLEMENT_INVALID_ID,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'entitlements': [{}]" +
        "}");
  }

  @Test
  public void entitlementIdInvalid() {
    assertPolicyIssue(
      PolicyIssue.Code.ENTITLEMENT_INVALID_ID,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'entitlements': [" +
        "    {" +
        "      'id': 'not a valid id'" +
        "    }" +
        "  ]" +
        "}");
  }

  @Test
  public void entitlementNameMissing() {
    assertPolicyIssue(
      PolicyIssue.Code.ENTITLEMENT_MISSING_NAME,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'entitlements': [" +
        "    {" +
        "      'id': 'id-2'" +
        "    }" +
        "  ]" +
        "}");
  }

  @Test
  public void entitlementExpiryInvalid() {
    assertPolicyIssue(
      PolicyIssue.Code.ENTITLEMENT_INVALID_EXPIRY,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'entitlements': [" +
        "    {" +
        "      'id': 'id-2'," +
        "      'name': 'name of id-2'," +
        "      'expires_after': 'not a date'" +
        "    }" +
        "  ]" +
        "}");
  }

  @Test
  public void eligiblePrincipalInvalid() {
    assertPolicyIssue(
      PolicyIssue.Code.PRINCIPAL_INVALID,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'entitlements': [" +
        "    {" +
        "      'id': 'id-2'," +
        "      'name': 'name of id-2'," +
        "      'expires_after': 'PT15M'," +
        "      'eligible': {" +
        "        'principals': [" +
        "          'foo@example.com'" +
        "        ]" +
        "      }" +
        "    }" +
        "  ]" +
        "}");
  }

  //---------------------------------------------------------------------------
  // Requirements issues.
  //---------------------------------------------------------------------------

  @Test
  public void invalidNumberOfMinimumPeersToNotify() {
    assertPolicyIssue(
      PolicyIssue.Code.PEER_APPROVAL_CONSTRAINTS_INVALID,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'entitlements': [" +
        "    {" +
        "      'id': 'id-2'," +
        "      'name': 'name of id-2'," +
        "      'expires_after': 'PT15M'," +
        "      'eligible': {" +
        "        'principals': [" +
        "          'user:foo@example.com'" +
        "        ]" +
        "      }," +
        "      'requirements': {" +
        "        'requirePeerApproval': {" +
        "          'minimum_peers_to_notify': 0" +
        "        }" +
        "      }" +
        "    }" +
        "  ]" +
        "}");
  }

  @Test
  public void invalidNumberOfMaximumPeersToNotify() {
    assertPolicyIssue(
      PolicyIssue.Code.PEER_APPROVAL_CONSTRAINTS_INVALID,
      "{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'entitlements': [" +
        "    {" +
        "      'id': 'id-2'," +
        "      'name': 'name of id-2'," +
        "      'expires_after': 'PT15M'," +
        "      'eligible': {" +
        "        'principals': [" +
        "          'user:foo@example.com'" +
        "        ]" +
        "      }," +
        "      'requirements': {" +
        "        'requirePeerApproval': {" +
        "          'minimum_peers_to_notify': 1," +
        "          'maximum_peers_to_notify': 0" +
        "        }" +
        "      }" +
        "    }" +
        "  ]" +
        "}");
  }

  //---------------------------------------------------------------------------
  // Valid policies.
  //---------------------------------------------------------------------------

  private static PolicyDocument parse(String json) throws PolicyException {
    return PolicyDocument.fromString(json.replace('\'', '"'));
  }

  @Test
  public void jitPolicy() throws Exception {
    var json =
      "[{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'entitlements': [" +
        "    {" +
        "      'id': 'id-2'," +
        "      'name': 'name of id-2'," +
        "      'expires_after': 'PT5M'," +
        "      'eligible': {" +
        "        'principals': [" +
        "          'user:alice@example.com'," +
        "          'group:ftes@example.com'" +
        "        ]" +
        "      }" +
        "    }" +
        "  ]" +
        "}]";

    var policyFile = parse(json);
    assertTrue(policyFile.warnings().isEmpty());

    assertEquals(1, policyFile.policies().size());
    var policy = policyFile.policies().get(0);

    assertEquals("policy-1", policy.id());
    assertEquals("name-of-policy-1", policy.name());
    assertEquals(1, policy.entitlements().size());

    var entitlement = policy.entitlements().get(0);
    assertEquals("id-2", entitlement.id());
    assertEquals("name of id-2", entitlement.name());
    assertEquals(Duration.ofMinutes(5), entitlement.expiry());
    assertEquals(2, entitlement.acl().allowedPrincipals().size());
    assertIterableEquals(
      List.of(new UserEmail("alice@example.com"), new GroupEmail("ftes@example.com")),
      entitlement.acl().allowedPrincipals());
    assertInstanceOf(Policy.SelfApprovalRequirement.class, entitlement.approvalRequirement());
  }

  @Test
  public void peerApprovalPolicy() throws Exception {
    var json =
      "[{" +
        "  'id': 'policy-1'," +
        "  'name': 'name-of-policy-1'," +
        "  'entitlements': [" +
        "    {" +
        "      'id': 'id-2'," +
        "      'name': 'name of id-2'," +
        "      'expires_after': 'PT5M'," +
        "      'eligible': {" +
        "        'principals': [" +
        "          'user:alice@example.com'," +
        "          'group:ftes@example.com'" +
        "        ]" +
        "      }," +
        "      'requirements': {" +
        "        'requirePeerApproval': {}" +
        "      }" +
        "    }" +
        "  ]" +
        "}]";

    var policyFile = parse(json);
    assertTrue(policyFile.warnings().isEmpty());

    assertEquals(1, policyFile.policies().size());
    var policy = policyFile.policies().get(0);

    assertEquals("policy-1", policy.id());
    assertEquals("name-of-policy-1", policy.name());
    assertEquals(1, policy.entitlements().size());

    var entitlement = policy.entitlements().get(0);
    assertEquals("id-2", entitlement.id());
    assertEquals("name of id-2", entitlement.name());
    assertEquals(Duration.ofMinutes(5), entitlement.expiry());
    assertEquals(2, entitlement.acl().allowedPrincipals().size());
    assertIterableEquals(
      List.of(new UserEmail("alice@example.com"), new GroupEmail("ftes@example.com")),
      entitlement.acl().allowedPrincipals());
    assertInstanceOf(Policy.PeerApprovalRequirement.class, entitlement.approvalRequirement());
  }
}
