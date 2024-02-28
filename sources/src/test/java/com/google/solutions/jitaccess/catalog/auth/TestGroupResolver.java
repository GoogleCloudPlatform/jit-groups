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

package com.google.solutions.jitaccess.catalog.auth;

import com.google.api.services.cloudidentity.v1.model.EntityKey;
import com.google.api.services.cloudidentity.v1.model.Membership;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestGroupResolver {
  private static final UserId TEST_USER_1 = new UserId("test-1@example.com");
  private static final UserId TEST_USER_2 = new UserId("test-2@example.com");
  private static final GroupId TEST_GROUP_1 = new GroupId("group-1@example.com");
  private static final GroupId TEST_GROUP_2 = new GroupId("group-2@example.com");

  private static class SynchronousExecutor implements Executor {

    @Override
    public void execute(Runnable command) {
      command.run();
    }
  }

  @Test
  public void expand_whenSetEmpty() throws Exception {
    var groupsClient = mock(CloudIdentityGroupsClient.class);
    var expander = new GroupResolver(groupsClient, new SynchronousExecutor());

    var inputSet = Set.<PrincipalId>of();
    var outputSet = expander.expand(inputSet);
    assertEquals(0, outputSet.size());
  }

  @Test
  public void expand_whenSetContainsNoGroups() throws Exception {
    var groupsClient = mock(CloudIdentityGroupsClient.class);
    var expander = new GroupResolver(groupsClient, new SynchronousExecutor());

    var inputSet = Set.<PrincipalId>of(
      TEST_USER_1,
      TEST_USER_2);
    var outputSet = expander.expand(inputSet);

    assertEquals(2, outputSet.size());
    assertTrue(outputSet.contains(TEST_USER_1));
    assertTrue(outputSet.contains(TEST_USER_2));
  }

  @Test
  public void expand_whenSetContainsGroups() throws Exception {
    var member1 = new UserId("member-1@example.com");
    var member2 = new GroupId("member-1@example.com");

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

    var expander = new GroupResolver(groupsClient, new SynchronousExecutor());

    var inputSet = Set.<PrincipalId>of(
      TEST_USER_1,
      TEST_USER_2,
      TEST_GROUP_1,
      TEST_GROUP_2);
    var outputSet = expander.expand(inputSet);

    assertEquals(4, outputSet.size());
    assertTrue(outputSet.contains(TEST_USER_1));
    assertTrue(outputSet.contains(TEST_USER_2));
    assertTrue(outputSet.contains(member1));
    assertTrue(outputSet.contains(member2));
  }
}
