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

package com.google.solutions.jitaccess.catalog.legacy;

import com.google.api.services.cloudasset.v1.model.Binding;
import com.google.api.services.cloudasset.v1.model.Expr;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.solutions.jitaccess.apis.IamRole;
import com.google.solutions.jitaccess.apis.ProjectId;
import com.google.solutions.jitaccess.catalog.EventIds;
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.catalog.auth.GroupId;
import com.google.solutions.jitaccess.catalog.auth.UserClassId;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestLegacyPolicy {
  private static final Policy.Metadata METADATA = new Policy.Metadata("Asset Inventory", Instant.EPOCH);
  private static final ProjectId SAMPLE_PROJECT_1 = new ProjectId("project-1");
  private static final IamRole SAMPLE_ROLE_1 = new IamRole("roles/role-1");
  private static final IamRole UNACCEPTABLY_LONG_ROLE = new IamRole(
    "roles/" + new String(new char[LegacyPolicy.RolePolicy.NAME_MAX_LENGTH + 1]).replace('\0', 'a'));

  //---------------------------------------------------------------------------
  // accessControlList.
  //---------------------------------------------------------------------------

  @Test
  public void accessControlList_whenRootBindingsEmpty() {
    var policy = new LegacyPolicy(Duration.ofMinutes(1), "", "", List.of(), METADATA);
    assertFalse(policy.accessControlList().isEmpty());

    var aces = List.copyOf(policy.accessControlList().get().entries());
    assertEquals(1, aces.size());
    assertEquals(UserClassId.AUTHENTICATED_USERS, aces.get(0).principal);
    assertEquals(PolicyPermission.VIEW.toMask(), aces.get(0).accessRights);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "roles/viewer",
    "roles/iam.roleAdmin",
    "roles/browser"
  })
  public void accessControlList_whenRootBindingsGrantGetIamPolicy(String privilegedRole) {
    var policy = new LegacyPolicy(
      Duration.ofMinutes(1),
      "",
      "",
      List.of(
        new Binding()
          .setRole("roles/insignificant")
          .setMembers(List.of("user:insignificant")),
        new Binding()
          .setRole(privilegedRole)
          .setCondition(new Expr().setExpression("ignore"))
          .setMembers(List.of("user:insignificant")),
        new Binding()
          .setRole(privilegedRole)
          .setMembers(List.of("user:admin-1@example.com"))),
      METADATA);
    assertFalse(policy.accessControlList().isEmpty());

    var aces = List.copyOf(policy.accessControlList().get().entries());
    assertEquals(2, aces.size());

    assertEquals(new UserId("admin-1@example.com"), aces.get(0).principal);
    assertEquals(PolicyPermission.EXPORT.toMask(), aces.get(0).accessRights);

    assertEquals(UserClassId.AUTHENTICATED_USERS, aces.get(1).principal);
    assertEquals(PolicyPermission.VIEW.toMask(), aces.get(1).accessRights);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "roles/owner",
    "roles/resourcemanager.projectIamAdmin",
    "roles/resourcemanager.folderAdmin",
    "roles/resourcemanager.organizationAdmin"
  })
  public void accessControlList_whenRootBindingsGrantSetIamPolicy(String privilegedRole) {
    var policy = new LegacyPolicy(
      Duration.ofMinutes(1),
      "",
      "",
      List.of(
        new Binding()
          .setRole("roles/viewer")
          .setMembers(List.of("user:admin-1@example.com")),
        new Binding()
          .setRole(privilegedRole)
          .setMembers(List.of("user:admin-1@example.com"))),
      METADATA);
    assertFalse(policy.accessControlList().isEmpty());

    var aces = List.copyOf(policy.accessControlList().get().entries());
    assertEquals(3, aces.size());

    assertEquals(new UserId("admin-1@example.com"), aces.get(0).principal);
    assertEquals(PolicyPermission.EXPORT.toMask(), aces.get(0).accessRights);

    assertEquals(new UserId("admin-1@example.com"), aces.get(1).principal);
    assertEquals(PolicyPermission.RECONCILE.toMask(), aces.get(1).accessRights);

    assertEquals(UserClassId.AUTHENTICATED_USERS, aces.get(2).principal);
    assertEquals(PolicyPermission.VIEW.toMask(), aces.get(2).accessRights);
  }

  //---------------------------------------------------------------------------
  // constraints.
  //---------------------------------------------------------------------------

  @Test
  public void constraints() {
    var policy = new LegacyPolicy(Duration.ofMinutes(1), "\\d+", "hint", List.of(), METADATA);

    assertFalse(policy.constraints(Policy.ConstraintClass.JOIN).isEmpty());
    assertTrue(policy.constraints(Policy.ConstraintClass.APPROVE).isEmpty());

    var joinConstraints = List.copyOf(policy.constraints(Policy.ConstraintClass.JOIN));

    var expiryConstraint = assertInstanceOf(ExpiryConstraint.class, joinConstraints.get(0));
    assertTrue(expiryConstraint.isFixedDuration());
    assertEquals(Duration.ofMinutes(1), expiryConstraint.maxDuration());

    var justificationConstraint = assertInstanceOf(CelConstraint.class, joinConstraints.get(1));
    assertEquals(1, justificationConstraint.variables().size());
    assertEquals("input.justification.matches('\\\\d+')", justificationConstraint.expression());
    assertEquals("hint", List.copyOf(justificationConstraint.variables()).get(0).displayName());
  }

  //---------------------------------------------------------------------------
  // add.
  //---------------------------------------------------------------------------

  @Test
  public void add_whenSystemPolicy() {
    var policy = new LegacyPolicy(Duration.ofMinutes(1), "pattern", "hint", List.of(), METADATA);
    assertThrows(
      IllegalArgumentException.class,
      () -> policy.add(new SystemPolicy("sys", "")));
  }

  @Test
  public void add_whenProjectHasNoBindings() {
    var policy = new LegacyPolicy(Duration.ofMinutes(1), "pattern", "hint", List.of(), METADATA);

    var projectNumber = 12345;
    policy.add(new Project()
      .setName("projects/" + projectNumber)
      .setProjectId("project-1"),
      () -> List.of(),
      Mockito.mock(Logger.class));

    assertEquals(1, policy.systems().size());

    var projectPolicy = policy.system(LegacyPolicy.ProjectPolicy.createName(projectNumber));
    assertTrue(projectPolicy.isPresent());

    assertEquals("3039", projectPolicy.get().name());
    assertEquals("project-1", projectPolicy.get().displayName());
    assertEquals("Project project-1", projectPolicy.get().description());
    assertEquals(0, projectPolicy.get().groups().size());
  }

  @Test
  public void add_whenProjectHasEligibleBindingsForUnacceptablyLongRole() {
    var policy = new LegacyPolicy(Duration.ofMinutes(1), "pattern", "hint", List.of(), METADATA);

    var logger = Mockito.mock(Logger.class);
    var projectNumber = 12345;
    policy.add(new Project()
        .setName("projects/" + projectNumber)
        .setProjectId("project-1"),
      () -> List.of(
        new Binding()
          .setCondition(new Expr().setExpression("has({}.jitAccessConstraint)"))
          .setRole(UNACCEPTABLY_LONG_ROLE.toString())
          .setMembers(List.of("user:user@example.com"))),
      logger);

    assertEquals(1, policy.systems().size());

    var projectPolicy = policy.system(LegacyPolicy.ProjectPolicy.createName(projectNumber));
    assertTrue(projectPolicy.isPresent());

    assertEquals(0, projectPolicy.get().groups().size());

    verify(logger, times(1)).warn(
      eq(EventIds.MAP_LEGACY_ROLE_),
      anyString(),
      any(),
      any(),
      any());
  }

  @Test
  public void add_whenProjectHasMultipleBindingsForSameRole() {
    var policy = new LegacyPolicy(Duration.ofMinutes(1), "pattern", "hint", List.of(), METADATA);

    var projectNumber = 12345;
    policy.add(new Project()
        .setName("projects/" + projectNumber)
        .setProjectId("project-1"),
      () -> List.of(
        new Binding()
          .setCondition(new Expr().setExpression("has({}.jitAccessConstraint)"))
          .setRole(SAMPLE_ROLE_1.toString())
          .setMembers(List.of("user:user@example.com")),
        new Binding()
          .setCondition(new Expr().setExpression("has({}.multipartyapprovalconstraint)"))
          .setRole(SAMPLE_ROLE_1.toString())
          .setMembers(List.of("user:user@example.com"))),
      Mockito.mock(Logger.class));

    assertEquals(1, policy.systems().size());

    var projectPolicy = policy.system(LegacyPolicy.ProjectPolicy.createName(projectNumber));
    assertTrue(projectPolicy.isPresent());

    assertEquals("3039", projectPolicy.get().name());
    assertEquals("project-1", projectPolicy.get().displayName());
    assertEquals("Project project-1", projectPolicy.get().description());
    var group = List.copyOf(projectPolicy.get().groups()).get(0);

    assertEquals(1, group.privileges().size());
    assertEquals(2, group.accessControlList().get().entries().size());
  }

  @Test
  public void add_whenProjectHasMultipleBindingsForSameRoleWithDifferentConditions() {
    var policy = new LegacyPolicy(Duration.ofMinutes(1), "pattern", "hint", List.of(), METADATA);

    var projectNumber = 12345;
    policy.add(new Project()
        .setName("projects/" + projectNumber)
        .setProjectId("project-1"),
      () -> List.of(
        new Binding()
          .setCondition(new Expr().setExpression("has({}.jitAccessConstraint)"))
          .setRole(SAMPLE_ROLE_1.toString())
          .setMembers(List.of("user:user@example.com")),
        new Binding()
          .setCondition(new Expr().setExpression("has({}.jitAccessConstraint) && resource.name=='y'"))
          .setRole(SAMPLE_ROLE_1.toString())
          .setMembers(List.of("user:user@example.com"))),
      Mockito.mock(Logger.class));

    assertEquals(1, policy.systems().size());

    var projectPolicy = policy.system(LegacyPolicy.ProjectPolicy.createName(projectNumber));
    assertTrue(projectPolicy.isPresent());

    assertEquals("3039", projectPolicy.get().name());
    assertEquals("project-1", projectPolicy.get().displayName());
    assertEquals("Project project-1", projectPolicy.get().description());
    var group = List.copyOf(projectPolicy.get().groups()).get(0);
    assertEquals(1, group.accessControlList().get().entries().size());

    var iamRoleBinding = (IamRoleBinding)List.copyOf(group.privileges()).get(0);
    assertNull(iamRoleBinding.condition());
  }

  @Nested
  public static class ProjectPolicy {

    //---------------------------------------------------------------------------
    // createName.
    //---------------------------------------------------------------------------

    @Test
    public void createName() {
      assertEquals("ffffffffffff", LegacyPolicy.ProjectPolicy.createName(0xFFFFFFFFFFFFL));
      assertEquals("0", LegacyPolicy.ProjectPolicy.createName(0));
    }
  }

  @Nested
  public static class RolePolicy {

    //---------------------------------------------------------------------------
    // createName.
    //---------------------------------------------------------------------------

    @Test
    public void createName_whenPredefinedRole() {
      assertEquals(
        "accessapproval-approver",
        LegacyPolicy.RolePolicy.createName(new IamRole("roles/accessapproval.approver")));
      assertEquals(
        "composer-serviceagentv2ext",
        LegacyPolicy.RolePolicy.createName(new IamRole("roles/composer.ServiceAgentV2Ext")));
    }

    @Test
    public void createName_whenCustomProjectRole() {
      assertEquals(
        "p-mycompanyadmin",
        LegacyPolicy.RolePolicy.createName(new IamRole("projects/my-project/roles/myCompanyAdmin")));
    }

    @Test
    public void createName_whenCustomOrgRole() {
      assertEquals(
        "o-serviceaccount-impersonator",
        LegacyPolicy.RolePolicy.createName(new IamRole("organizations/123/roles/ServiceAccount.Impersonator")));
    }

    //---------------------------------------------------------------------------
    // fromBinding.
    //---------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
      "resource.name=='x'",
      "has({}.somethingElse)",
      "invalidJunk(;"
    })
    public void fromBinding_whenBindingHasUnrecognizedCondition(String condition) {
      var binding = new Binding()
        .setCondition(new Expr().setExpression(condition))
        .setRole(SAMPLE_ROLE_1.toString())
        .setMembers(List.of(
          "user:user@example.com"
        ));

      var role = LegacyPolicy.RolePolicy.fromBinding(SAMPLE_PROJECT_1, binding);
      assertFalse(role.isPresent());
    }

    @Test
    public void fromBinding_whenIamRoleNameTooLong() {
      var binding = new Binding()
        .setCondition(new Expr().setExpression("has({}.jitAccessConstraint)"))
        .setRole(UNACCEPTABLY_LONG_ROLE.toString())
        .setMembers(List.of(
          "user:user@example.com",
          "group:group@example.com",
          "domain:example.com",
          "serviceAccount:sa@example.gcp.gserviceaccount.com"
        ));

      assertThrows(
        IllegalArgumentException.class,
        () -> LegacyPolicy.RolePolicy.fromBinding(SAMPLE_PROJECT_1, binding));
    }

    @Test
    public void fromBinding_whenBindingIsJitEligible() {
      var binding = new Binding()
        .setCondition(new Expr().setExpression("has({}.jitAccessConstraint)"))
        .setRole(SAMPLE_ROLE_1.toString())
        .setMembers(List.of(
          "user:user@example.com",
          "group:group@example.com",
          "domain:example.com",
          "serviceAccount:sa@example.gcp.gserviceaccount.com"
          ));

      var role = LegacyPolicy.RolePolicy.fromBinding(SAMPLE_PROJECT_1, binding);
      assertTrue(role.isPresent());

      var aces = List.copyOf(role.get().accessControlList().get().entries());
      assertEquals(2, aces.size());

      assertEquals(new UserId("user@example.com"), aces.get(0).principal);
      assertEquals(
        PolicyPermission.toMask(EnumSet.of(PolicyPermission.JOIN, PolicyPermission.APPROVE_SELF)),
        aces.get(0).accessRights);

      assertEquals(new GroupId("group@example.com"), aces.get(1).principal);
      assertEquals(
        PolicyPermission.toMask(EnumSet.of(PolicyPermission.JOIN, PolicyPermission.APPROVE_SELF)),
        aces.get(1).accessRights);

      assertEquals(1, role.get().privileges().size());
      var privilege = (IamRoleBinding)List.copyOf(role.get().privileges()).get(0);
      assertEquals(SAMPLE_PROJECT_1, privilege.resource());
      assertEquals(SAMPLE_ROLE_1, privilege.role());
      assertNull(privilege.description());
      assertNull(privilege.condition());
    }

    @Test
    public void fromBinding_whenBindingIsMpaEligible() {
      var binding = new Binding()
        .setCondition(new Expr().setExpression("has({}.multipartyapprovalconstraint)"))
        .setRole(SAMPLE_ROLE_1.toString())
        .setMembers(List.of(
          "user:user@example.com",
          "group:group@example.com",
          "domain:example.com",
          "serviceAccount:sa@example.gcp.gserviceaccount.com"
        ));

      var role = LegacyPolicy.RolePolicy.fromBinding(SAMPLE_PROJECT_1, binding);
      assertTrue(role.isPresent());

      var aces = List.copyOf(role.get().accessControlList().get().entries());
      assertEquals(2, aces.size());

      assertEquals(new UserId("user@example.com"), aces.get(0).principal);
      assertEquals(
        PolicyPermission.toMask(EnumSet.of(PolicyPermission.JOIN, PolicyPermission.APPROVE_OTHERS)),
        aces.get(0).accessRights);

      assertEquals(new GroupId("group@example.com"), aces.get(1).principal);
      assertEquals(
        PolicyPermission.toMask(EnumSet.of(PolicyPermission.JOIN, PolicyPermission.APPROVE_OTHERS)),
        aces.get(1).accessRights);

      assertEquals(1, role.get().privileges().size());
      var privilege = (IamRoleBinding)List.copyOf(role.get().privileges()).get(0);
      assertEquals(SAMPLE_PROJECT_1, privilege.resource());
      assertEquals(SAMPLE_ROLE_1, privilege.role());
      assertNull(privilege.description());
      assertNull(privilege.condition());
    }

    @Test
    public void fromBinding_whenBindingIsJitEligibleWithResourceCondition() {
      var binding = new Binding()
        .setCondition(new Expr()
          .setTitle("title")
          .setExpression("has({}.jitAccessConstraint) && resource.name=='x'"))
        .setRole(SAMPLE_ROLE_1.toString())
        .setMembers(List.of(
          "user:user@example.com",
          "group:group@example.com",
          "domain:example.com",
          "serviceAccount:sa@example.gcp.gserviceaccount.com"
        ));

      assertThrows(
        UnsupportedOperationException.class,
        () -> LegacyPolicy.RolePolicy.fromBinding(SAMPLE_PROJECT_1, binding));
    }

    @Test
    public void fromBinding_whenBindingIsMpaEligibleWithResourceCondition() {
      var binding = new Binding()
        .setCondition(new Expr()
          .setTitle("title")
          .setExpression("has({}.multipartyapprovalconstraint) && resource.name=='x'"))
        .setRole(SAMPLE_ROLE_1.toString())
        .setMembers(List.of(
          "user:user@example.com",
          "group:group@example.com",
          "domain:example.com",
          "serviceAccount:sa@example.gcp.gserviceaccount.com"
        ));

      assertThrows(
        UnsupportedOperationException.class,
        () -> LegacyPolicy.RolePolicy.fromBinding(SAMPLE_PROJECT_1, binding));
    }

    //---------------------------------------------------------------------------
    // merge.
    //---------------------------------------------------------------------------

    @Test
    public void merge() {
      var mpa = LegacyPolicy.RolePolicy
        .fromBinding(
          SAMPLE_PROJECT_1,
          new Binding()
          .setCondition(new Expr()
            .setTitle("title")
            .setExpression("has({}.multipartyapprovalconstraint)"))
          .setRole(SAMPLE_ROLE_1.toString())
          .setMembers(List.of("user:user-1@example.com")))
        .get();

      var jit = LegacyPolicy.RolePolicy
        .fromBinding(
          SAMPLE_PROJECT_1,
          new Binding()
            .setCondition(new Expr()
              .setTitle("title")
              .setExpression("has({}.jitaccessconstraint)"))
            .setRole(SAMPLE_ROLE_1.toString())
            .setMembers(List.of("user:user-1@example.com", "user:user-2@example.com")))
        .get();

      var merged = LegacyPolicy.RolePolicy.merge(mpa, jit);

      assertEquals("role-1", merged.name());
      assertEquals(0, merged.constraints(Policy.ConstraintClass.JOIN).size());
      assertEquals(1, merged.privileges().size());

      var aces = List.copyOf(merged.accessControlList().get().entries());
      assertEquals(3, aces.size());

      assertEquals(new UserId("user-1@example.com"), aces.get(0).principal);
      assertEquals(PolicyPermission.JOIN.toMask() | PolicyPermission.APPROVE_OTHERS.toMask(), aces.get(0).accessRights);

      assertEquals(new UserId("user-1@example.com"), aces.get(1).principal);
      assertEquals(PolicyPermission.JOIN.toMask() | PolicyPermission.APPROVE_SELF.toMask(), aces.get(1).accessRights);

      assertEquals(new UserId("user-2@example.com"), aces.get(2).principal);
      assertEquals(PolicyPermission.JOIN.toMask() | PolicyPermission.APPROVE_SELF.toMask(), aces.get(2).accessRights);
    }
  }
}
