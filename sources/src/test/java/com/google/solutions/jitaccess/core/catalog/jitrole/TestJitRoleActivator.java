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

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AlreadyExistsException;
import com.google.solutions.jitaccess.core.auth.GroupId;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.Catalog;
import com.google.solutions.jitaccess.core.catalog.JustificationPolicy;
import com.google.solutions.jitaccess.core.clients.CloudIdentityGroupsClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestJitRoleActivator {

  private static final JitRole SAMPLE_ROLE_1 = new JitRole("policy-1", "role-1");
  private static final JitRole SAMPLE_ROLE_2 = new JitRole("policy-1", "role-2");
  private static final UserId SAMPLE_REQUESTING_USER = new UserId("user@example.com");
  private static final UserId SAMPLE_APPROVING_USER = new UserId("approver@example.com");

  private static InstantSource createInstantSource(Instant instant) {
    var now = Instant.now();
    var source = Mockito.mock(InstantSource.class);
    when(source.instant()).thenReturn(now);
    return source;
  }

  //---------------------------------------------------------------------------
  // provisionAccess - JIT.
  //---------------------------------------------------------------------------

  @Test
  public void whenJitRequestContainsMultipleEntitlements_ThenProvisionAccessThrowsException() {
    var now = Instant.now();
    var clock = createInstantSource(now);

    var activator = new JitRoleActivator(
      Mockito.mock(Catalog.class),
      Mockito.mock(CloudIdentityGroupsClient.class),
      Mockito.mock(JitRoleGroupMapping.class),
      Mockito.mock(JustificationPolicy.class),
      clock);

    var request = activator.createJitRequest(
      new UserContext(SAMPLE_REQUESTING_USER, Set.of(), Set.of()),
      Set.of(SAMPLE_ROLE_1, SAMPLE_ROLE_2),
      "justification",
      now,
      Duration.ofMinutes(5));

    assertThrows(
      IllegalArgumentException.class,
      () -> activator.provisionAccess(request));
  }

  @Test
  public void whenJitRequestValid_ThenProvisionAccessAddsMembership() throws Exception {
    var now = Instant.now();
    var clock = createInstantSource(now);

    var mapping = Mockito.mock(JitRoleGroupMapping.class);
    when(mapping.groupFromJitRole(SAMPLE_ROLE_1))
      .thenReturn(new GroupId("sample-role-1@example.com"));

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    var activator = new JitRoleActivator(
      Mockito.mock(Catalog.class),
      groupsClient,
      mapping,
      Mockito.mock(JustificationPolicy.class),
      clock);

    var request = activator.createJitRequest(
      new UserContext(SAMPLE_REQUESTING_USER, Set.of(), Set.of()),
      Set.of(SAMPLE_ROLE_1),
      "justification",
      now,
      Duration.ofMinutes(5));

    activator.provisionAccess(request);

    verify(groupsClient, times(1)).addMembership(
      eq(new GroupId("sample-role-1@example.com")),
      eq(SAMPLE_REQUESTING_USER),
      eq(now.plus(Duration.ofMinutes(5))));
  }

  //---------------------------------------------------------------------------
  // provisionAccess - MPA.
  //---------------------------------------------------------------------------

  @Test
  public void whenMpaRequestContainsMultipleEntitlements_ThenProvisionAccessThrowsException() {
    var now = Instant.now();
    var clock = createInstantSource(now);

    var activator = new JitRoleActivator(
      Mockito.mock(Catalog.class),
      Mockito.mock(CloudIdentityGroupsClient.class),
      Mockito.mock(JitRoleGroupMapping.class),
      Mockito.mock(JustificationPolicy.class),
      clock);

    fail("todo"); //TODO: test
  }

  @Test
  public void whenMpaRequestValid_ThenProvisionAccessAddsMembership() throws Exception {
    var now = Instant.now();
    var clock = createInstantSource(now);

    var mapping = Mockito.mock(JitRoleGroupMapping.class);
    when(mapping.groupFromJitRole(SAMPLE_ROLE_1))
      .thenReturn(new GroupId("sample-role-1@example.com"));

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    var activator = new JitRoleActivator(
      Mockito.mock(Catalog.class),
      groupsClient,
      mapping,
      Mockito.mock(JustificationPolicy.class),
      clock);

    fail("todo"); //TODO: test
  }

  //---------------------------------------------------------------------------
  // createJitRequest.
  //---------------------------------------------------------------------------

  @Test
  public void whenStartTimeInPast_ThenCreateJitRequestThrowsException() {
    var now = Instant.now();
    var clock = createInstantSource(now);

    var activator = new JitRoleActivator(
      Mockito.mock(Catalog.class),
      Mockito.mock(CloudIdentityGroupsClient.class),
      Mockito.mock(JitRoleGroupMapping.class),
      Mockito.mock(JustificationPolicy.class),
      clock);

    assertThrows(
      IllegalArgumentException.class,
      () -> activator.createJitRequest(
        new UserContext(SAMPLE_REQUESTING_USER, Set.of(), Set.of()),
        Set.of(SAMPLE_ROLE_1),
        "justification",
        now.minusSeconds(60),
        Duration.ofMinutes(5)));
  }

  //---------------------------------------------------------------------------
  // createMpaRequest.
  //---------------------------------------------------------------------------

  @Test
  public void whenMpaRequestContainsMultipleEntitlements_ThenCreateMpaRequestThrowsException() throws Exception {
    var now = Instant.now();
    var clock = createInstantSource(now);

    var activator = new JitRoleActivator(
      Mockito.mock(Catalog.class),
      Mockito.mock(CloudIdentityGroupsClient.class),
      Mockito.mock(JitRoleGroupMapping.class),
      Mockito.mock(JustificationPolicy.class),
      clock);

    assertThrows(
      IllegalArgumentException.class,
      () -> activator.createMpaRequest(
        new UserContext(SAMPLE_REQUESTING_USER, Set.of(), Set.of()),
        Set.of(SAMPLE_ROLE_1, SAMPLE_ROLE_2),
        Set.of(SAMPLE_APPROVING_USER),
        "justification",
        now,
        Duration.ofMinutes(5)));
  }

  // TODO: add tests
}
