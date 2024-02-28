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

package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.catalog.*;
import com.google.solutions.jitaccess.catalog.auth.Principal;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.*;
import com.google.solutions.jitaccess.web.EventIds;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestSystemsResource {

  private static final UserId SAMPLE_USER = new UserId("user@example.com");

  private static Catalog createCatalog(EnvironmentPolicy environment, Subject subject) {
    return new Catalog(
      subject,
      CatalogSources.create(environment));
  }

  private static Catalog createCatalog(EnvironmentPolicy environment) {
    return createCatalog(environment, Subjects.create(SAMPLE_USER));
  }

  //---------------------------------------------------------------------------
  // get.
  //---------------------------------------------------------------------------

  @Test
  public void get_whenEnvironmentInvalid() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new SystemsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group.system().environment());

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.get(null, "system"));
  }

  @Test
  public void get_whenSystemInvalid() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new SystemsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group.system().environment());

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.get(group.system().environment().name(), null));
  }

  @Test
  public void get_whenEnvironmentNotFound() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new SystemsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group.system().environment());

    assertThrows(
      AccessDeniedException.class,
      () ->  resource.get("unknown", "system"));

    verify(resource.logger, times(1)).warn(
      eq(EventIds.API_VIEW_SYSTEMS),
      any(Exception.class));
  }

  @Test
  public void get_whenAccessToSystemDenied() {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new Policy.Metadata("test", Instant.EPOCH));
    var system = new SystemPolicy(
      "sys-1",
      "Sys-1",
      new AccessControlList.Builder()
        .deny(SAMPLE_USER, -1)
        .build(),
      Map.of());
    environment.add(system);

    var resource = new SystemsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(environment, Subjects.create(SAMPLE_USER));

    assertThrows(
      AccessDeniedException.class,
      () ->  resource.get(environment.name(), system.name()));

    verify(resource.logger, times(1)).warn(
      eq(EventIds.API_VIEW_SYSTEMS),
      any(Exception.class));
  }

  @Test
  public void get_whenAccessToSomeGroupsDenied_thenResultIsFiltered() throws Exception {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new Policy.Metadata("test", Instant.EPOCH));
    var system = new SystemPolicy("system-1", "System 1");
    var allowedGroup = new JitGroupPolicy(
      "allowed-1",
      "Group 1",
      new AccessControlList(
        List.of(new AccessControlList.AllowedEntry(
          SAMPLE_USER,
          PolicyPermission.VIEW.toMask()))),
      Map.of(),
      List.of());
    var deniedGroup = new JitGroupPolicy(
      "denied-1",
      "Denied 1",
      new AccessControlList.Builder()
        .deny(SAMPLE_USER, -1)
        .build(),
      Map.of(),
      List.of());
    system.add(allowedGroup);
    system.add(deniedGroup);
    environment.add(system);

    var resource = new SystemsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(environment, Subjects.create(SAMPLE_USER));

    var systemInfo = resource.get(environment.name(), system.name());
    assertEquals(system.name(), systemInfo.name());
    assertEquals(system.description(), systemInfo.description());
    assertEquals(environment.name(), systemInfo.environment().name());
    assertEquals(environment.description(), systemInfo.environment().description());

    assertEquals(1, systemInfo.groups().size());
    assertSame(allowedGroup.name(), systemInfo.groups().stream().findFirst().get().name());
  }

  @Test
  public void get_returnsSortedListOfGroups() throws Exception {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new Policy.Metadata("test", Instant.EPOCH));
    var system = new SystemPolicy("system-1", "System 1");
    system.add(new JitGroupPolicy(
      "group-2",
      "Two",
      AccessControlList.EMPTY,
      Map.of(),
      List.of()));
    system.add(new JitGroupPolicy(
      "group-3",
      "Three",
      AccessControlList.EMPTY,
      Map.of(),
      List.of()));
    system.add(new JitGroupPolicy(
      "z-group-1",
      "One",
      AccessControlList.EMPTY,
      Map.of(),
      List.of()) {
      @Override
      public @NotNull String displayName() {
        return "group-1";
      }
    });
    environment.add(system);

    var resource = new SystemsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(environment, Subjects.create(SAMPLE_USER));

    var systemInfo = resource.get(environment.name(), system.name());
    var groups = List.copyOf(systemInfo.groups());

    assertEquals("z-group-1", groups.get(0).name());
    assertEquals("group-2", groups.get(1).name());
    assertEquals("group-3", groups.get(2).name());
  }

  @Test
  public void get_whenGroupJoined() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(
        List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.JOIN.toMask()))),
      Map.of());

    var expiry = Instant.now().plusSeconds(60);
    var subject = Subjects.createWithPrincipals(
      SAMPLE_USER,
      Set.of(
        new Principal(SAMPLE_USER),
        new Principal(group.id(), expiry)));

    var resource = new SystemsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group.system().environment(), subject);

    var systemInfo = resource.get(group.id().environment(), group.id().system());
    assertEquals(1, systemInfo.groups().size());

    var groupInfo = systemInfo.groups().get(0);
    assertEquals(GroupsResource.JoinStatusInfo.JOINED, groupInfo.join().status());

    assertEquals(group.name(), groupInfo.name());
    assertEquals(group.id().toString(), groupInfo.id());
    assertTrue(groupInfo.join().membership().active());
    assertEquals(expiry.getEpochSecond(), groupInfo.join().membership().expiry());
  }

  @Test
  public void get_whenJoinDisallowed() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(
        List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.VIEW.toMask()))),
      Map.of());

    var resource = new SystemsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group.system().environment());

    var systemInfo = resource.get(group.id().environment(), group.id().system());
    assertEquals(1, systemInfo.groups().size());

    var groupInfo = systemInfo.groups().get(0);
    assertEquals(GroupsResource.JoinStatusInfo.JOIN_DISALLOWED, groupInfo.join().status());

    assertEquals(group.name(), groupInfo.name());
    assertEquals(group.id().toString(), groupInfo.id());
    assertFalse(groupInfo.join().membership().active());
  }

  @Test
  public void get_whenJoinAllowedWithApproval() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(
        List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.JOIN.toMask()))),
      Map.of());

    var resource = new SystemsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group.system().environment());

    var systemInfo = resource.get(group.id().environment(), group.id().system());
    assertEquals(1, systemInfo.groups().size());

    var groupInfo = systemInfo.groups().get(0);
    assertEquals(GroupsResource.JoinStatusInfo.JOIN_ALLOWED_WITH_APPROVAL, groupInfo.join().status());

    assertEquals(group.name(), groupInfo.name());
    assertEquals(group.id().toString(), groupInfo.id());
    assertFalse(groupInfo.join().membership().active());
  }

  @Test
  public void get_whenJoinAllowedWithoutApproval() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(
        List.of(
          new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.JOIN.toMask()),
          new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask()))),
      Map.of());

    var resource = new SystemsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group.system().environment());

    var systemInfo = resource.get(group.id().environment(), group.id().system());
    assertEquals(1, systemInfo.groups().size());

    var groupInfo = systemInfo.groups().get(0);
    assertEquals(GroupsResource.JoinStatusInfo.JOIN_ALLOWED_WITHOUT_APPROVAL, groupInfo.join().status());

    assertEquals(group.name(), groupInfo.name());
    assertEquals(group.id().toString(), groupInfo.id());
    assertFalse(groupInfo.join().membership().active());
  }
}
