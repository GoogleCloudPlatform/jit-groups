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

package com.google.solutions.jitaccess.core.catalog.group;

import com.google.solutions.jitaccess.core.auth.GroupEmail;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.EntitlementCatalog;
import com.google.solutions.jitaccess.core.catalog.JustificationPolicy;
import com.google.solutions.jitaccess.core.clients.CloudIdentityGroupsClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestGroupMembershipActivator {

  private static final GroupEmail SAMPLE_GROUP_1 = new GroupEmail("group-1@example.com");
  private static final GroupEmail SAMPLE_GROUP_2 = new GroupEmail("group-2@example.com");
  private static final UserEmail SAMPLE_REQUESTING_USER = new UserEmail("user@example.com");
  private static final UserEmail SAMPLE_APPROVING_USER = new UserEmail("approver@example.com");

  //---------------------------------------------------------------------------
  // provisionAccess - JIT.
  //---------------------------------------------------------------------------

  @Test
  public void whenJitRequestContainsMultipleEntitlements_ThenProvisionAccessThrowsException() {
    var activator = new GroupMembershipActivator(
      Mockito.mock(EntitlementCatalog.class),
      Mockito.mock(CloudIdentityGroupsClient.class),
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createJitRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new GroupMembership(SAMPLE_GROUP_1), new GroupMembership(SAMPLE_GROUP_2)),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertThrows(
      IllegalArgumentException.class,
      () -> activator.provisionAccess(request));
  }

  //---------------------------------------------------------------------------
  // createJitRequest.
  //---------------------------------------------------------------------------

  @Test
  public void whenStartTimeInPast_ThenCreqteJitRequestThrowsException() {
    var activator = new GroupMembershipActivator(
      Mockito.mock(EntitlementCatalog.class),
      Mockito.mock(CloudIdentityGroupsClient.class),
      Mockito.mock(JustificationPolicy.class));

    assertThrows(
      IllegalArgumentException.class,
      () -> activator.createJitRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(new GroupMembership(SAMPLE_GROUP_1)),
        "justification",
        Instant.now().minusSeconds(60),
        Duration.ofMinutes(5)));
  }

  //---------------------------------------------------------------------------
  // createMpaRequest.
  //---------------------------------------------------------------------------

  @Test
  public void whenMpaRequestContainsMultipleEntitlements_ThenCreateMpaRequestThrowsException() throws Exception {
    var activator = new GroupMembershipActivator(
      Mockito.mock(EntitlementCatalog.class),
      Mockito.mock(CloudIdentityGroupsClient.class),
      Mockito.mock(JustificationPolicy.class));

    assertThrows(
      IllegalArgumentException.class,
      () -> activator.createMpaRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(new GroupMembership(SAMPLE_GROUP_1), new GroupMembership(SAMPLE_GROUP_2)),
        Set.of(SAMPLE_APPROVING_USER),
        "justification",
        Instant.now(),
        Duration.ofMinutes(5)));
  }
}
