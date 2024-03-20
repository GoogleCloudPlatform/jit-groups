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

import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.web.LogAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

public class TestListScopesAction {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");

  @Test
  public void whenCatalogReturnsNoProjects_ThenResponseContainsEmptyList() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog.listScopes(argThat(ctx -> ctx.user().equals(SAMPLE_USER))))
      .thenReturn(new TreeSet<>());

    var action = new ListScopesAction(new LogAdapter(), catalog);
    var response = action.execute(Mocks.createIapPrincipalMock(SAMPLE_USER));

    assertEquals(0, response.projects.size());
  }

  @Test
  public void whenCatalogReturnsProjects_ThenResponseContainsProjects() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog.listScopes(argThat(ctx -> ctx.user().equals(SAMPLE_USER))))
      .thenReturn(new TreeSet<>(Set.of(
        new ProjectId("project-1"),
        new ProjectId("project-2"),
        new ProjectId("project-3"))));

    var action = new ListScopesAction(new LogAdapter(), catalog);
    var response = action.execute(Mocks.createIapPrincipalMock(SAMPLE_USER));

    assertNotNull(response.projects);
    assertIterableEquals(
      List.of(
        "project-1",
        "project-2",
        "project-3"),
      response.projects);
  }
}
