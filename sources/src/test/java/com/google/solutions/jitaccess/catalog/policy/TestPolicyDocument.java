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

import com.google.api.client.json.GenericJson;
import com.google.solutions.jitaccess.apis.FolderId;
import com.google.solutions.jitaccess.apis.IamRole;
import com.google.solutions.jitaccess.apis.OrganizationId;
import com.google.solutions.jitaccess.apis.ProjectId;
import com.google.solutions.jitaccess.catalog.auth.UserClassId;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


public class TestPolicyDocument {
  private static final UserId SAMPLE_USER = new UserId("user@example.com");

  private static final Policy.Metadata METADATA = new Policy.Metadata("test", Instant.EPOCH);

  //---------------------------------------------------------------------------
  // toString.
  //---------------------------------------------------------------------------

  @Test
  public void toString_whenPolicyValid() {
    var policy = new EnvironmentPolicy("env", "", METADATA);
    assertEquals(
      "---\n" +
        "schemaVersion: 1\n" +
        "environment:\n" +
        "  name: \"env\"\n" +
        "  description: \"\"\n" +
        "  access:\n" +
        "  - principal: \"class:iapUsers\"\n" +
        "    allow: \"VIEW\"\n" +
        "  systems: []\n",
      new PolicyDocument(policy).toString());
  }

  //---------------------------------------------------------------------------
  // fromString.
  //---------------------------------------------------------------------------

  @Test
  public void fromString_whenYamlIsEmpty() {
    var e = assertThrows(
      PolicyDocument.SyntaxException.class,
      () -> PolicyDocument.fromString("  "));
    assertTrue(e.issues().get(0).error());
    assertEquals(
      PolicyDocument.Issue.Code.FILE_UNKNOWN_PROPERTY,
      e.issues().get(0).code());
  }

  @Test
  public void fromString_whenYamlMalformed() {
    var e = assertThrows(
      PolicyDocument.SyntaxException.class,
      () -> PolicyDocument.fromString("}"));
    assertTrue(e.issues().get(0).error());
    assertEquals(
      PolicyDocument.Issue.Code.FILE_INVALID_SYNTAX,
      e.issues().get(0).code());
  }

  @Test
  public void fromString_whenFieldUnrecognized() {
    var yaml = "foo: 1";

    var e = assertThrows(
      PolicyDocument.SyntaxException.class,
      () -> PolicyDocument.fromString(yaml));
    assertTrue(e.issues().get(0).error());
    assertEquals(
      PolicyDocument.Issue.Code.FILE_UNKNOWN_PROPERTY,
      e.issues().get(0).code());
  }

  @Test
  public void fromString_whenSchemaVersionInvalid() {
    var yaml = "schemaVersion: 0";

    var e = assertThrows(
      PolicyDocument.SyntaxException.class,
      () -> PolicyDocument.fromString(yaml));
    assertTrue(e.issues().get(0).error());
    assertEquals(
      PolicyDocument.Issue.Code.FILE_INVALID_VERSION,
      e.issues().get(0).code());
  }

  @Test
  public void fromString_whenSchemaVersionMissing() {
    var yaml = "schemaVersion: ";

    var e = assertThrows(
      PolicyDocument.SyntaxException.class,
      () -> PolicyDocument.fromString(yaml));
    assertTrue(e.issues().get(0).error());
    assertEquals(
      PolicyDocument.Issue.Code.FILE_INVALID_VERSION,
      e.issues().get(0).code());
  }

  @Test
  public void fromString_whenEnvironmentMissing() {
    var yaml =
      "schemaVersion: 1\n" +
      "environment: ";


    var e = assertThrows(
      PolicyDocument.SyntaxException.class,
      () -> PolicyDocument.fromString(yaml));
    assertTrue(e.issues().get(0).error());
    assertEquals(
      PolicyDocument.Issue.Code.ENVIRONMENT_MISSING,
      e.issues().get(0).code());
  }

  @Test
  public void fromString_whenEnvironmentNameInvalid() {
    var yaml =
      "schemaVersion: 1\n" +
      "environment: \n" +
      "  name: ''";

    var e = assertThrows(
      PolicyDocument.SyntaxException.class,
      () -> PolicyDocument.fromString(yaml));
    assertTrue(e.issues().get(0).error());
    assertEquals(
      PolicyDocument.Issue.Code.ENVIRONMENT_INVALID,
      e.issues().get(0).code());
  }

  @Test
  public void fromString_whenSystemNameInvalid() {
    var yaml =
      "schemaVersion: 1\n" +
      "environment: \n" +
        "  name: 'env-1'\n" +
        "  systems:\n" +
        "  - name: ''";

    var e = assertThrows(
      PolicyDocument.SyntaxException.class,
      () -> PolicyDocument.fromString(yaml));
    assertTrue(e.issues().get(0).error());
    assertEquals(
      PolicyDocument.Issue.Code.SYSTEM_INVALID,
      e.issues().get(0).code());
  }

  @Test
  public void fromString() throws Exception {
    var yaml =
      "schemaVersion: 1\n" +
        "environment: \n" +
        "  name: 'env-1'";

    var doc = PolicyDocument.fromString(yaml);
    assertEquals("env-1", doc.policy().name());
    assertEquals("memory", doc.policy().metadata().source());
    assertFalse(doc.policy().metadata().lastModified().isAfter(Instant.now()));
  }

  //---------------------------------------------------------------------------
  // fromFile.
  //---------------------------------------------------------------------------

  @Test
  public void fromFile_whenFileNotFound() {
    assertThrows(
      FileNotFoundException.class,
      () -> PolicyDocument.fromFile(new File("doesnotexist.yaml")));
  }

  @Test
  public void fromFile() throws Exception {
    var yaml = "schemaVersion: 1\n" +
      "environment: \n" +
      "  name: 'env-1'";

    var tempFile = File.createTempFile("policy", "yaml");
    Files.writeString(tempFile.toPath(), yaml);

    var doc = PolicyDocument.fromFile(tempFile);
    assertEquals("env-1", doc.policy().name());
    assertEquals(tempFile.getName(), doc.policy().metadata().source());
    assertFalse(doc.policy().metadata().lastModified().isAfter(Instant.now()));
  }

  //---------------------------------------------------------------------------
  // Issue.
  //---------------------------------------------------------------------------

  @Nested
  public static class Issue {
    @Test
    public void toString_whenError() {
      var issue = new PolicyDocument.Issue(true, null, PolicyDocument.Issue.Code.FILE_INVALID, "Error!!1");
      assertEquals("ERROR FILE_INVALID: Error!!1", issue.toString());
    }

    @Test
    public void toString_whenWarning() {
      var issue = new PolicyDocument.Issue(false, null, PolicyDocument.Issue.Code.FILE_INVALID, "Warning!!1");
      assertEquals("WARNING FILE_INVALID: Warning!!1", issue.toString());
    }
  }

  //---------------------------------------------------------------------------
  // EnvironmentElement.
  //---------------------------------------------------------------------------

  @Nested
  public static class EnvironmentElement {

    @Test
    public void toYaml_whenAclIsEmpty() {
      var policy = new EnvironmentPolicy("env", "Env", AccessControlList.EMPTY, Map.of(), METADATA);
      var element = PolicyDocument.EnvironmentElement.toYaml(policy);

      assertEquals("env", element.name());
      assertEquals("Env", element.description());
      assertEquals(0, element.acl().size());
    }

    @Test
    public void toYaml_whenAclIsNotEmpty() {
      var policy = new EnvironmentPolicy(
        "env",
        "Env",
        new AccessControlList(List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, 1))),
        Map.of(),
        METADATA);
      var element = PolicyDocument.EnvironmentElement.toYaml(policy);

      assertEquals("env", element.name());
      assertEquals("Env", element.description());
      assertEquals(1, element.acl().size());
    }

    @Test
    public void toYaml_whenConstraintsSpecified() {
      var policy = new EnvironmentPolicy(
        "env",
        "Env",
        new AccessControlList(List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, 1))),
        Map.of(
          Policy.ConstraintClass.JOIN, List.of(new CelConstraint("join", "", List.of(), "true")),
          Policy.ConstraintClass.APPROVE, List.of(new CelConstraint("approve", "", List.of(), "true"))),
        METADATA);
      var element = PolicyDocument.EnvironmentElement.toYaml(policy);

      assertEquals(1, element.constraints().join().size());
      assertEquals("join", element.constraints().join().get(0).name());

      assertEquals(1, element.constraints().approve().size());
      assertEquals("approve", element.constraints().approve().get(0).name());
    }

    @Test
    public void toPolicy_whenEnvironmentNameNotEmpty() {
      var element = new PolicyDocument.EnvironmentElement("env", "", List.of(), null, null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues, METADATA);

      assertTrue(policy.isPresent());
      assertEquals("env", policy.get().name());
    }

    @Test
    public void toPolicy_whenEnvironmentNameIsNull() {
      var element = new PolicyDocument.EnvironmentElement(null, "", List.of(), null, null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues, new Policy.Metadata("test", Instant.EPOCH, "1", "default-name"));

      assertTrue(policy.isPresent());
      assertEquals("default-name", policy.get().name());
    }

    @Test
    public void toPolicy_whenEnvironmentNameEmpty() {
      var element = new PolicyDocument.EnvironmentElement("", "", List.of(), null, null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues, new Policy.Metadata("test", Instant.EPOCH, "1", "default-name"));

      assertTrue(policy.isPresent());
      assertEquals("default-name", policy.get().name());
    }

    @Test
    public void toPolicy_whenAclIsNull() {
      var element = new PolicyDocument.EnvironmentElement("env", "", null, null, null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues, METADATA);

      assertTrue(policy.isPresent());
      assertTrue(policy.get().accessControlList().isPresent());

      var aces = List.copyOf(policy.get().accessControlList().get().entries());
      assertEquals(1, aces.size());
      assertEquals(UserClassId.IAP_USERS, aces.get(0).principal);
      assertEquals(PolicyPermission.VIEW.toMask(), aces.get(0).accessRights);
    }

    @Test
    public void toPolicy_whenAclIsEmpty() {
      var element = new PolicyDocument.EnvironmentElement("env", "", List.of(), null, null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues, METADATA);

      assertTrue(policy.isPresent());
      assertTrue(policy.get().accessControlList().isPresent());
      assertEquals(0, policy.get().accessControlList().get().entries().size());
    }

    @Test
    public void toPolicy_whenAclInvalid() {
      var element = new PolicyDocument.EnvironmentElement(
        "env",
        "",
        List.of(new PolicyDocument.AccessControlEntryElement("invalid", "JOIN", null)),
        null,
        null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues, METADATA);

      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.ACL_INVALID_PRINCIPAL,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy_whenSystemInvalid() {
      var element = new PolicyDocument.EnvironmentElement(
        "env",
        "",
        List.of(),
        null,
        List.of(new PolicyDocument.SystemElement(null, null, null, null, null)));
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues, METADATA);

      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.SYSTEM_INVALID,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy_whenConstraintInvalid() {
      var element = new PolicyDocument.EnvironmentElement(
        "env",
        "",
        List.of(),
        new PolicyDocument.ConstraintsElement(
          List.of(new PolicyDocument.ConstraintElement("invalid", null, null, null, null, null, null)),
          null
        ),
        List.of(new PolicyDocument.SystemElement("sys", "System", null, null, null)));
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues, METADATA);

      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.CONSTRAINT_INVALID_TYPE,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy() {
      var element = new PolicyDocument.EnvironmentElement(
        "env",
        "",
        List.of(),
        null,
        List.of(new PolicyDocument.SystemElement("sys", "System", null, null, null)));
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues, METADATA);

      assertTrue(policy.isPresent());
      assertEquals(1, policy.get().systems().size());
    }
  }

  //---------------------------------------------------------------------------
  // SystemElement.
  //---------------------------------------------------------------------------

  @Nested
  public static class SystemElement {
    @Test
    public void toYaml_whenAclIsNull() {
      var policy = new SystemPolicy("sys", "System", null, Map.of());
      var element = PolicyDocument.SystemElement.toYaml(policy);

      assertEquals("sys", element.name());
      assertEquals("System", element.description());
      assertNull(element.acl());
    }

    @Test
    public void toYaml_whenAclIsEmpty() {
      var policy = new SystemPolicy("sys", "System", AccessControlList.EMPTY, Map.of());
      var element = PolicyDocument.SystemElement.toYaml(policy);

      assertEquals("sys", element.name());
      assertEquals("System", element.description());
      assertEquals(0, element.acl().size());
    }

    @Test
    public void toYaml_whenAclIsNotEmpty() {
      var policy = new SystemPolicy(
        "sys",
        "System",
        new AccessControlList(List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, 1))),
        Map.of());
      var element = PolicyDocument.SystemElement.toYaml(policy);

      assertEquals("sys", element.name());
      assertEquals("System", element.description());
      assertEquals(1, element.acl().size());
    }

    @Test
    public void toYaml_whenConstraintsSpecified() {
      var policy = new SystemPolicy(
        "sys",
        "System",
        new AccessControlList(List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, 1))),
        Map.of(
          Policy.ConstraintClass.JOIN, List.of(new CelConstraint("join", "", List.of(), "true")),
          Policy.ConstraintClass.APPROVE, List.of(new CelConstraint("approve", "", List.of(), "true"))));
      var element = PolicyDocument.SystemElement.toYaml(policy);

      assertEquals(1, element.constraints().join().size());
      assertEquals("join", element.constraints().join().get(0).name());

      assertEquals(1, element.constraints().approve().size());
      assertEquals("approve", element.constraints().approve().get(0).name());
    }

    @Test
    public void toPolicy_whenAclIsNull() {
      var element = new PolicyDocument.SystemElement("sys", "", null, null, null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertTrue(policy.isPresent());
      assertFalse(policy.get().accessControlList().isPresent());
    }

    @Test
    public void toPolicy_whenAclIsEmpty() {
      var element = new PolicyDocument.SystemElement("sys", "", List.of(), null, null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertTrue(policy.isPresent());
      assertTrue(policy.get().accessControlList().isPresent());
      assertEquals(0, policy.get().accessControlList().get().entries().size());
    }

    @Test
    public void toPolicy_whenAclInvalid() {
      var element = new PolicyDocument.SystemElement(
        "sys",
        "",
        List.of(new PolicyDocument.AccessControlEntryElement("invalid", "JOIN", null)),
        null,
        null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.ACL_INVALID_PRINCIPAL,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy_whenGroupInvalid() {
      var element = new PolicyDocument.SystemElement(
        "sys",
        "",
        List.of(),
        null,
        List.of(new PolicyDocument.GroupElement(null, null, List.of(), null, null)));
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.GROUP_INVALID,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy_whenConstraintInvalid() {
      var element = new PolicyDocument.SystemElement(
        "sys",
        "",
        List.of(),
        new PolicyDocument.ConstraintsElement(
          List.of(new PolicyDocument.ConstraintElement("invalid", null, null, null, null, null, null)),
          null
        ),
        List.of(new PolicyDocument.GroupElement("group", "Group", List.of(), null, null)));
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.CONSTRAINT_INVALID_TYPE,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy() {
      var element = new PolicyDocument.SystemElement(
        "sys",
        "",
        List.of(),
        null,
        List.of(new PolicyDocument.GroupElement("group", "Group", List.of(), null, null)));
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertTrue(policy.isPresent());
      assertEquals(1, policy.get().groups().size());
    }
  }

  //---------------------------------------------------------------------------
  // GroupElement.
  //---------------------------------------------------------------------------

  @Nested
  public static class GroupElement {
    @Test
    public void toYaml_whenAclIsEmpty() {
      var policy = new JitGroupPolicy(
        "group",
        "Group",
        AccessControlList.EMPTY,
        Map.of(),
        List.of());
      var element = PolicyDocument.GroupElement.toYaml(policy);

      assertEquals("group", element.name());
      assertEquals("Group", element.description());
      assertEquals(0, element.acl().size());
    }

    @Test
    public void toYaml_whenAclIsNotEmpty() {
      var policy = new JitGroupPolicy(
        "group",
        "Group",
        new AccessControlList(List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, 1))),
        Map.of(),
        List.of());
      var element = PolicyDocument.GroupElement.toYaml(policy);

      assertEquals("group", element.name());
      assertEquals("Group", element.description());
      assertEquals(1, element.acl().size());
    }

    @Test
    public void toYaml_whenConstraintsSpecified() {
      var policy = new JitGroupPolicy(
        "group",
        "Group",
        new AccessControlList(List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, 1))),
        Map.of(
          Policy.ConstraintClass.JOIN, List.of(new CelConstraint("join", "", List.of(), "true")),
          Policy.ConstraintClass.APPROVE, List.of(new CelConstraint("approve", "", List.of(), "true"))),
        List.of());
      var element = PolicyDocument.GroupElement.toYaml(policy);

      assertEquals(1, element.constraints().join().size());
      assertEquals("join", element.constraints().join().get(0).name());

      assertEquals(1, element.constraints().approve().size());
      assertEquals("approve", element.constraints().approve().get(0).name());
    }

    @Test
    public void toYaml_whenPrivilegesSpecified() {
      var policy = new JitGroupPolicy(
        "group",
        "Group",
        new AccessControlList(List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, 1))),
        Map.of(),
        List.of(new IamRoleBinding(new ProjectId("project-1"), new IamRole("roles/role-1"))));
      var element = PolicyDocument.GroupElement.toYaml(policy);

      assertEquals(1, element.privileges().iamRoleBindings().size());
      assertNull(element.privileges().iamRoleBindings().get(0).project());
      assertEquals("projects/project-1", element.privileges().iamRoleBindings().get(0).resource());
      assertEquals("roles/role-1", element.privileges().iamRoleBindings().get(0).role());
    }

    @Test
    public void toPolicy_whenAclIsNull() {
      var element = new PolicyDocument.GroupElement("group", "", null, null, null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertTrue(policy.isPresent());
      assertTrue(policy.get().accessControlList().get().entries().isEmpty());
    }

    @Test
    public void toPolicy_whenAclIsEmpty() {
      var element = new PolicyDocument.GroupElement("group", "", List.of(), null, null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertTrue(policy.isPresent());
      assertTrue(policy.get().accessControlList().isPresent());
      assertTrue(policy.get().accessControlList().get().entries().isEmpty());
    }

    @Test
    public void toPolicy_whenAclInvalid() {
      var element = new PolicyDocument.GroupElement(
        "group",
        "",
        List.of(new PolicyDocument.AccessControlEntryElement("invalid", "JOIN", null)),
        null,
        null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.ACL_INVALID_PRINCIPAL,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy_whenConstraintInvalid() {
      var element = new PolicyDocument.GroupElement(
        "group",
        "",
        List.of(),
        new PolicyDocument.ConstraintsElement(
          List.of(new PolicyDocument.ConstraintElement("invalid", null, null, null, null, null, null)),
          null
        ),
        null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.CONSTRAINT_INVALID_TYPE,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy_whenJoinConstraintsContainMultipleExpiryConstraints() {
      var element = new PolicyDocument.GroupElement(
        "group",
        "",
        List.of(),
        new PolicyDocument.ConstraintsElement(
          List.of(
            new PolicyDocument.ConstraintElement("expiry", null, null, "P1D", "P1D", null, null),
            new PolicyDocument.ConstraintElement("expiry", null, null, "P1D", "P1D", null, null)),
          null
        ),
        null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.CONSTRAINT_INVALID_EXPIRY,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy_whenApprovalConstraintsContainExpiryConstraint() {
      var element = new PolicyDocument.GroupElement(
        "group",
        "",
        List.of(),
        new PolicyDocument.ConstraintsElement(
          null,
          List.of(new PolicyDocument.ConstraintElement("expiry", null, null, "P1D", "P1D", null, null))),
        null);
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.CONSTRAINT_INVALID_EXPIRY,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy_whenBindingInvalid() {
      var element = new PolicyDocument.GroupElement(
        "group",
        "",
        List.of(),
        null,
        new PolicyDocument.PrivilegesElement(List.of(
          new PolicyDocument.IamRoleBindingElement("1", null, null, null, null)
        )));
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.PRIVILEGE_INVALID_RESOURCE_ID,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy() {
      var element = new PolicyDocument.GroupElement(
        "group",
        "",
        List.of(),
        null,
        new PolicyDocument.PrivilegesElement(List.of(
          new PolicyDocument.IamRoleBindingElement("project-1", null, "roles/viewer", "d", "c")
        )));
      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);

      assertTrue(policy.isPresent());
      assertEquals("group", policy.get().name());
      assertEquals(1, policy.get().privileges().size());
      assertEquals("d", policy.get().privileges().stream().findFirst().get().description());
    }
  }

  //---------------------------------------------------------------------------
  // AccessControlEntryElement.
  //---------------------------------------------------------------------------

  @Nested
  public static class AccessControlEntryElement {
    @Test
    public void toYaml_whenAllowMaskCombinesMultipleValues() {
      var ace = new AccessControlList.AllowedEntry(SAMPLE_USER, 15);
      var element = PolicyDocument.AccessControlEntryElement.toYaml(ace);

      assertEquals("user:" + SAMPLE_USER.email, element.principal());
      assertEquals("JOIN, APPROVE_OTHERS, APPROVE_SELF", element.allowedPermissions());
      assertNull(element.deniedPermissions());
    }

    @Test
    public void toYaml_whenDenyMaskCombinesMultipleValues() {
      var ace = new AccessControlList.DeniedEntry(SAMPLE_USER, 7);
      var element = PolicyDocument.AccessControlEntryElement.toYaml(ace);

      assertEquals("user:" + SAMPLE_USER.email, element.principal());
      assertEquals("JOIN, APPROVE_OTHERS", element.deniedPermissions());
      assertNull(element.allowedPermissions());
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "",
      "   ",
      "serviceAccount:x",
      "robot:x"
    })
    public void toPolicy_whenPrincipalInvalid(String principal) {
      var element = new PolicyDocument.AccessControlEntryElement(
        principal,
        "JOIN",
        "ALLOW");

      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);
      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.ACL_INVALID_PRINCIPAL,
        issues.issues().get(0).code());
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "",
      "sleep"
    })
    public void toPolicy_whenAccessMaskInvalid(String accessMask) {
      var element = new PolicyDocument.AccessControlEntryElement(
        "user:" + SAMPLE_USER.email,
        accessMask,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);
      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.ACL_INVALID_PERMISSION,
        issues.issues().get(0).code());
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "maybe",
      "ALLOW?"
    })
    public void toPolicy_whenEffectInvalid(String effect) {
      var element = new PolicyDocument.AccessControlEntryElement(
        "user:" + SAMPLE_USER.email,
        null,
        effect);

      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);
      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.ACL_INVALID_PERMISSION,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy() {
      var element = new PolicyDocument.AccessControlEntryElement(
        "user:" + SAMPLE_USER.email,
        "JOIN",
        null);

      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);
      assertEquals(SAMPLE_USER, policy.get().principal);
      assertEquals(PolicyPermission.JOIN.toMask(), policy.get().accessRights);
    }
  }

  //---------------------------------------------------------------------------
  // ConstraintElement.
  //---------------------------------------------------------------------------

  @Nested
  public static class ConstraintElement {
    @Test
    public void toYaml_whenFixedExpiry() {
      var constraint = new ExpiryConstraint(Duration.ofMinutes(5));
      var element = PolicyDocument.ConstraintElement.toYaml(constraint);

      assertEquals("expiry", element.type());
      assertNull(element.name());
      assertNull(element.displayName());
      assertEquals("PT5M", element.expiryMinDuration());
      assertEquals("PT5M", element.expiryMaxDuration());
      assertNull(element.celVariables());
      assertNull(element.celExpression());
    }

    @Test
    public void toYaml_whenUserDefinedExpiry() {
      var constraint = new ExpiryConstraint(Duration.ofMinutes(5), Duration.ofDays(3));
      var element = PolicyDocument.ConstraintElement.toYaml(constraint);

      assertEquals("expiry", element.type());
      assertNull(element.name());
      assertNull(element.displayName());
      assertEquals("PT5M", element.expiryMinDuration());
      assertEquals("PT72H", element.expiryMaxDuration());
      assertNull(element.celVariables());
      assertNull(element.celExpression());
    }

    @Test
    public void toYaml_whenCelExpressionWithoutVariables() {
      var constraint = new CelConstraint(
        "name",
        "display name",
        List.of(),
        "expression");
      var element = PolicyDocument.ConstraintElement.toYaml(constraint);

      assertEquals("expression", element.type());
      assertEquals("name", element.name());
      assertEquals("display name", element.displayName());
      assertNull(element.expiryMaxDuration());
      assertNull(element.expiryMinDuration());
      assertEquals("expression", element.celExpression());
      assertEquals(0, element.celVariables().size());
    }

    @Test
    public void toYaml_whenCelExpressionWithVariables() {
      var constraint = new CelConstraint(
        "name",
        "display name",
        List.of(new CelConstraint.BooleanVariable("b", "")),
        "expression");
      var element = PolicyDocument.ConstraintElement.toYaml(constraint);

      assertEquals("expression", element.type());
      assertEquals("name", element.name());
      assertEquals("display name", element.displayName());
      assertNull(element.expiryMaxDuration());
      assertNull(element.expiryMinDuration());
      assertEquals("expression", element.celExpression());
      assertEquals(1, element.celVariables().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "",
      "  void"
    })
    public void toPolicy_whenTypeInvalid(String type) {
      var element = new PolicyDocument.ConstraintElement(
        type,
        "name",
        "display name",
        null,
        null,
        null,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);
      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.CONSTRAINT_INVALID_TYPE,
        issues.issues().get(0).code());
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "",
      " P",
      "P-1D",
      "PT8D"
    })
    public void toPolicy_whenExpiryInvalid(String duration) {
      var element = new PolicyDocument.ConstraintElement(
        "Expiry",
        "name",
        "display name",
        duration,
        "PT5D",
        null,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);
      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.CONSTRAINT_INVALID_EXPIRY,
        issues.issues().get(0).code());
    }

    @ParameterizedTest
    @ValueSource(strings = {
      ""
    })
    public void toPolicy_whenExpressionInvalid(String expression) {
      var element = new PolicyDocument.ConstraintElement(
        "Expression",
        "name",
        "display name",
        null,
        null,
        expression,
        List.of());

      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);
      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.CONSTRAINT_INVALID_EXPRESSION,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy_whenExpressionVariableInvalid() {
      var element = new PolicyDocument.ConstraintElement(
        "Expression",
        "name",
        "display name",
        null,
        null,
        "expression",
        List.of(new PolicyDocument.CelVariableElement("invalid", "var", "", null, null)));

      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);
      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.CONSTRAINT_INVALID_VARIABLE_DECLARATION,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy_whenExpressionHasVariables() {
      var element = new PolicyDocument.ConstraintElement(
        "Expression",
        "name",
        "display name",
        null,
        null,
        "expression",
        List.of(new PolicyDocument.CelVariableElement("string", "var", "Var", null, null)));

      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);
      assertTrue(policy.isPresent());

      var constraint = (CelConstraint)policy.get();
      assertEquals("name", constraint.name());
      assertEquals("display name", constraint.displayName());
      assertEquals("expression", constraint.expression());
      assertEquals(1, constraint.variables().size());

      var variable = List.copyOf(constraint.variables()).get(0);
      assertEquals("var", variable.name());
      assertEquals("Var", variable.displayName());
    }

    @Test
    public void toPolicy_whenExpressionHasNoVariables() {
      var element = new PolicyDocument.ConstraintElement(
        "Expression",
        "name",
        "display name",
        null,
        null,
        "expression",
        null);

      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);
      assertTrue(policy.isPresent());

      var constraint = (CelConstraint)policy.get();
      assertEquals("name", constraint.name());
      assertEquals("display name", constraint.displayName());
      assertEquals("expression", constraint.expression());
      assertEquals(0, constraint.variables().size());
    }
  }

  //---------------------------------------------------------------------------
  // CelVariableElement.
  //---------------------------------------------------------------------------

  @Nested
  public static class CelVariableElement {
    @Test
    public void toYaml_whenTypeInvalid() {
      var variable = new CelConstraint.Variable("test", "test") {
        @Override
        protected @NotNull Property bind(@NotNull GenericJson json) {
          return null;
        }
      };

      assertThrows(
        UnsupportedOperationException.class,
        () -> PolicyDocument.CelVariableElement.toYaml(variable));
    }

    @Test
    public void toYaml_whenTypeIsString() {
      var variable = new CelConstraint.StringVariable("name", "display name", 1, 10);
      var element = PolicyDocument.CelVariableElement.toYaml(variable);

      assertEquals("string", element.type());
      assertEquals("name", element.name());
      assertEquals("display name", element.displayName());
      assertEquals(1, element.min());
      assertEquals(10, element.max());
    }

    @Test
    public void toYaml_whenTypeIsLong() {
      var variable = new CelConstraint.LongVariable("name", "display name", -1L, 1L);
      var element = PolicyDocument.CelVariableElement.toYaml(variable);

      assertEquals("int", element.type());
      assertEquals("name", element.name());
      assertEquals("display name", element.displayName());
      assertEquals(-1, element.min());
      assertEquals(1, element.max());
    }

    @Test
    public void toYaml_whenTypeIsBoolean() {
      var variable = new CelConstraint.BooleanVariable("name", "display name");
      var element = PolicyDocument.CelVariableElement.toYaml(variable);

      assertEquals("boolean", element.type());
      assertEquals("name", element.name());
      assertEquals("display name", element.displayName());
      assertNull(element.min());
      assertNull(element.max());
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "",
      "  void"
    })
    public void toPolicy_whenTypeInvalid(String type) {
      var element = new PolicyDocument.CelVariableElement(
        type,
        "name",
        "display name",
        null,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var policy = element.toPolicy(issues);
      assertFalse(policy.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.CONSTRAINT_INVALID_VARIABLE_DECLARATION,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy_whenString() {
      var element = new PolicyDocument.CelVariableElement(
        " String ",
        "name",
        "display name",
        1,
        10);

      var issues = new PolicyDocument.IssueCollection();
      var variable = (CelConstraint.StringVariable)element.toPolicy(issues).get();

      assertEquals("name", variable.name());
      assertEquals("display name", variable.displayName());
      assertEquals(1, variable.minLength());
      assertEquals(10, variable.maxLength());
    }

    @ParameterizedTest
    @ValueSource(strings = {"int", " INTEGER  "})
    public void toPolicy_whenInteger(String type) {
      var element = new PolicyDocument.CelVariableElement(
        type,
        "name",
        "display name",
        1,
        10);

      var issues = new PolicyDocument.IssueCollection();
      var variable = (CelConstraint.LongVariable)element.toPolicy(issues).get();

      assertEquals("name", variable.name());
      assertEquals("display name", variable.displayName());
      assertEquals(1, variable.minInclusive());
      assertEquals(10, variable.maxInclusive());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bool", " BOOLEAN  "})
    public void toPolicy_whenBoolean(String type) {
      var element = new PolicyDocument.CelVariableElement(
        type,
        "name",
        "display name",
        null,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var variable = (CelConstraint.BooleanVariable)element.toPolicy(issues).get();

      assertEquals("name", variable.name());
      assertEquals("display name", variable.displayName());
    }
  }


  //---------------------------------------------------------------------------
  // IamRoleBindingElement.
  //---------------------------------------------------------------------------

  @Nested
  public static class IamRoleBindingElement {
    @Test
    public void toYaml_whenTypeInvalid() {
      var binding = new IamRoleBinding(
        new ProjectId("project-1"),
        new IamRole("roles/viewer"),
        "description",
        "expression");

      var element = PolicyDocument.IamRoleBindingElement.toYaml(binding);
      assertNull(element.project());
      assertEquals("projects/project-1", element.resource());
      assertEquals("roles/viewer", element.role());
      assertEquals("description", element.description());
      assertEquals("expression", element.condition());
    }

    @Test
    public void toPolicy_whenProjectIdAndResourceIdEmpty() {
      var element = new PolicyDocument.IamRoleBindingElement(
        " ",
        " ",
        "roles/viewer",
        null,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var binding = element.toPolicy(issues);

      assertFalse(binding.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.PRIVILEGE_INVALID_RESOURCE_ID,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy_whenProjectIdSpecified() {
      var element = new PolicyDocument.IamRoleBindingElement(
        " ",
        "project-1",
        "roles/viewer",
        null,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var binding = element.toPolicy(issues);

      assertTrue(binding.isPresent());
      assertEquals(
        new ProjectId("project-1"),
        binding.get().resource());
    }

    @Test
    public void toPolicy_whenFolderIdSpecified() {
      var element = new PolicyDocument.IamRoleBindingElement(
        " ",
        "folders/1",
        "roles/viewer",
        null,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var binding = element.toPolicy(issues);

      assertTrue(binding.isPresent());
      assertEquals(
        new FolderId("1"),
        binding.get().resource());
    }

    @Test
    public void toPolicy_whenOrganizationIdSpecified() {
      var element = new PolicyDocument.IamRoleBindingElement(
        " ",
        "organizations/1",
        "roles/viewer",
        null,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var binding = element.toPolicy(issues);

      assertTrue(binding.isPresent());
      assertEquals(
        new OrganizationId("1"),
        binding.get().resource());
    }

    @Test
    public void toPolicy_whenProjectIdAndResourceIdSpecified() {
      var element = new PolicyDocument.IamRoleBindingElement(
        "project-1",
        "project-2",
        "roles/viewer",
        null,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var binding = element.toPolicy(issues);

      assertFalse(binding.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.PRIVILEGE_DUPLICATE_RESOURCE_ID,
        issues.issues().get(0).code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "folder/1", "projects/a/resource/b"})
    public void toPolicy_whenProjectIdInvalid(String project) {
      var element = new PolicyDocument.IamRoleBindingElement(
        project,
        null,
        "roles/viewer",
        null,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var binding = element.toPolicy(issues);

      assertFalse(binding.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.PRIVILEGE_INVALID_RESOURCE_ID,
        issues.issues().get(0).code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"billingAccounts/1", "projects/a/resource/b"})
    public void toPolicy_whenResourceIdInvalid(String project) {
      var element = new PolicyDocument.IamRoleBindingElement(
        null,
        project,
        "roles/viewer",
        null,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var binding = element.toPolicy(issues);

      assertFalse(binding.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.PRIVILEGE_INVALID_RESOURCE_ID,
        issues.issues().get(0).code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"roles/", ""})
    public void toPolicy_whenRoleInvalid(String role) {
      var element = new PolicyDocument.IamRoleBindingElement(
        "project-1",
        null,
        role,
        null,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var binding = element.toPolicy(issues);

      assertFalse(binding.isPresent());
      assertEquals(
        PolicyDocument.Issue.Code.PRIVILEGE_INVALID_ROLE,
        issues.issues().get(0).code());
    }

    @Test
    public void toPolicy_whenOptionalFieldsMissing() {
      var element = new PolicyDocument.IamRoleBindingElement(
        "project-1",
        null,
        "roles/viewer",
        null,
        null);

      var issues = new PolicyDocument.IssueCollection();
      var binding = element.toPolicy(issues);

      assertTrue(binding.isPresent());
      assertEquals("project-1", binding.get().resource().id());
      assertEquals("roles/viewer", binding.get().role().name());
      assertNull(binding.get().description());
      assertNull(binding.get().condition());
    }

    @Test
    public void toPolicy() {
      var element = new PolicyDocument.IamRoleBindingElement(
        "project-1",
        null,
        "roles/viewer",
        "description",
        "expression");

      var issues = new PolicyDocument.IssueCollection();
      var binding = element.toPolicy(issues);

      assertTrue(binding.isPresent());
      assertEquals("project-1", binding.get().resource().id());
      assertEquals("roles/viewer", binding.get().role().name());
      assertEquals("description", binding.get().description());
      assertEquals("expression", binding.get().condition());
    }
  }
}
