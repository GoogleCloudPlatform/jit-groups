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
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.ExternalApproval;
import com.google.solutions.jitaccess.core.catalog.PeerApproval;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege;
import com.google.solutions.jitaccess.core.catalog.SelfApproval;
import com.google.solutions.jitaccess.core.clients.AssetInventoryClient;
import com.google.solutions.jitaccess.core.clients.DirectoryGroupsClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
  private static final UserEmail SAMPLE_USER = new UserEmail("user-1@example.com");
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");
  private static final String SELF_APPROVAL_CONDITION = "has({}.jitAccessConstraint)";
  private static final String PEER_CONDITION = "has({}.multiPartyApprovalConstraint.topic)";
  private static final String REQUESTER_CONDITION = "has({}.externalApprovalConstraint.topic)";
  private static final String REVIEWER_CONDITION = "has({}.reviewerPrivilege.topic)";
  private static final String PEER_CONDITION_OTHER_TOPIC = "has({}.multiPartyApprovalConstraint.other_topic)";
  private static final String REQUESTER_CONDITION_OTHER_TOPIC = "has({}.externalApprovalConstraint.other_topic)";
  private static final String REVIEWER_CONDITION_OTHER_TOPIC = "has({}.reviewerPrivilege.other_topic)";
  private static final String PEER_CONDITION_NO_TOPIC = "has({}.multiPartyApprovalConstraint)";
  private static final String REQUESTER_CONDITION_NO_TOPIC = "has({}.externalApprovalConstraint)";
  private static final String REVIEWER_CONDITION_NO_TOPIC = "has({}.reviewerPrivilege)";

  private class SynchronousExecutor implements Executor {

    @Override
    public void execute(Runnable command) {
      command.run();
    }
  }

  private AssetInventoryClient setupTestBindings() throws Exception {
    var jitBindingForUser = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr().setExpression(SELF_APPROVAL_CONDITION))
        .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var peerBindingForUser = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr().setExpression(PEER_CONDITION))
        .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var peerBindingOtherTopic = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr().setExpression(PEER_CONDITION_OTHER_TOPIC))
        .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var peerBindingNoTopic = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr().setExpression(PEER_CONDITION_NO_TOPIC))
        .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var externalBindingForUser = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr().setExpression(REQUESTER_CONDITION))
        .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var externalBindingOtherTopic = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr().setExpression(REQUESTER_CONDITION_OTHER_TOPIC))
        .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var externalBindingNoTopic = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr().setExpression(REQUESTER_CONDITION_NO_TOPIC))
        .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var reviewerBindingForUser = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr().setExpression(REVIEWER_CONDITION))
        .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var reviewerBindingOtherTopic = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr().setExpression(REVIEWER_CONDITION_OTHER_TOPIC))
        .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var reviewerBindingNoTopic = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr().setExpression(REVIEWER_CONDITION_NO_TOPIC))
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
                    .setBindings(
                        List.of(jitBindingForUser,
                            peerBindingForUser,
                            peerBindingOtherTopic,
                            peerBindingNoTopic,
                            externalBindingForUser,
                            externalBindingOtherTopic,
                            externalBindingNoTopic,
                            reviewerBindingForUser,
                            reviewerBindingOtherTopic,
                            reviewerBindingNoTopic)))));
    return caiClient;
  }

  // ---------------------------------------------------------------------------
  // findProjectBindings.
  // ---------------------------------------------------------------------------

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
  public void whenEffectiveIamPoliciesContainsInapplicableBindings_ThenFindProjectBindingsReturnsEmptyList()
      throws Exception {
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
  public void whenEffectiveIamPoliciesContainsBindingsForUser_ThenFindProjectBindingsReturnsList()
      throws Exception {
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
            "roles/for-user-conditional"),
        bindings.stream().map(Binding::getRole).collect(Collectors.toList()));
  }

  @Test
  public void whenEffectiveIamPoliciesContainsBindingsForGroup_ThenFindProjectBindingsReturnsList()
      throws Exception {
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
            "roles/for-group-2"),
        bindings.stream().map(Binding::getRole).collect(Collectors.toList()));
  }

  // ---------------------------------------------------------------------------
  // findRequesterPrivileges.
  // ---------------------------------------------------------------------------

  @Test
  public void whenEffectiveIamPoliciesContainEligibleSelfApprovalBinding_ThenFindRequesterPrivilegesReturnsList()
      throws Exception {
    var caiClient = setupTestBindings();

    var repository = new AssetInventoryRepository(
        new SynchronousExecutor(),
        Mockito.mock(DirectoryGroupsClient.class),
        caiClient,
        new AssetInventoryRepository.Options("organization/0"));

    var privileges = repository.findRequesterPrivileges(
        SAMPLE_USER,
        SAMPLE_PROJECT,
        Set.of(new SelfApproval()),
        EnumSet.of(RequesterPrivilege.Status.INACTIVE));

    assertIterableEquals(
        List.of("roles/for-user"),
        privileges.available().stream().map(e -> e.id().roleBinding().role())
            .collect(Collectors.toList()));
    var selfApprovalPrivilege = privileges.available().first();
    assertEquals(new SelfApproval().name(), selfApprovalPrivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, selfApprovalPrivilege.status());
  }

  @Test
  public void whenEffectiveIamPoliciesContainEligiblePeerApprovalBinding_ThenFindRequesterPrivilegesReturnsList()
      throws Exception {
    var caiClient = setupTestBindings();

    var repository = new AssetInventoryRepository(
        new SynchronousExecutor(),
        Mockito.mock(DirectoryGroupsClient.class),
        caiClient,
        new AssetInventoryRepository.Options("organization/0"));

    var privileges = repository.findRequesterPrivileges(
        SAMPLE_USER,
        SAMPLE_PROJECT,
        Set.of(new PeerApproval("topic")),
        EnumSet.of(RequesterPrivilege.Status.INACTIVE));

    assertIterableEquals(
        List.of("roles/for-user"),
        privileges.available().stream().map(e -> e.id().roleBinding().role())
            .collect(Collectors.toList()));
    var peerApprovalPrivilege = privileges.available().first();
    assertEquals(new PeerApproval("topic").name(), peerApprovalPrivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, peerApprovalPrivilege.status());
  }

  @Test
  public void whenEffectiveIamPoliciesContainEligiblePeerApprovalBindingWithOtherTopic_ThenFindRequesterPrivilegesReturnsList()
      throws Exception {
    var caiClient = setupTestBindings();

    var repository = new AssetInventoryRepository(
        new SynchronousExecutor(),
        Mockito.mock(DirectoryGroupsClient.class),
        caiClient,
        new AssetInventoryRepository.Options("organization/0"));

    var privileges = repository.findRequesterPrivileges(
        SAMPLE_USER,
        SAMPLE_PROJECT,
        Set.of(new PeerApproval("other_topic")),
        EnumSet.of(RequesterPrivilege.Status.INACTIVE));

    assertIterableEquals(
        List.of("roles/for-user"),
        privileges.available().stream().map(e -> e.id().roleBinding().role())
            .collect(Collectors.toList()));
    var peerApprovalPrivilege = privileges.available().first();
    assertEquals(new PeerApproval("other_topic").name(), peerApprovalPrivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, peerApprovalPrivilege.status());
  }

  @Test
  public void whenEffectiveIamPoliciesContainEligiblePeerApprovalBindingWithNoTopic_ThenFindRequesterPrivilegesReturnsList()
      throws Exception {
    var caiClient = setupTestBindings();

    var repository = new AssetInventoryRepository(
        new SynchronousExecutor(),
        Mockito.mock(DirectoryGroupsClient.class),
        caiClient,
        new AssetInventoryRepository.Options("organization/0"));

    var privileges = repository.findRequesterPrivileges(
        SAMPLE_USER,
        SAMPLE_PROJECT,
        Set.of(new PeerApproval("")),
        EnumSet.of(RequesterPrivilege.Status.INACTIVE));

    var peerPrivileges = privileges.available();
    assertEquals(3, peerPrivileges.size());

    var peerApprovalPrivilege = peerPrivileges.stream().findFirst().get();
    assertEquals(new PeerApproval("").name(), peerApprovalPrivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, peerApprovalPrivilege.status());

    peerApprovalPrivilege = peerPrivileges.stream().skip(1).findFirst().get();
    assertEquals(new PeerApproval("other_topic").name(), peerApprovalPrivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, peerApprovalPrivilege.status());

    peerApprovalPrivilege = peerPrivileges.stream().skip(2).findFirst().get();
    assertEquals(new PeerApproval("topic").name(), peerApprovalPrivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, peerApprovalPrivilege.status());
  }

  @Test
  public void whenEffectiveIamPoliciesContainEligibleExternalApprovalBinding_ThenFindRequesterPrivilegesReturnsList()
      throws Exception {
    var caiClient = setupTestBindings();

    var repository = new AssetInventoryRepository(
        new SynchronousExecutor(),
        Mockito.mock(DirectoryGroupsClient.class),
        caiClient,
        new AssetInventoryRepository.Options("organization/0"));

    var privileges = repository.findRequesterPrivileges(
        SAMPLE_USER,
        SAMPLE_PROJECT,
        Set.of(new ExternalApproval("topic")),
        EnumSet.of(RequesterPrivilege.Status.INACTIVE));

    assertIterableEquals(
        List.of("roles/for-user"),
        privileges.available().stream().map(e -> e.id().roleBinding().role())
            .collect(Collectors.toList()));
    var externalApprovalprivilege = privileges.available().first();
    assertEquals(new ExternalApproval("topic").name(), externalApprovalprivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, externalApprovalprivilege.status());
  }

  @Test
  public void whenEffectiveIamPoliciesContainEligibleExternalApprovalBindingWithOtherTopic_ThenFindRequesterPrivilegesReturnsList()
      throws Exception {
    var caiClient = setupTestBindings();

    var repository = new AssetInventoryRepository(
        new SynchronousExecutor(),
        Mockito.mock(DirectoryGroupsClient.class),
        caiClient,
        new AssetInventoryRepository.Options("organization/0"));

    var privileges = repository.findRequesterPrivileges(
        SAMPLE_USER,
        SAMPLE_PROJECT,
        Set.of(new ExternalApproval("other_topic")),
        EnumSet.of(RequesterPrivilege.Status.INACTIVE));

    assertIterableEquals(
        List.of("roles/for-user"),
        privileges.available().stream().map(e -> e.id().roleBinding().role())
            .collect(Collectors.toList()));
    var externalApprovalPrivilege = privileges.available().first();
    assertEquals(new ExternalApproval("other_topic").name(),
        externalApprovalPrivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, externalApprovalPrivilege.status());
  }

  @Test
  public void whenEffectiveIamPoliciesContainEligibleExternalApprovalBindingWithNoTopic_ThenFindRequesterPrivilegesReturnsList()
      throws Exception {
    var caiClient = setupTestBindings();

    var repository = new AssetInventoryRepository(
        new SynchronousExecutor(),
        Mockito.mock(DirectoryGroupsClient.class),
        caiClient,
        new AssetInventoryRepository.Options("organization/0"));

    var privileges = repository.findRequesterPrivileges(
        SAMPLE_USER,
        SAMPLE_PROJECT,
        Set.of(new ExternalApproval("")),
        EnumSet.of(RequesterPrivilege.Status.INACTIVE));

    var externalPrivileges = privileges.available();
    assertEquals(3, externalPrivileges.size());

    var externalApprovalPrivilege = externalPrivileges.stream().findFirst().get();
    assertEquals(new ExternalApproval("").name(), externalApprovalPrivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, externalApprovalPrivilege.status());

    externalApprovalPrivilege = externalPrivileges.stream().skip(1).findFirst().get();
    assertEquals(new ExternalApproval("other_topic").name(),
        externalApprovalPrivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, externalApprovalPrivilege.status());

    externalApprovalPrivilege = externalPrivileges.stream().skip(2).findFirst().get();
    assertEquals(new ExternalApproval("topic").name(), externalApprovalPrivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, externalApprovalPrivilege.status());
  }

  @Test
  public void whenEffectiveIamPoliciesContainEligibleBindingsWithDifferentType_ThenFindRequesterPrivilegesReturnsList()
      throws Exception {
    var caiClient = setupTestBindings();

    var repository = new AssetInventoryRepository(
        new SynchronousExecutor(),
        Mockito.mock(DirectoryGroupsClient.class),
        caiClient,
        new AssetInventoryRepository.Options("organization/0"));

    var privileges = repository.findRequesterPrivileges(
        SAMPLE_USER,
        SAMPLE_PROJECT,
        Set.of(new SelfApproval(), new PeerApproval("topic"),
            new ExternalApproval("topic")),
        EnumSet.of(RequesterPrivilege.Status.INACTIVE));

    assertIterableEquals(
        List.of("roles/for-user", "roles/for-user", "roles/for-user"),
        privileges.available().stream().map(e -> e.id().roleBinding().role())
            .collect(Collectors.toList()));
    assertEquals(3, privileges.available().size());
    var externalApprovalPrivilege = privileges.available().first();
    assertEquals(new ExternalApproval("topic").name(), externalApprovalPrivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, externalApprovalPrivilege.status());
    var peerApprovalPrivilege = privileges.available().stream().skip(1).findFirst()
        .get();
    assertEquals(new PeerApproval("topic").name(), peerApprovalPrivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, peerApprovalPrivilege.status());
    var selfApprovalPrivilege = privileges.available().last();
    assertEquals(new SelfApproval().name(), selfApprovalPrivilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.INACTIVE, selfApprovalPrivilege.status());

  }

  @Test
  public void whenEffectiveIamPoliciesContainsExpiredActivation_ThenFindRequesterPrivilegesReturnsList()
      throws Exception {
    var jitBindingForUser = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr().setExpression(SELF_APPROVAL_CONDITION))
        .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var expiredActivationForUser = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr()
            .setTitle(PrivilegeFactory.ACTIVATION_CONDITION_TITLE)
            .setExpression(new TemporaryIamCondition(
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS)).toString()))
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
                    .setBindings(List.of(jitBindingForUser,
                        expiredActivationForUser)))));

    var repository = new AssetInventoryRepository(
        new SynchronousExecutor(),
        Mockito.mock(DirectoryGroupsClient.class),
        caiClient,
        new AssetInventoryRepository.Options("organization/0"));

    //
    // AVAILABLE + ACTIVE.
    //
    {
      var privileges = repository.findRequesterPrivileges(
          SAMPLE_USER,
          SAMPLE_PROJECT,
          Set.of(new SelfApproval()),
          EnumSet.of(RequesterPrivilege.Status.INACTIVE,
              RequesterPrivilege.Status.ACTIVE));
      var privilege = privileges.available().first();
      assertEquals(new SelfApproval().name(), privilege.activationType().name());
      assertEquals(RequesterPrivilege.Status.INACTIVE, privilege.status());
    }

    //
    // AVAILABLE + ACTIVE + EXPIRED.
    //
    {
      var privileges = repository.findRequesterPrivileges(
          SAMPLE_USER,
          SAMPLE_PROJECT,
          Set.of(new SelfApproval(), new PeerApproval("topic")),
          EnumSet.of(RequesterPrivilege.Status.INACTIVE, RequesterPrivilege.Status.ACTIVE,
              RequesterPrivilege.Status.EXPIRED));

      assertEquals(1, privileges.available().size());
      assertEquals(1, privileges.available().size());

      var privilege = privileges.available().first();
      assertEquals(new SelfApproval().name(), privilege.activationType().name());
      assertEquals(RequesterPrivilege.Status.INACTIVE, privilege.status());

      assertEquals(
          "roles/for-user",
          privileges.available().stream().toList().get(0).id()
              .roleBinding()
              .role());
    }

  }

  @Test
  public void whenEffectiveIamPoliciesContainsActivation_ThenFindRequesterPrivilegesReturnsList()
      throws Exception {
    var jitBindingForUser = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr().setExpression(SELF_APPROVAL_CONDITION))
        .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var expiredActivationForUser = new Binding()
        .setRole("roles/for-user")
        .setCondition(new Expr()
            .setTitle(PrivilegeFactory.ACTIVATION_CONDITION_TITLE)
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
                    .setBindings(List.of(jitBindingForUser,
                        expiredActivationForUser)))));

    var repository = new AssetInventoryRepository(
        new SynchronousExecutor(),
        Mockito.mock(DirectoryGroupsClient.class),
        caiClient,
        new AssetInventoryRepository.Options("organization/0"));

    var privileges = repository.findRequesterPrivileges(
        SAMPLE_USER,
        SAMPLE_PROJECT,
        Set.of(new SelfApproval(), new PeerApproval("topic")),
        EnumSet.of(RequesterPrivilege.Status.INACTIVE, RequesterPrivilege.Status.ACTIVE));
    var privilege = privileges.available().first();
    assertEquals(new SelfApproval().name(), privilege.activationType().name());
    assertEquals(RequesterPrivilege.Status.ACTIVE, privilege.status());

  }

  // ---------------------------------------------------------------------------
  // findReviewerPrivelegeHolders.
  // ---------------------------------------------------------------------------

  @Test
  public void whenEffectiveIamPoliciesOnlyContainInapplicableBindings_ThenFindReviewerPrivelegeHoldersHoldersReturnsEmptyList()
      throws Exception {
    var otherBinding1 = new Binding()
        .setRole("roles/other-1")
        .setCondition(new Expr().setExpression(PEER_CONDITION))
        .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var otherBinding2 = new Binding()
        .setRole("roles/role-1")
        .setCondition(new Expr().setExpression(SELF_APPROVAL_CONDITION))
        .setMembers(List.of("user:" + SAMPLE_USER.email, "user:other@example.com"));
    var otherBinding3 = new Binding()
        .setRole("roles/role-1")
        .setCondition(new Expr().setExpression(REVIEWER_CONDITION))
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
                    .setBindings(List.of(otherBinding1,
                        otherBinding2,
                        otherBinding3)))));

    var repository = new AssetInventoryRepository(
        new SynchronousExecutor(),
        Mockito.mock(DirectoryGroupsClient.class),
        caiClient,
        new AssetInventoryRepository.Options("organization/0"));

    var holders = repository.findReviewerPrivelegeHolders(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, "roles/role-1")),
        new PeerApproval("topic"));

    assertNotNull(holders);
    assertTrue(holders.isEmpty());
  }

  @Test
  public void whenEffectiveIamPoliciesContainUsers_ThenFindReviewerPrivelegeHoldersHoldersReturnsList()
      throws Exception {
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
                        .setCondition(new Expr()
                            .setExpression(PEER_CONDITION))
                        .setMembers(List.of(
                            "user:user-1@example.com",
                            "user:user-2@example.com"))))),
            new PolicyInfo()
                .setAttachedResource(SAMPLE_PROJECT.path())
                .setPolicy(new Policy()
                    .setBindings(List.of(new Binding()
                        .setRole(role.role())
                        .setCondition(new Expr()
                            .setExpression(PEER_CONDITION))
                        .setMembers(List.of(
                            "user:user-2@example.com")))))));

    var repository = new AssetInventoryRepository(
        new SynchronousExecutor(),
        Mockito.mock(DirectoryGroupsClient.class),
        caiClient,
        new AssetInventoryRepository.Options("organization/0"));

    var holders = repository.findReviewerPrivelegeHolders(
        new ProjectRoleBinding(role),
        new PeerApproval("topic"));

    assertNotNull(holders);
    assertEquals(
        Set.of(new UserEmail("user-1@example.com"), new UserEmail("user-2@example.com")),
        holders);
  }

  @Test
  public void whenEffectiveIamPoliciesContainsGroups_ThenfindReviewerPrivelegeHoldersReturnsList()
      throws Exception {
    var role = new RoleBinding(SAMPLE_PROJECT, "roles/role-1");

    var groupBinding = new Binding()
        .setRole(role.role())
        .setCondition(new Expr().setExpression(PEER_CONDITION))
        .setMembers(List.of("group:group@example.com"));
    var groupBindingNoTopic = new Binding()
        .setRole(role.role())
        .setCondition(new Expr().setExpression(PEER_CONDITION_NO_TOPIC))
        .setMembers(List.of("group:group2@example.com"));
    var unavailableGroupBinding = new Binding()
        .setRole(role.role())
        .setCondition(new Expr().setExpression(PEER_CONDITION))
        .setMembers(List.of("group:unavailable-group@example.com"));

    var groupsClient = Mockito.mock(DirectoryGroupsClient.class);
    when(groupsClient
        .listDirectGroupMembers(eq("group@example.com")))
        .thenReturn(List.of(
            new Member().setEmail("user-1@example.com"),
            new Member().setEmail("user-2@example.com")));
    when(groupsClient
        .listDirectGroupMembers(eq("group2@example.com")))
        .thenReturn(List.of(
            new Member().setEmail("user-3@example.com"),
            new Member().setEmail("user-4@example.com")));
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
                    .setBindings(
                        List.of(groupBinding,
                            groupBindingNoTopic,
                            unavailableGroupBinding)))));

    var repository = new AssetInventoryRepository(
        new SynchronousExecutor(),
        groupsClient,
        caiClient,
        new AssetInventoryRepository.Options("organization/0"));

    var holders = repository.findReviewerPrivelegeHolders(
        new ProjectRoleBinding(role),
        new PeerApproval("topic"));

    assertNotNull(holders);
    assertEquals(
        Set.of(new UserEmail("user-1@example.com"), new UserEmail("user-2@example.com"),
            new UserEmail("user-3@example.com"), new UserEmail("user-4@example.com")),
        holders);
  }
}
