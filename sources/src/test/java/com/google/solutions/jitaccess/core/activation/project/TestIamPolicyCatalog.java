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

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.Annotated;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.activation.ActivationType;
import com.google.solutions.jitaccess.core.activation.Entitlement;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.core.entitlements.RoleBinding;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestIamPolicyCatalog {

  private static final UserId SAMPLE_REQUESTING_USER = new UserId("user@example.com");
  private static final UserId SAMPLE_APPROVIING_USER = new UserId("approver@example.com");
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");
  private static final String SAMPLE_ROLE = "roles/resourcemanager.role1";

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

    var projects = catalog.listProjects(SAMPLE_REQUESTING_USER);
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
    when(policyAnalyzer.findProjectsWithEntitlements(eq(SAMPLE_REQUESTING_USER)))
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

    var projects = catalog.listProjects(SAMPLE_REQUESTING_USER);
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
      eq(SAMPLE_REQUESTING_USER),
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

    var entitlements = catalog.listEntitlements(SAMPLE_REQUESTING_USER, SAMPLE_PROJECT);
    assertNotNull(entitlements);

    verify(policyAnalyzer, times(1)).findEntitlements(
      SAMPLE_REQUESTING_USER,
      SAMPLE_PROJECT,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));
  }

  //---------------------------------------------------------------------------
  // listReviewers.
  //---------------------------------------------------------------------------

  @Test
  public void whenUserNotAllowedToActivateRole_ThenListReviewersThrowsException() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzer.class);
    when(policyAnalyzer.findEntitlements(
      eq(SAMPLE_REQUESTING_USER),
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

    assertThrows(
      AccessDeniedException.class,
      () -> catalog.listReviewers(SAMPLE_REQUESTING_USER, new ProjectRoleId(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE))));
  }

  @Test
  public void whenUserAllowedToActivateRoleWithoutMpa_ThenListReviewersReturnsList() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzer.class);
    var role = new ProjectRoleId(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE));
    when(policyAnalyzer.findEntitlements(
      eq(SAMPLE_REQUESTING_USER),
      eq(SAMPLE_PROJECT),
      any()))
      .thenReturn(new Annotated<>(
        new TreeSet<>(Set.of(new Entitlement<>(
          role,
          "-",
          ActivationType.JIT,
          Entitlement.Status.AVAILABLE))),
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

    assertThrows(
      AccessDeniedException.class,
      () -> catalog.listReviewers(SAMPLE_REQUESTING_USER, role));
  }

  @Test
  public void whenUserAllowedToActivateRole_ThenListReviewersReturnsList() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzer.class);
    var role = new ProjectRoleId(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE));
    when(policyAnalyzer.findEntitlements(
      eq(SAMPLE_REQUESTING_USER),
      eq(SAMPLE_PROJECT),
      any()))
      .thenReturn(new Annotated<>(
        new TreeSet<>(Set.of(new Entitlement<>(
          role,
          "-",
          ActivationType.MPA,
          Entitlement.Status.AVAILABLE))),
        Set.of()));
    when(policyAnalyzer.findApproversForEntitlement(eq(role.roleBinding())))
        .thenReturn(Set.of(SAMPLE_APPROVIING_USER, SAMPLE_REQUESTING_USER));

    var catalog = new IamPolicyCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new IamPolicyCatalog.Options(
        null,
        Duration.ofMinutes(5),
        1,
        1)
    );

    var reviewers = catalog.listReviewers(SAMPLE_REQUESTING_USER, role);
    assertIterableEquals(
      Set.of(SAMPLE_APPROVIING_USER),
      reviewers);
  }
}
