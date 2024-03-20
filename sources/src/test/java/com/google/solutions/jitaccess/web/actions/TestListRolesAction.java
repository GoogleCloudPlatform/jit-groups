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

package com.google.solutions.jitaccess.web.actions;

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.Entitlement;
import com.google.solutions.jitaccess.core.catalog.EntitlementSet;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRole;
import com.google.solutions.jitaccess.web.LogAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class TestListRolesAction {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");

  @Test
  public void whenProjectIsEmpty_ThenActionThrowsException() throws Exception {
    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when (catalog.createContext(any()))
      .thenAnswer(inv -> new MpaProjectRoleCatalog.UserContext(inv.getArgument(0)));
    when(catalog.listScopes(argThat(ctx -> ctx.user().equals(SAMPLE_USER))))
      .thenThrow(new AccessDeniedException("mock"));

    var action = new ListRolesAction(new LogAdapter(), catalog);

    assertThrows(
      IllegalArgumentException.class,
      () -> action.execute(Mocks.createIapPrincipalMock(SAMPLE_USER), " "));
  }

  @Test
  public void whenCatalogThrowsAccessDeniedException_ThenActionThrowsException() throws Exception {
    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when (catalog.createContext(any()))
      .thenAnswer(inv -> new MpaProjectRoleCatalog.UserContext(inv.getArgument(0)));
    when(catalog
      .listEntitlements(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER)),
        eq(new ProjectId("project-1"))))
      .thenThrow(new AccessDeniedException("mock"));

    var action = new ListRolesAction(new LogAdapter(), catalog);

    assertThrows(
      AccessDeniedException.class,
      () -> action.execute(Mocks.createIapPrincipalMock(SAMPLE_USER), "project-1"));
  }

  @Test
  public void whenCatalogReturnsNoRoles_ThenActionReturnsEmptyList() throws Exception {
    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when (catalog.createContext(any()))
      .thenAnswer(inv -> new MpaProjectRoleCatalog.UserContext(inv.getArgument(0)));
    when(catalog
      .listEntitlements(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER)),
        eq(new ProjectId("project-1"))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of()),
        Map.of(),
        Map.of(),
        Set.of("warning")));

    var action = new ListRolesAction(new LogAdapter(), catalog);
    var response = action.execute(Mocks.createIapPrincipalMock(SAMPLE_USER), "project-1");

    assertNotNull(response.roles);
    assertEquals(0, response.roles.size());
    assertNotNull(response.warnings);
    assertEquals(1, response.warnings.size());
    assertEquals("warning", response.warnings.stream().findFirst().get());
  }

  @Test
  public void whenCatalogReturnsRoles_ThenActionReturnsList() throws Exception {
    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when (catalog.createContext(any()))
      .thenAnswer(inv -> new MpaProjectRoleCatalog.UserContext(inv.getArgument(0)));

    var role1 = new Entitlement<ProjectRole>(
      new ProjectRole(new RoleBinding(new ProjectId("project-1").getFullResourceName(), "roles/browser")),
      "ent-1",
      ActivationType.JIT);
    var role2 = new Entitlement<ProjectRole>(
      new ProjectRole(new RoleBinding(new ProjectId("project-1").getFullResourceName(), "roles/janitor")),
      "ent-2",
      ActivationType.JIT);

    when(catalog
      .listEntitlements(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER)),
        eq(new ProjectId("project-1"))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(role1, role2)),
        Map.of(),
        Map.of(),
        Set.of()));

    var action = new ListRolesAction(new LogAdapter(), catalog);
    var response = action.execute(Mocks.createIapPrincipalMock(SAMPLE_USER), "project-1");

    assertNotNull(response.roles);
    assertEquals(2, response.roles.size());
    assertEquals(role1.id().roleBinding(), response.roles.get(0).roleBinding);
    assertEquals(role2.id().roleBinding(), response.roles.get(1).roleBinding);
    assertTrue(response.warnings.isEmpty());
  }
}
