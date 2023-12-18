//
// Copyright 2023 Google LLC
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

package com.google.solutions.jitaccess.core.activation.project;

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.Annotated;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.activation.Entitlement;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestIamPolicyCatalog {

  private static final UserId SAMPLE_USER = new UserId("user@example.com");
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");

  //---------------------------------------------------------------------------
  // listProjects.
  //---------------------------------------------------------------------------

  @Test
  public void whenProjectQueryProvided_thenListProjectsPerformsProjectSearch() throws Exception {
    var resourceManager = Mockito.mock(ResourceManagerClient.class);
    when(resourceManager.searchProjectIds(eq("query")))
      .thenReturn(new TreeSet<>(Set.of(
        new ProjectId("project-2"),
        new ProjectId("project-3"),
        new ProjectId("project-1"))));

    var catalog = new IamPolicyCatalog(
      Mockito.mock(PolicyAnalyzer.class),
      resourceManager,
      new IamPolicyCatalog.Options(
        "query",
        Duration.ofMinutes(5),
        1,
        1)
    );

    var projects = catalog.listProjects(SAMPLE_USER);
    assertIterableEquals(
      List.of( // Sorted
        new ProjectId("project-1"),
        new ProjectId("project-2"),
        new ProjectId("project-3")),
      projects);
  }

  @Test
  public void whenProjectQueryNotProvided_thenListProjectsPerformsPolicySearch() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzer.class);
    when(policyAnalyzer.findProjectsWithEntitlements(eq(SAMPLE_USER)))
      .thenReturn(new TreeSet<>(Set.of(
        new ProjectId("project-2"),
        new ProjectId("project-3"),
        new ProjectId("project-1"))));

    var catalog = new IamPolicyCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new IamPolicyCatalog.Options(
        "",
        Duration.ofMinutes(5),
        1,
        1)
    );

    var projects = catalog.listProjects(SAMPLE_USER);
    assertIterableEquals(
      List.of( // Sorted
        new ProjectId("project-1"),
        new ProjectId("project-2"),
        new ProjectId("project-3")),
      projects);
  }

  //---------------------------------------------------------------------------
  // listEntitlements.
  //---------------------------------------------------------------------------

  @Test
  public void listEntitlementsReturnsAvailableAndActiveEntitlements() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzer.class);
    when(policyAnalyzer.findEntitlements(
      eq(SAMPLE_USER),
      eq(SAMPLE_PROJECT),
      any()))
      .thenReturn(new Annotated<>(
        new TreeSet<>(Set.of()),
        Set.of()));

    var catalog = new IamPolicyCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new IamPolicyCatalog.Options(
        null,
        Duration.ofMinutes(5),
        1,
        1)
    );

    var entitlements = catalog.listEntitlements(SAMPLE_USER, SAMPLE_PROJECT);
    assertNotNull(entitlements);

    verify(policyAnalyzer, times(1)).findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));
  }
}
