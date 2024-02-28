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

package com.google.solutions.jitaccess.core.catalog.jitrole;

import com.google.api.services.cloudidentity.v1.model.*;
import com.google.solutions.jitaccess.core.ResourceNotFoundException;
import com.google.solutions.jitaccess.core.auth.GroupId;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.clients.CloudIdentityGroupsClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class TestUserContextResolver {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");
  private static final GroupId SAMPLE_GROUP_1 = new GroupId("group-1@example.com");
  private static final GroupId SAMPLE_GROUP_2 = new GroupId("group-2@example.com");
  private static final GroupId SAMPLE_JITGROUP_1 = new GroupId("jit-role-1@example.com");
  private static final GroupId SAMPLE_JITGROUP_2 = new GroupId("jit-role-2@example.com");

  private class SynchronousExecutor implements Executor {
    @Override
    public void execute(Runnable command) {
      command.run();
    }
  }

  private class SampleGroupMapping implements JitRoleGroupMapping {
    @Override
    public boolean isJitRole(GroupId group) {
      return group.email.startsWith("jit-");
    }

    @Override
    public JitRole jitRoleFromGroup(GroupId email) {
      return new JitRole(
        "policy",
        email.value().split("@")[0].substring(4));
    }

    @Override
    public GroupId groupFromJitRole(JitRole role) {
      return new GroupId(String.format("jit-%s@example.com", role.name()));
    }
  }

  //---------------------------------------------------------------------------
  // lookup.
  //---------------------------------------------------------------------------

  @Test
  public void whenUserNotFound_ThenLookupThrowsException() throws Exception {
    var user = SAMPLE_USER;

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    when(groupsClient.listMembershipsByUser(eq(user)))
      .thenThrow(new ResourceNotFoundException("mock"));

    var resolver = new UserContext.Resolver(
      groupsClient,
      Mockito.mock(JitRoleGroupMapping.class),
      new SynchronousExecutor());

    assertThrows(
      ResourceNotFoundException.class,
      () -> resolver.lookup(user));
  }

  @Test
  public void whenUserHasNoGroupMemberships_ThenLookupReturnsContext() throws Exception {
    var user = SAMPLE_USER;

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    when(groupsClient.listMembershipsByUser(eq(user)))
      .thenReturn(List.of());

    var resolver = new UserContext.Resolver(
      groupsClient,
      Mockito.mock(JitRoleGroupMapping.class),
      new SynchronousExecutor());

    var context = resolver.lookup(user);

    assertEquals(user, context.user());
    assertTrue(context.principals().contains(user));
    assertEquals(0, context.activeRoles().size());
  }

  @Test
  public void whenUserHasNonJitRoleGroupMemberships_ThenLookupReturnsContext() throws Exception {
    var user = SAMPLE_USER;

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    when(groupsClient.listMembershipsByUser(eq(user)))
      .thenReturn(List.of(
        new MembershipRelation().setGroup(SAMPLE_GROUP_1.email),
        new MembershipRelation().setGroup(SAMPLE_GROUP_2.email)
      ));

    var resolver = new UserContext.Resolver(
      groupsClient,
      Mockito.mock(JitRoleGroupMapping.class),
      new SynchronousExecutor());

    var context = resolver.lookup(user);

    assertEquals(user, context.user());
    assertTrue(context.principals().contains(user));
    assertTrue(context.principals().contains(SAMPLE_GROUP_1));
    assertTrue(context.principals().contains(SAMPLE_GROUP_2));
    assertEquals(0, context.activeRoles().size());
  }

  @Test
  public void whenJitRoleGroupMembershipNotFound_ThenLookupIgnoresMembership() throws Exception {
    var user = SAMPLE_USER;

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    when(groupsClient.listMembershipsByUser(eq(user)))
      .thenReturn(List.of(
        new MembershipRelation().setMembership("m-1").setGroup(SAMPLE_JITGROUP_1.email),
        new MembershipRelation().setMembership("m-2").setGroup(SAMPLE_JITGROUP_2.email)
      ));

    when(groupsClient.getMembership(eq(new CloudIdentityGroupsClient.MembershipId("m-1"))))
      .thenReturn(new Membership()
        .setPreferredMemberKey(new EntityKey().setId(SAMPLE_JITGROUP_1.email))
        .setRoles(List.of(new MembershipRole()
          .setExpiryDetail(new ExpiryDetail().setExpireTime("2100-01-01T00:00:00Z")))));
    when(groupsClient.getMembership(eq(new CloudIdentityGroupsClient.MembershipId("m-2"))))
      .thenThrow(new ResourceNotFoundException("mock"));

    var resolver = new UserContext.Resolver(
      groupsClient,
      new SampleGroupMapping(),
      new SynchronousExecutor());

    var context = resolver.lookup(user);

    assertEquals(user, context.user());
    assertTrue(context.principals().contains(user));

    //
    // Check that m-2 is there and m-1 is ignored.
    //
    var roles = context.activeRoles().stream().toList();
    assertEquals(
      new JitRole("policy", "role-1"),
      roles.get(0).role());
  }

  @Test
  public void whenJitRoleGroupLacksExpiry_ThenLookupReturnsGroup() throws Exception {
    var user = SAMPLE_USER;

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    when(groupsClient.listMembershipsByUser(eq(user)))
      .thenReturn(List.of(
        new MembershipRelation().setMembership("m-1").setGroup(SAMPLE_JITGROUP_1.email)
      ));

    when(groupsClient.getMembership(eq(new CloudIdentityGroupsClient.MembershipId("m-1"))))
      .thenReturn(new Membership()
        .setPreferredMemberKey(new EntityKey().setId(SAMPLE_JITGROUP_1.email))
        .setRoles(List.of(new MembershipRole())));

    var resolver = new UserContext.Resolver(
      groupsClient,
      new SampleGroupMapping(),
      new SynchronousExecutor());

    var context = resolver.lookup(user);

    assertEquals(user, context.user());
    assertTrue(context.principals().contains(user));
    assertTrue(context.principals().contains(SAMPLE_JITGROUP_1));

    assertEquals(0, context.activeRoles().size());
  }
}
