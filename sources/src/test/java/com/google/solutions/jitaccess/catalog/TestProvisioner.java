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

import com.google.api.services.cloudidentity.v1.model.EntityKey;
import com.google.api.services.cloudidentity.v1.model.Group;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.solutions.jitaccess.apis.IamRole;
import com.google.solutions.jitaccess.apis.ProjectId;
import com.google.solutions.jitaccess.apis.clients.*;
import com.google.solutions.jitaccess.catalog.auth.GroupId;
import com.google.solutions.jitaccess.catalog.auth.GroupMapping;
import com.google.solutions.jitaccess.catalog.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.AccessControlList;
import com.google.solutions.jitaccess.catalog.policy.IamRoleBinding;
import com.google.solutions.jitaccess.catalog.policy.JitGroupPolicy;
import com.google.solutions.jitaccess.catalog.policy.Privilege;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestProvisioner {
  private static final UserId SAMPLE_USER_1 = new UserId("user-1@example.com");
  private static final UserId SAMPLE_USER_2 = new UserId("user-2@example.com");
  private static final GroupId SAMPLE_GROUP = new GroupId("group@example.com");
  private static final ProjectId SAMPLE_PROJECT_1 = new ProjectId("project-1");
  private static final ProjectId SAMPLE_PROJECT_2 = new ProjectId("project-2");
  private static final IamRole SAMPLE_ROLE_1 = new IamRole("roles/role-1");
  private static final IamRole SAMPLE_ROLE_2 = new IamRole("roles/role-2");
  private static final IamRole SAMPLE_ROLE_3 = new IamRole("roles/role-3");


  //---------------------------------------------------------------------------
  // provisionMembership.
  //---------------------------------------------------------------------------

  @Test
  public void provisionMembership() throws Exception {
    var groupProvisioner = Mockito.mock(Provisioner.GroupProvisioner.class);
    when(groupProvisioner.provisionedGroupId(any()))
      .thenAnswer(a -> SAMPLE_GROUP);

    var iamProvisioner = Mockito.mock(Provisioner.IamProvisioner.class);

    var group = Policies.createJitGroupPolicy(
      "group",
      AccessControlList.EMPTY,
      Map.of(),
      List.of(
        new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_1),
        new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_2),
        Mockito.mock(Privilege.class)));

    var provisioner = new Provisioner(
      group.id().environment(),
      groupProvisioner,
      iamProvisioner);

    var expiry = Instant.now();
    provisioner.provisionMembership(
      group,
      SAMPLE_USER_1,
      expiry);

    verify(groupProvisioner, times(1))
      .provision(eq(group), eq(SAMPLE_USER_1), eq(expiry));
    verify(iamProvisioner, times(1))
      .provisionAccess(eq(new GroupId("group@example.com")), argThat(roles -> roles.size() == 2));
  }

  //---------------------------------------------------------------------------
  // reconcile.
  //---------------------------------------------------------------------------

  @Test
  public void reconcile_whenGroupNotProvisionedYet() throws Exception {
    var groupProvisioner = Mockito.mock(Provisioner.GroupProvisioner.class);
    when(groupProvisioner.provisionedGroupId(any()))
      .thenAnswer(a -> SAMPLE_GROUP);
    when(groupProvisioner.isProvisioned(eq(SAMPLE_GROUP)))
      .thenReturn(false);

    var group = Policies.createJitGroupPolicy(
      "group",
      AccessControlList.EMPTY,
      Map.of(),
      List.of());

    var iamProvisioner = Mockito.mock(Provisioner.IamProvisioner.class);

    var provisioner = new Provisioner(
      group.id().environment(),
      groupProvisioner,
      iamProvisioner);

    provisioner.reconcile(group);

    verify(iamProvisioner, times(0)).provisionAccess(
      any(),
      any());
  }

  @Test
  public void reconcile_whenGroupProvisioned() throws Exception {
    var groupProvisioner = Mockito.mock(Provisioner.GroupProvisioner.class);
    when(groupProvisioner.provisionedGroupId(any()))
      .thenAnswer(a -> SAMPLE_GROUP);
    when(groupProvisioner.isProvisioned(eq(SAMPLE_GROUP)))
      .thenReturn(true);

    var group = Policies.createJitGroupPolicy(
      "group",
      AccessControlList.EMPTY,
      Map.of(),
      List.of());

    var iamProvisioner = Mockito.mock(Provisioner.IamProvisioner.class);

    var provisioner = new Provisioner(
      group.id().environment(),
      groupProvisioner,
      iamProvisioner);

    provisioner.reconcile(group);

    verify(iamProvisioner, times(1)).provisionAccess(
      eq(SAMPLE_GROUP),
      any());
  }

  @Nested
  public static class GroupProvisioner {

    // -------------------------------------------------------------------------
    // provisionedGroupId.
    // -------------------------------------------------------------------------

    @Test
    public void provisionedGroupId() {

      var groupId = new JitGroupId("env-1", "system-1", "group-1");
      var groupPolicy = Mockito.mock(JitGroupPolicy.class);
      when(groupPolicy.id())
        .thenReturn(groupId);

      var mapping = Mockito.mock(GroupMapping.class);
      when(mapping.groupFromJitGroup(eq(groupId)))
        .thenReturn(new GroupId("mapped@example.com"));

      var provisioner = new Provisioner.GroupProvisioner(
        mapping,
        Mockito.mock(CloudIdentityGroupsClient.class),
        Mockito.mock(Logger.class));

      assertEquals(
        new GroupId("mapped@example.com"),
        provisioner.provisionedGroupId(groupPolicy.id()));
    }

    // -------------------------------------------------------------------------
    // isProvisioned.
    // -------------------------------------------------------------------------

    @Test
    public void isProvisioned_whenGroupNotFound() throws Exception {
      var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
      when(groupsClient
        .getGroup(eq(SAMPLE_GROUP)))
        .thenThrow(new ResourceNotFoundException("mock"));

      var logger = Mockito.mock(Logger.class);
      var provisioner = new Provisioner.GroupProvisioner(
        Mockito.mock(GroupMapping.class),
        groupsClient,
        logger);

      assertFalse(provisioner.isProvisioned(SAMPLE_GROUP));
    }

    @Test
    public void isProvisioned_whenGroupFound() throws Exception {
      var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
      when(groupsClient
        .getGroup(eq(SAMPLE_GROUP)))
        .thenReturn(new Group());

      var logger = Mockito.mock(Logger.class);
      var provisioner = new Provisioner.GroupProvisioner(
        Mockito.mock(GroupMapping.class),
        groupsClient,
        logger);

      assertTrue(provisioner.isProvisioned(SAMPLE_GROUP));
    }

    // -------------------------------------------------------------------------
    // provision.
    // -------------------------------------------------------------------------

    @Test
    public void provision_whenAccessDenied() throws Exception {
      var jitGroupId = new JitGroupId("env", "system", "group");

      var groupPolicy = Mockito.mock(JitGroupPolicy.class);
      when(groupPolicy.id())
        .thenReturn(jitGroupId);
      when(groupPolicy.description())
        .thenReturn("Test group");
      when(groupPolicy.privileges())
        .thenReturn(Set.of());

      var mapping = Mockito.mock(GroupMapping.class);
      when(mapping.groupFromJitGroup(eq(jitGroupId)))
        .thenReturn(SAMPLE_GROUP);

      var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
      when(groupsClient
        .createGroup(
          eq(SAMPLE_GROUP),
          eq(CloudIdentityGroupsClient.GroupType.Security),
          anyString(),
          any()))
        .thenThrow(new AccessDeniedException("mock"));

      var logger = Mockito.mock(Logger.class);
      var provisioner = new Provisioner.GroupProvisioner(
        mapping,
        groupsClient,
        logger);

      assertThrows(
        AccessDeniedException.class,
        () ->  provisioner.provision(groupPolicy, SAMPLE_USER_1, Instant.now()));

      verify(logger, times(1))
        .error(
          eq(EventIds.PROVISION_MEMBER),
          anyString(),
          any(AccessDeniedException.class));
    }

    @Test
    public void provision() throws Exception {
      var jitGroupId = new JitGroupId("env", "system", "group");

      var groupPolicy = Mockito.mock(JitGroupPolicy.class);
      when(groupPolicy.id())
        .thenReturn(jitGroupId);
      when(groupPolicy.description())
        .thenReturn("Test group");
      when(groupPolicy.privileges())
        .thenReturn(Set.of());

      var mapping = Mockito.mock(GroupMapping.class);
      when(mapping.groupFromJitGroup(eq(jitGroupId)))
        .thenReturn(SAMPLE_GROUP);

      var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
      when(groupsClient
        .createGroup(
          eq(SAMPLE_GROUP),
          eq(CloudIdentityGroupsClient.GroupType.Security),
          anyString(),
          any()))
        .thenReturn(new GroupKey("1"));
      when(groupsClient.getGroup(eq(SAMPLE_GROUP)))
        .thenReturn(new Group());

      var provisioner = new Provisioner.GroupProvisioner(
        mapping,
        groupsClient,
        Mockito.mock(Logger.class));

      var expiry = Instant.now();
      provisioner.provision(groupPolicy, SAMPLE_USER_1, expiry);

      verify(groupsClient, times(1)).createGroup(
        eq(SAMPLE_GROUP),
        eq(CloudIdentityGroupsClient.GroupType.Security),
        anyString(),
        eq("Test group"));
      verify(groupsClient, times(1)).addMembership(
        eq(new GroupKey("1")),
        eq(SAMPLE_USER_1),
        eq(expiry));
    }

    //---------------------------------------------------------------------------
    // provisionedGroups.
    //---------------------------------------------------------------------------

    @Test
    public void provisionedGroups() throws Exception {
      var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
      when(groupsClient.createSearchQueryForPrefix(eq("jit.env-1.")))
        .thenReturn("query");
      when(groupsClient.searchGroups(
        eq("query"),
        eq(false)))
        .thenReturn(List.of(
          // Invalid groups
          new Group().setGroupKey(new EntityKey().setId("jit.env-1.invalid@example.com")),
          new Group().setGroupKey(new EntityKey().setId("jit.env-1.system-1.group-1@invalid.example.com")),

          // Valid groups
          new Group().setGroupKey(new EntityKey().setId("jit.env-1.system-1.group-1@example.com")),
          new Group().setGroupKey(new EntityKey().setId("jit.env-1.system-1.group-2@example.com"))));

      var mapping = new GroupMapping("example.com");

      var provisioner = new Provisioner.GroupProvisioner(
        mapping,
        groupsClient,
        Mockito.mock(Logger.class));

      var groups = provisioner.provisionedGroups("env-1");
      assertEquals(2, groups.size());
      assertTrue(groups.contains(new JitGroupId("env-1", "system-1", "group-1")));
      assertTrue(groups.contains(new JitGroupId("env-1", "system-1", "group-2")));
    }
  }
  
  @Nested
  public static class IamProvisioner {


    // -------------------------------------------------------------------------
    // replaceBindingsForPrincipals.
    // -------------------------------------------------------------------------

    @Test
    public void replaceBindingsForPrincipals_whenExistingPoliciesHasObsoleteBindings() {
      var role1 = new Binding()
        .setRole("roles/role-1")
        .setMembers(new ArrayList<>(List.of(
          "user:" + SAMPLE_USER_1.email,
          "user:" + SAMPLE_USER_2.email)));
      var role2 = new Binding()
        .setRole("roles/role-1")
        .setMembers(new ArrayList<>(List.of(
          "user:" + SAMPLE_USER_1.email)));

      var policy = new Policy();
      policy.setBindings(new ArrayList<>(List.of(role1, role2)));

      Provisioner.IamProvisioner.replaceBindingsForPrincipals(
        policy,
        SAMPLE_USER_1,
        List.of(new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_3)));

      assertEquals(2, policy.getBindings().size());

      assertEquals("roles/role-1", policy.getBindings().get(0).getRole());
      assertEquals(1, policy.getBindings().get(0).getMembers().size());
      assertEquals("user:" + SAMPLE_USER_2.email, policy.getBindings().get(0).getMembers().get(0));

      assertEquals("roles/role-3", policy.getBindings().get(1).getRole());
      assertEquals(1, policy.getBindings().get(1).getMembers().size());
      assertEquals("user:" + SAMPLE_USER_1.email, policy.getBindings().get(1).getMembers().get(0));
    }

    @Test
    public void replaceBindingsForPrincipals_whenBindingHasCondition() {
      var policy = new Policy();
      policy.setBindings(new ArrayList<>());

      Provisioner.IamProvisioner.replaceBindingsForPrincipals(
        policy,
        SAMPLE_USER_1,
        List.of(new IamRoleBinding(
          SAMPLE_PROJECT_1,
          SAMPLE_ROLE_1,
          "description",
          "expression")));

      assertEquals(1, policy.getBindings().size());
      var binding = policy.getBindings().get(0);

      assertEquals("roles/role-1", binding.getRole());
      assertEquals(1, binding.getMembers().size());
      assertEquals("user:" + SAMPLE_USER_1.email, binding.getMembers().get(0));
      assertEquals("description", binding.getCondition().getTitle());
      assertEquals("expression", binding.getCondition().getExpression());
    }

    // -------------------------------------------------------------------------
    // provisionAccess.
    // -------------------------------------------------------------------------

    @Test
    public void provisionAccess_whenBindingsEmpty() throws Exception {
      var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
      when(groupsClient.getGroup(eq(SAMPLE_GROUP)))
        .thenReturn(new Group().setDescription("Test group"));

      var resourceManagerClient = Mockito.mock(ResourceManagerClient.class);

      var provisioner = new Provisioner.IamProvisioner(
        groupsClient,
        resourceManagerClient,
        Mockito.mock(Logger.class));

      provisioner.provisionAccess(
        SAMPLE_GROUP,
        Set.of());

      verify(resourceManagerClient, times(0)).modifyIamPolicy(any(), any(), any());
      verify(groupsClient, times(0)).patchGroup(any(), any());
    }

    @Test
    public void provisionAccess_whenBindingsCurrent() throws Exception {
      var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
      when(groupsClient.getGroup(eq(SAMPLE_GROUP)))
        .thenReturn(new Group().setDescription("Test group #d4c347b3"));

      var resourceManagerClient = Mockito.mock(ResourceManagerClient.class);

      var provisioner = new Provisioner.IamProvisioner(
        groupsClient,
        resourceManagerClient,
        Mockito.mock(Logger.class));

      provisioner.provisionAccess(
        SAMPLE_GROUP,
        Set.of(new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_1)));

      verify(resourceManagerClient, times(0)).modifyIamPolicy(any(), any(), any());
      verify(groupsClient, times(0)).patchGroup(any(), any());
    }

    @Test
    public void provisionAccess_whenBindingsChanged() throws Exception {
      var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
      when(groupsClient.getGroup(eq(SAMPLE_GROUP)))
        .thenReturn(new Group()
          .setName("1")
          .setDescription("Test group #d4c347b3"));

      var resourceManagerClient = Mockito.mock(ResourceManagerClient.class);

      var provisioner = new Provisioner.IamProvisioner(
        groupsClient,
        resourceManagerClient,
        Mockito.mock(Logger.class));

      provisioner.provisionAccess(
        SAMPLE_GROUP,
        Set.of(
          new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_1),
          new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_2),
          new IamRoleBinding(SAMPLE_PROJECT_2, SAMPLE_ROLE_2)));

      verify(resourceManagerClient, times(1))
        .modifyIamPolicy(eq(SAMPLE_PROJECT_1), any(), any());
      verify(resourceManagerClient, times(1))
        .modifyIamPolicy(eq(SAMPLE_PROJECT_2), any(), any());
      verify(groupsClient, times(1)).patchGroup(
        any(),
        eq("Test group #69092b7d"));
    }

    @Test
    public void provisionAccess_whenAccessDenied() throws Exception {
      var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
      when(groupsClient.getGroup(eq(SAMPLE_GROUP)))
        .thenReturn(new Group()
          .setName("1")
          .setDescription("Test group"));

      var resourceManagerClient = Mockito.mock(ResourceManagerClient.class);
      doThrow(new AccessDeniedException("mock"))
        .when(resourceManagerClient)
        .modifyIamPolicy(eq(SAMPLE_PROJECT_1), any(), any());

      var logger = Mockito.mock(Logger.class);
      var provisioner = new Provisioner.IamProvisioner(
        groupsClient,
        resourceManagerClient,
        logger);

      assertThrows(
        AccessDeniedException.class,
        () -> provisioner.provisionAccess(
          SAMPLE_GROUP,
          Set.of(
            new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_1))));

      verify(logger, times(1))
        .error(
          eq(EventIds.PROVISION_IAM_BINDINGS),
          anyString(),
          any(AccessDeniedException.class));
    }
  }

  @Nested
  public class IamBindingChecksum {
    // -------------------------------------------------------------------------
    // fromTaggedDescription.
    // -------------------------------------------------------------------------

    @Test
    public void fromTaggedDescription_whenDescriptionIsNull() {
      var checksum = Provisioner.IamBindingChecksum.fromTaggedDescription(null);

      assertEquals(Provisioner.IamBindingChecksum.ZERO, checksum);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "text", "###"})
    public void fromTaggedDescription_whenDescriptionDoesNotContainHash(String description) {
      var checksum = Provisioner.IamBindingChecksum.fromTaggedDescription(description);

      assertEquals(Provisioner.IamBindingChecksum.ZERO, checksum);
    }

    @Test
    public void fromTaggedDescription_whenDescriptionContainsHash() {
      var checksum = Provisioner.IamBindingChecksum.fromTaggedDescription("some text #1199aaff");

      assertNotEquals(Provisioner.IamBindingChecksum.ZERO, checksum);
      assertEquals("1199aaff", checksum.toString());
    }

    // -------------------------------------------------------------------------
    // toTaggedDescription.
    // -------------------------------------------------------------------------

    @Test
    public void toTaggedDescription_whenDescriptionIsNull() {
      assertEquals(
        "#000000cc",
        new Provisioner.IamBindingChecksum(0xCC).toTaggedDescription(null));
    }

    @Test
    public void toTaggedDescription_whenDescriptionEmpty() {
      assertEquals(
        "#000000cc",
        new Provisioner.IamBindingChecksum(0xCC).toTaggedDescription(""));
    }

    @Test
    public void toTaggedDescription_whenDescriptionNotEmpty() {
      assertEquals(
        "some text #000000cc",
        new Provisioner.IamBindingChecksum(0xCC).toTaggedDescription("some text"));
    }

    @Test
    public void toTaggedDescription_whenDescriptionIsTagged() {
      assertEquals(
        "some text #000000cc",
        new Provisioner.IamBindingChecksum(0xCC).toTaggedDescription("some text #aa11"));
    }

    // -------------------------------------------------------------------------
    // fromBindings.
    // -------------------------------------------------------------------------

    @Test
    public void fromBindings() {
      var checksum = Provisioner.IamBindingChecksum.fromBindings(
        Set.of(
          new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_1),
          new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_1, "description", "expression"),
          new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_2, "", "expression")));
      assertEquals("06b00b88", checksum.toString());
    }
  }
}
