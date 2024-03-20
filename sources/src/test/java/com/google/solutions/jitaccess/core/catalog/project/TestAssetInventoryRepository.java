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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.api.services.cloudasset.v1.model.Binding;
import com.google.api.services.cloudasset.v1.model.Expr;
import com.google.api.services.cloudasset.v1.model.Policy;
import com.google.api.services.cloudasset.v1.model.PolicyInfo;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Member;
import com.google.solutions.jitaccess.cel.TemporaryIamCondition;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.clients.AssetInventoryClient;
import com.google.solutions.jitaccess.core.clients.DirectoryGroupsClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class TestAssetInventoryRepository {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");
  private static final String JIT_CONDITION = "has({}.jitAccessConstraint)";
  private static final String MPA_CONDITION = "has({}.multiPartyApprovalConstraint)";

  private class SynchronousExecutor implements Executor {

    @Override
    public void execute(Runnable command) {
      command.run();
    }
  }

  //---------------------------------------------------------------------------
  // findProjectBindings.
  //---------------------------------------------------------------------------

  @Test
  public void whenEffectiveIamPoliciesEmpty_ThenFindProjectBindingsReturnsEmptyList() throws Exception {
    var caiClient = Mockito.mock(AssetInventoryClient.class);
    when(caiClient
      .getEffectiveIamPolicies(
        eq("organization/0"),
        eq(SAMPLE_PROJECT)))
      .thenReturn(List.of());

    var repository = new AssetInventoryRepository(
      new SynchronousExecutor(),
      Mockito.mock(DirectoryGroupsClient.class),
      caiClient,
      new AssetInventoryRepository.Options("organization/0"));

    var bindings = repository.findProjectBindings(
      SAMPLE_USER,
      SAMPLE_PROJECT);

    assertNotNull(bindings);
    assertIterableEquals(List.of(), bindings);
  }

  @Test
  public void whenEffectiveIamPoliciesContainsInapplicableBindings_ThenFindProjectBindingsReturnsEmptyList() throws Exception {
    var bindingForOtherUser = new Binding()
      .setRole("roles/for-other-user")
      .setMembers(List.of("user:other@example.com"));
    var bindingForServiceAccount = new Binding()
      .setRole("roles/for-service-account")
      .setMembers(List.of("serviceAccount:other@example.iam.gserviceaccount.com"));
    var permanentBindingForGroup = new Binding()
      .setRole("roles/for-group")
      .setMembers(List.of("group:other@example.com"));

    var caiClient = Mockito.mock(AssetInventoryClient.class);
    when(caiClient
      .getEffectiveIamPolicies(
        eq("organization/0"),
        eq(SAMPLE_PROJECT)))
      .thenReturn(List.of(
        new PolicyInfo()
          .setAttachedResource(SAMPLE_PROJECT.path())
          .setPolicy(new Policy()
            .setBindings(List.of(
              bindingForOtherUser,
              bindingForServiceAccount,
              permanentBindingForGroup)))));

    var repository = new AssetInventoryRepository(
      new SynchronousExecutor(),
      Mockito.mock(DirectoryGroupsClient.class),
      caiClient,
      new AssetInventoryRepository.Options("organization/0"));

    var bindings = repository.findProjectBindings(
      SAMPLE_USER,
      SAMPLE_PROJECT);

    assertNotNull(bindings);
    assertIterableEquals(List.of(), bindings);
  }

  @Test
  public void whenEffectiveIamPoliciesContainsBindingsForUser_ThenFindProjectBindingsReturnsList() throws Exception {
    var bindingForOtherUser = new Binding()
      .setRole("roles/for-other-user")
      .setMembers(List.of("user:other@example.com"));
    var bindingForServiceAccount = new Binding()
      .setRole("roles/for-service-account")
      .setMembers(List.of("serviceAccount:other@example.iam.gserviceaccount.com"));
    var permanentBindingForUser = new Binding()
      .setRole("roles/for-user-permanent")
      .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var conditionalBindingForUser = new Binding()
      .setRole("roles/for-user-conditional")
      .setCondition(new Expr().setExpression("true"))
      .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));

    var caiClient = Mockito.mock(AssetInventoryClient.class);
    when(caiClient
      .getEffectiveIamPolicies(
        eq("organization/0"),
        eq(SAMPLE_PROJECT)))
      .thenReturn(List.of(
        new PolicyInfo()
          .setAttachedResource(SAMPLE_PROJECT.path())
          .setPolicy(new Policy()
            .setBindings(List.of(
              bindingForOtherUser,
              bindingForServiceAccount,
              permanentBindingForUser,
              conditionalBindingForUser)))));

    var repository = new AssetInventoryRepository(
      new SynchronousExecutor(),
      Mockito.mock(DirectoryGroupsClient.class),
      caiClient,
      new AssetInventoryRepository.Options("organization/0"));

    var bindings = repository.findProjectBindings(
      SAMPLE_USER,
      SAMPLE_PROJECT);

    assertNotNull(bindings);
    assertIterableEquals(
      List.of(
        "roles/for-user-permanent",
        "roles/for-user-conditional"
      ),
      bindings.stream().map(Binding::getRole).collect(Collectors.toList()));
  }

  @Test
  public void whenEffectiveIamPoliciesContainsBindingsForGroup_ThenFindProjectBindingsReturnsList() throws Exception {
    var bindingForGroup1 = new Binding()
      .setRole("roles/for-group-1")
      .setMembers(List.of("group:group-1@example.com"));
    var bindingForGroup2 = new Binding()
      .setRole("roles/for-group-2")
      .setMembers(List.of("group:group-2@example.com"));
    var bindingForOtherGroup = new Binding()
      .setRole("roles/for-other-group")
      .setMembers(List.of("group:other-group@example.com"));

    var groupsClient = Mockito.mock(DirectoryGroupsClient.class);
    when(groupsClient
      .listDirectGroupMemberships(eq(SAMPLE_USER)))
      .thenReturn(List.of(
        new Group().setEmail("group-1@example.com"),
        new Group().setEmail("group-2@example.com"),
        new Group().setEmail("junk@example.com")));

    var caiClient = Mockito.mock(AssetInventoryClient.class);
    when(caiClient
      .getEffectiveIamPolicies(
        eq("organization/0"),
        eq(SAMPLE_PROJECT)))
      .thenReturn(List.of(
        new PolicyInfo()
          .setAttachedResource("organization/0")
          .setPolicy(new Policy()
            .setBindings(List.of(
              bindingForGroup1))),
        new PolicyInfo()
          .setAttachedResource(SAMPLE_PROJECT.path())
          .setPolicy(new Policy()
            .setBindings(List.of(
              bindingForGroup2,
              bindingForOtherGroup)))));

    var repository = new AssetInventoryRepository(
      new SynchronousExecutor(),
      groupsClient,
      caiClient,
      new AssetInventoryRepository.Options("organization/0"));

    var bindings = repository.findProjectBindings(
      SAMPLE_USER,
      SAMPLE_PROJECT);

    assertNotNull(bindings);
    assertIterableEquals(
      List.of(
        "roles/for-group-1",
        "roles/for-group-2"
      ),
      bindings.stream().map(Binding::getRole).collect(Collectors.toList()));
  }

  //---------------------------------------------------------------------------
  // findEntitlements.
  //---------------------------------------------------------------------------

  @Test
  public void whenEffectiveIamPoliciesContainEligibleBindings_ThenFindEntitlementsReturnsList() throws Exception {
    var jitBindingForUser = new Binding()
      .setRole("roles/for-user")
      .setCondition(new Expr().setExpression(JIT_CONDITION))
      .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var mpaBindingForUser = new Binding()
      .setRole("roles/for-user")
      .setCondition(new Expr().setExpression(MPA_CONDITION))
      .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));

    var caiClient = Mockito.mock(AssetInventoryClient.class);
    when(caiClient
      .getEffectiveIamPolicies(
        eq("organization/0"),
        eq(SAMPLE_PROJECT)))
      .thenReturn(List.of(
        new PolicyInfo()
          .setAttachedResource(SAMPLE_PROJECT.path())
          .setPolicy(new Policy()
            .setBindings(List.of(jitBindingForUser, mpaBindingForUser)))));

    var repository = new AssetInventoryRepository(
      new SynchronousExecutor(),
      Mockito.mock(DirectoryGroupsClient.class),
      caiClient,
      new AssetInventoryRepository.Options("organization/0"));

    //
    // Only JIT binding is retained.
    //

    var entitlements = repository.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT,
      EnumSet.of(ActivationType.JIT, ActivationType.MPA));

    assertTrue(entitlements.currentActivations().isEmpty());
    assertTrue(entitlements.expiredActivations().isEmpty());

    assertIterableEquals(
      List.of("roles/for-user"),
      entitlements.available().stream().map(e -> e.id().roleBinding().role()).collect(Collectors.toList()));
    var jitEntitlement = entitlements.available().first();
    assertEquals(ActivationType.JIT, jitEntitlement.activationType());
  }

  @Test
  public void whenEffectiveIamPoliciesContainsExpiredActivation_ThenFindEntitlementsReturnsList() throws Exception {
    var jitBindingForUser = new Binding()
      .setRole("roles/for-user")
      .setCondition(new Expr().setExpression(JIT_CONDITION))
      .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var expiredActivationForUser1 = new Binding()
      .setRole("roles/for-user")
      .setCondition(new Expr()
        .setTitle(JitConstraints.ACTIVATION_CONDITION_TITLE)
        .setExpression(new TemporaryIamCondition(
          Instant.now().minus(2, ChronoUnit.HOURS),
          Duration.ofHours(1)).toString()))
      .setMembers(List.of("user:" + SAMPLE_USER.email));
    var expiredActivationForUser2 = new Binding()
      .setRole("roles/for-user")
      .setCondition(new Expr()
        .setTitle(JitConstraints.ACTIVATION_CONDITION_TITLE)
        .setExpression(new TemporaryIamCondition(
          Instant.now().minus(2, ChronoUnit.DAYS),
          Duration.ofHours(1)).toString()))
      .setMembers(List.of("user:" + SAMPLE_USER.email));

    var caiClient = Mockito.mock(AssetInventoryClient.class);
    when(caiClient
      .getEffectiveIamPolicies(
        eq("organization/0"),
        eq(SAMPLE_PROJECT)))
      .thenReturn(List.of(
        new PolicyInfo()
          .setAttachedResource(SAMPLE_PROJECT.path())
          .setPolicy(new Policy()
            .setBindings(List.of(
              jitBindingForUser,
              expiredActivationForUser1,
              expiredActivationForUser2)))));

    var repository = new AssetInventoryRepository(
      new SynchronousExecutor(),
      Mockito.mock(DirectoryGroupsClient.class),
      caiClient,
      new AssetInventoryRepository.Options("organization/0"));

    var entitlements = repository.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT,
      EnumSet.of(ActivationType.JIT, ActivationType.MPA));

    assertEquals(1, entitlements.available().size());

    var entitlement = entitlements.available().first();
    assertEquals(ActivationType.JIT, entitlement.activationType());

    assertTrue(entitlements.currentActivations().isEmpty());
    assertTrue(entitlements.expiredActivations().containsKey(entitlement.id()));
  }

  @Test
  public void whenEffectiveIamPoliciesContainsActivation_ThenFindEntitlementsReturnsList() throws Exception {
    var jitBindingForUser = new Binding()
      .setRole("roles/for-user")
      .setCondition(new Expr().setExpression(JIT_CONDITION))
      .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var expiredActivationForUser = new Binding()
      .setRole("roles/for-user")
      .setCondition(new Expr()
        .setTitle(JitConstraints.ACTIVATION_CONDITION_TITLE)
        .setExpression(new TemporaryIamCondition(
          Instant.now().minus(1, ChronoUnit.HOURS),
          Instant.now().plus(1, ChronoUnit.HOURS)).toString()))
      .setMembers(List.of("user:" + SAMPLE_USER.email));

    var caiClient = Mockito.mock(AssetInventoryClient.class);
    when(caiClient
      .getEffectiveIamPolicies(
        eq("organization/0"),
        eq(SAMPLE_PROJECT)))
      .thenReturn(List.of(
        new PolicyInfo()
          .setAttachedResource(SAMPLE_PROJECT.path())
          .setPolicy(new Policy()
            .setBindings(List.of(jitBindingForUser, expiredActivationForUser)))));

    var repository = new AssetInventoryRepository(
      new SynchronousExecutor(),
      Mockito.mock(DirectoryGroupsClient.class),
      caiClient,
      new AssetInventoryRepository.Options("organization/0"));

    var entitlements = repository.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT,
      EnumSet.of(ActivationType.JIT, ActivationType.MPA));

    assertEquals(1, entitlements.available().size());

    var entitlement = entitlements.available().first();
    assertEquals(ActivationType.JIT, entitlement.activationType());

    assertTrue(entitlements.currentActivations().containsKey(entitlement.id()));
    assertTrue(entitlements.expiredActivations().isEmpty());
  }

  //---------------------------------------------------------------------------
  // findEntitlementHolders.
  //---------------------------------------------------------------------------

  @Test
  public void whenEffectiveIamPoliciesOnlyContainInapplicableBindings_ThenFindEntitlementHoldersReturnsEmptyList() throws Exception {
    var otherBinding1 = new Binding()
      .setRole("roles/other-1")
      .setCondition(new Expr().setExpression(MPA_CONDITION))
      .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var otherBinding2 = new Binding()
      .setRole("roles/role-1")
      .setCondition(new Expr().setExpression(JIT_CONDITION))
      .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));

    var caiClient = Mockito.mock(AssetInventoryClient.class);
    when(caiClient
      .getEffectiveIamPolicies(
        eq("organization/0"),
        eq(SAMPLE_PROJECT)))
      .thenReturn(List.of(
        new PolicyInfo()
          .setAttachedResource(SAMPLE_PROJECT.path())
          .setPolicy(new Policy()
            .setBindings(List.of(otherBinding1, otherBinding2)))));

    var repository = new AssetInventoryRepository(
      new SynchronousExecutor(),
      Mockito.mock(DirectoryGroupsClient.class),
      caiClient,
      new AssetInventoryRepository.Options("organization/0"));

    var holders = repository.findEntitlementHolders(
      new ProjectRole(new RoleBinding(SAMPLE_PROJECT, "roles/role-1")),
      ActivationType.MPA);

    assertNotNull(holders);
    assertTrue(holders.isEmpty());
  }

  @Test
  public void whenEffectiveIamPoliciesContainUsers_ThenFindEntitlementHoldersReturnsList() throws Exception {
    var role = new RoleBinding(SAMPLE_PROJECT, "roles/role-1");

    var caiClient = Mockito.mock(AssetInventoryClient.class);
    when(caiClient
      .getEffectiveIamPolicies(
        eq("organization/0"),
        eq(SAMPLE_PROJECT)))
      .thenReturn(List.of(
        new PolicyInfo()
          .setAttachedResource("organization/0")
          .setPolicy(new Policy()
            .setBindings(List.of(new Binding()
              .setRole(role.role())
              .setCondition(new Expr().setExpression(MPA_CONDITION))
              .setMembers(List.of("user:user-1@example.com", "user:user-2@example.com"))))),
        new PolicyInfo()
          .setAttachedResource(SAMPLE_PROJECT.path())
          .setPolicy(new Policy()
            .setBindings(List.of(new Binding()
              .setRole(role.role())
              .setCondition(new Expr().setExpression(MPA_CONDITION))
              .setMembers(List.of("user:user-2@example.com")))))));

    var repository = new AssetInventoryRepository(
      new SynchronousExecutor(),
      Mockito.mock(DirectoryGroupsClient.class),
      caiClient,
      new AssetInventoryRepository.Options("organization/0"));

    var holders = repository.findEntitlementHolders(
      new ProjectRole(role),
      ActivationType.MPA);

    assertNotNull(holders);
    assertEquals(
      Set.of(new UserId("user-1@example.com"), new UserId("user-2@example.com")),
      holders);
  }

  @Test
  public void whenEffectiveIamPoliciesContainsGroups_ThenFindEntitlementHoldersReturnsList() throws Exception {
    var role = new RoleBinding(SAMPLE_PROJECT, "roles/role-1");

    var groupBinding = new Binding()
      .setRole(role.role())
      .setCondition(new Expr().setExpression(MPA_CONDITION))
      .setMembers(List.of("group:group@example.com"));
    var unavailableGroupBinding = new Binding()
      .setRole(role.role())
      .setCondition(new Expr().setExpression(MPA_CONDITION))
      .setMembers(List.of("group:unavailable-group@example.com"));

    var groupsClient = Mockito.mock(DirectoryGroupsClient.class);
    when(groupsClient
      .listDirectGroupMembers(eq("group@example.com")))
      .thenReturn(List.of(
        new Member().setEmail("user-1@example.com"),
        new Member().setEmail("user-2@example.com")));
    when(groupsClient
      .listDirectGroupMembers(eq("unavailable-group@example.com")))
      .thenThrow(new AccessDeniedException("mock"));

    var caiClient = Mockito.mock(AssetInventoryClient.class);
    when(caiClient
      .getEffectiveIamPolicies(
        eq("organization/0"),
        eq(SAMPLE_PROJECT)))
      .thenReturn(List.of(
        new PolicyInfo()
          .setAttachedResource(SAMPLE_PROJECT.path())
          .setPolicy(new Policy()
            .setBindings(List.of(groupBinding, unavailableGroupBinding)))));

    var repository = new AssetInventoryRepository(
      new SynchronousExecutor(),
      groupsClient,
      caiClient,
      new AssetInventoryRepository.Options("organization/0"));

    var holders = repository.findEntitlementHolders(
      new ProjectRole(role),
      ActivationType.MPA);

    assertNotNull(holders);
    assertEquals(
      Set.of(new UserId("user-1@example.com"), new UserId("user-2@example.com")),
      holders);
  }
}
