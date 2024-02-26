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

package com.google.solutions.jitaccess.core.auth;

import com.google.api.services.cloudidentity.v1.model.EntityKey;
import com.google.api.services.cloudidentity.v1.model.Membership;
import com.google.solutions.jitaccess.core.clients.CloudIdentityGroupsClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public class TestGroupExpander {
  private static final UserEmail TEST_USER_1 = new UserEmail("test-1@example.com");
  private static final UserEmail TEST_USER_2 = new UserEmail("test-2@example.com");
  private static final GroupEmail TEST_GROUP_1 = new GroupEmail("group-1@example.com");
  private static final GroupEmail TEST_GROUP_2 = new GroupEmail("group-2@example.com");

  private class SynchronousExecutor implements Executor {

    @Override
    public void execute(Runnable command) {
      command.run();
    }
  }

  @Test
  public void whenAclEmpty_ThenExpandReturnsEquivalentAcl() throws Exception {
    var groupsClient = mock(CloudIdentityGroupsClient.class);
    var expander = new GroupExpander(groupsClient, new SynchronousExecutor());

    var inputAcl = new AccessControlList(Set.of());
    var outputAcl = expander.expand(inputAcl);
    assertEquals(0, outputAcl.allowedPrincipals().size());
  }

  @Test
  public void whenAclContainsNoGroups_ThenExpandReturnsEquivalentAcl() throws Exception {
    var groupsClient = mock(CloudIdentityGroupsClient.class);
    var expander = new GroupExpander(groupsClient, new SynchronousExecutor());

    var inputAcl = new AccessControlList(Set.of(
      TEST_USER_1,
      TEST_USER_2));
    var outputAcl = expander.expand(inputAcl);

    assertEquals(2, outputAcl.allowedPrincipals().size());
    assertTrue(outputAcl.allowedPrincipals().contains(TEST_USER_1));
    assertTrue(outputAcl.allowedPrincipals().contains(TEST_USER_2));
  }

  @Test
  public void whenAclContainsGroups_ThenExpandReturnsExpandedAcl() throws Exception {
    var member1 = new UserEmail("member-1@example.com");
    var member2 = new GroupEmail("member-1@example.com");

    var groupsClient = mock(CloudIdentityGroupsClient.class);
    when(groupsClient.listMemberships(TEST_GROUP_1))
      .thenReturn(List.of(new Membership()
        .setType("SERVICE_ACCOUNT")
        .setPreferredMemberKey(new EntityKey().setId("sa@example.com"))));
    when(groupsClient.listMemberships(TEST_GROUP_2))
      .thenReturn(List.of(
        new Membership()
          .setType("USER")
          .setPreferredMemberKey(new EntityKey().setId(member1.email)),
        new Membership()
          .setType("GROUP")
          .setPreferredMemberKey(new EntityKey().setId(member2.email))));

    var expander = new GroupExpander(groupsClient, new SynchronousExecutor());

    var inputAcl = new AccessControlList(Set.of(
      TEST_USER_1,
      TEST_USER_2,
      TEST_GROUP_1,
      TEST_GROUP_2));
    var outputAcl = expander.expand(inputAcl);

    assertEquals(4, outputAcl.allowedPrincipals().size());
    assertTrue(outputAcl.allowedPrincipals().contains(TEST_USER_1));
    assertTrue(outputAcl.allowedPrincipals().contains(TEST_USER_2));
    assertTrue(outputAcl.allowedPrincipals().contains(member1));
    assertTrue(outputAcl.allowedPrincipals().contains(member2));
  }
}
