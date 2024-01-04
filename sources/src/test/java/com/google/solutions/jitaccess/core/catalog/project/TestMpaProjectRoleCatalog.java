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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
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

public class TestMpaProjectRoleCatalog {

  private static final UserId SAMPLE_REQUESTING_USER = new UserId("user@example.com");
  private static final UserId SAMPLE_APPROVIING_USER = new UserId("approver@example.com");
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");
  private static final String SAMPLE_ROLE = "roles/resourcemanager.role1";

  //---------------------------------------------------------------------------
  // validateRequest.
  //---------------------------------------------------------------------------

  @Test
  public void whenDurationExceedsMax_ThenValidateRequestThrowsException() throws Exception {
    var catalog = new MpaProjectRoleCatalog(
      Mockito.mock(PolicyAnalyzerRepository.class),
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(
        null,
        Duration.ofMinutes(30),
        1,
        2
      ));

      var request = Mockito.mock(ActivationRequest.class);
      when (request.duration()).thenReturn(catalog.options().maxActivationDuration().plusMinutes(1));

      assertThrows(
        IllegalArgumentException.class,
        () -> catalog.validateRequest(request));
  }

  @Test
  public void whenDurationBelowMin_ThenValidateRequestThrowsException() throws Exception {
    var catalog = new MpaProjectRoleCatalog(
      Mockito.mock(PolicyAnalyzerRepository.class),
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(
        null,
        Duration.ofMinutes(30),
        1,
        2
      ));

    var request = Mockito.mock(ActivationRequest.class);
    when (request.duration()).thenReturn(catalog.options().minActivationDuration().minusMinutes(1));

    assertThrows(
      IllegalArgumentException.class,
      () -> catalog.validateRequest(request));
  }

  @Test
  public void whenReviewersMissing_ThenValidateRequestThrowsException() throws Exception {
    var catalog = new MpaProjectRoleCatalog(
      Mockito.mock(PolicyAnalyzerRepository.class),
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(
        null,
        Duration.ofMinutes(30),
        1,
        2
      ));

    var request = Mockito.mock(MpaActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.reviewers()).thenReturn(null);

    assertThrows(
      IllegalArgumentException.class,
      () -> catalog.validateRequest(request));
  }

  @Test
  public void whenNumberOfReviewersExceedsMax_ThenValidateRequestThrowsException() throws Exception {
    var catalog = new MpaProjectRoleCatalog(
      Mockito.mock(PolicyAnalyzerRepository.class),
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(
        null,
        Duration.ofMinutes(30),
        1,
        2
      ));

    var request = Mockito.mock(MpaActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.reviewers()).thenReturn(Set.of(
      new UserId("user-1@example.com"),
      new UserId("user-2@example.com"),
      new UserId("user-3@example.com")));

    assertThrows(
      IllegalArgumentException.class,
      () -> catalog.validateRequest(request));
  }

  @Test
  public void whenNumberOfReviewersBelowMin_ThenValidateRequestThrowsException() throws Exception {
    var catalog = new MpaProjectRoleCatalog(
      Mockito.mock(PolicyAnalyzerRepository.class),
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(
        null,
        Duration.ofMinutes(30),
        2,
        2
      ));

    var request = Mockito.mock(MpaActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.reviewers()).thenReturn(Set.of(
      new UserId("user-1@example.com")));

    assertThrows(
      IllegalArgumentException.class,
      () -> catalog.validateRequest(request));
  }

  @Test
  public void whenNumberOfReviewersOk_ThenValidateRequestReturns() throws Exception {
    var catalog = new MpaProjectRoleCatalog(
      Mockito.mock(PolicyAnalyzerRepository.class),
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(
        null,
        Duration.ofMinutes(30),
        1,
        2
      ));

    var request = Mockito.mock(MpaActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.reviewers()).thenReturn(Set.of(
      new UserId("user-1@example.com")));

    catalog.validateRequest(request);
  }

  //---------------------------------------------------------------------------
  // verifyUserCanActivateEntitlements.
  //---------------------------------------------------------------------------

  @Test
  public void whenEntitlementNotFound_ThenVerifyUserCanActivateEntitlementsThrowsException() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.JIT)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(EntitlementSet.empty());

    assertThrows(
      AccessDeniedException.class,
      () -> catalog.verifyUserCanActivateEntitlements(
        SAMPLE_REQUESTING_USER,
        SAMPLE_PROJECT,
        ActivationType.JIT,
        List.of(new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)))));
  }

  @Test
  public void whenActivationTypeMismatches_ThenVerifyUserCanActivateEntitlementsThrowsException() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var mpaEntitlement = new Entitlement<>(
      new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      ActivationType.MPA,
      Entitlement.Status.AVAILABLE);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.JIT)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(EntitlementSet.empty());

    assertThrows(
      AccessDeniedException.class,
      () -> catalog.verifyUserCanActivateEntitlements(
        SAMPLE_REQUESTING_USER,
        SAMPLE_PROJECT,
        ActivationType.JIT,
        List.of(mpaEntitlement.id())));
  }

  //---------------------------------------------------------------------------
  // listReviewers.
  //---------------------------------------------------------------------------

  @Test
  public void whenUserNotAllowedToActivateEntitlement_ThenListReviewersThrowsException() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.MPA)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(EntitlementSet.empty());

    assertThrows(
      AccessDeniedException.class,
      () -> catalog.listReviewers(
        SAMPLE_REQUESTING_USER,
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE))));
  }

  @Test
  public void whenUserAllowedToActivateEntitlement_ThenListReviewersExcludesUser() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var mpaEntitlement = new Entitlement<>(
      new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      ActivationType.MPA,
      Entitlement.Status.AVAILABLE);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.MPA)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(mpaEntitlement)),
        Set.of(),
        Set.of()));

    when(policyAnalyzer
      .findEntitlementHolders(
        eq(mpaEntitlement.id()),
        eq(ActivationType.MPA)))
      .thenReturn(Set.of(SAMPLE_REQUESTING_USER, SAMPLE_APPROVIING_USER));

    var reviewers = catalog.listReviewers(SAMPLE_REQUESTING_USER, mpaEntitlement.id());
    assertIterableEquals(Set.of(SAMPLE_APPROVIING_USER), reviewers);
  }

  //---------------------------------------------------------------------------
  // verifyUserCanRequest.
  //---------------------------------------------------------------------------

  @Test
  public void whenUserNotAllowedToActivate_ThenVerifyUserCanRequestThrowsException() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var jitEntitlement = new Entitlement<>(
      new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.MPA)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(EntitlementSet.empty());

    var request = Mockito.mock(JitActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.type()).thenReturn(ActivationType.MPA); // mismatch
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.entitlements()).thenReturn(Set.of(jitEntitlement.id()));

    assertThrows(
      AccessDeniedException.class,
      () -> catalog.verifyUserCanRequest(request));
  }

  @Test
  public void whenUserAllowedToActivate_ThenVerifyUserCanRequestReturns() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var jitEntitlement = new Entitlement<>(
      new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.JIT)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(jitEntitlement)),
        Set.of(),
        Set.of()));

    var request = Mockito.mock(JitActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.type()).thenReturn(ActivationType.JIT);
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.entitlements()).thenReturn(Set.of(jitEntitlement.id()));

    catalog.verifyUserCanRequest(request);
  }

  //---------------------------------------------------------------------------
  // verifyUserCanApprove.
  //---------------------------------------------------------------------------

  @Test
  public void whenUserNotAllowedToActivate_ThenVerifyUserCanApproveThrowsException() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var mpaEntitlement = new Entitlement<>(
      new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      ActivationType.MPA,
      Entitlement.Status.AVAILABLE);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_APPROVIING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.MPA)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(EntitlementSet.empty());

    var request = Mockito.mock(MpaActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.type()).thenReturn(ActivationType.MPA);
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.reviewers()).thenReturn(Set.of(SAMPLE_APPROVIING_USER));
    when(request.entitlements()).thenReturn(Set.of(mpaEntitlement.id()));

    assertThrows(
      AccessDeniedException.class,
      () -> catalog.verifyUserCanApprove(SAMPLE_APPROVIING_USER, request));
  }

  @Test
  public void whenUserAllowedToActivate_ThenVerifyUserCanApproveReturns() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var mpaEntitlement = new Entitlement<>(
      new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      ActivationType.MPA,
      Entitlement.Status.AVAILABLE);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_APPROVIING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.MPA)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(mpaEntitlement)),
        Set.of(),
        Set.of()));

    var request = Mockito.mock(MpaActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.type()).thenReturn(ActivationType.MPA);
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.reviewers()).thenReturn(Set.of(SAMPLE_APPROVIING_USER));
    when(request.entitlements()).thenReturn(Set.of(mpaEntitlement.id()));

    catalog.verifyUserCanApprove(SAMPLE_APPROVIING_USER, request);
  }

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

    var catalog = new MpaProjectRoleCatalog(
      Mockito.mock(PolicyAnalyzerRepository.class),
      resourceManager,
      new MpaProjectRoleCatalog.Options(
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
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
    when(policyAnalyzer.findProjectsWithEntitlements(eq(SAMPLE_REQUESTING_USER)))
      .thenReturn(new TreeSet<>(Set.of(
        new ProjectId("project-2"),
        new ProjectId("project-3"),
        new ProjectId("project-1"))));

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(
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
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
    when(policyAnalyzer.findEntitlements(
      eq(SAMPLE_REQUESTING_USER),
      eq(SAMPLE_PROJECT),
      eq(EnumSet.of(ActivationType.JIT, ActivationType.MPA)),
      any()))
      .thenReturn(EntitlementSet.empty());

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(
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
      EnumSet.of(ActivationType.JIT, ActivationType.MPA),
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));
  }

  //---------------------------------------------------------------------------
  // listReviewers.
  //---------------------------------------------------------------------------

  @Test
  public void whenUserNotAllowedToActivateRole_ThenListReviewersThrowsException() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
    when(policyAnalyzer.findEntitlements(
      eq(SAMPLE_REQUESTING_USER),
      eq(SAMPLE_PROJECT),
      eq(EnumSet.of(ActivationType.MPA)),
      any()))
      .thenReturn(EntitlementSet.empty());

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(
        null,
        Duration.ofMinutes(5),
        1,
        1)
    );

    assertThrows(
      AccessDeniedException.class,
      () -> catalog.listReviewers(SAMPLE_REQUESTING_USER, new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE))));
  }

  @Test
  public void whenUserAllowedToActivateRoleWithoutMpa_ThenListReviewersReturnsList() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
    var role = new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE));
    when(policyAnalyzer.findEntitlements(
      eq(SAMPLE_REQUESTING_USER),
      eq(SAMPLE_PROJECT),
      eq(EnumSet.of(ActivationType.MPA)),
      any()))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(new Entitlement<>(
          new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, "roles/different-role")),
          "-",
          ActivationType.MPA,
          Entitlement.Status.AVAILABLE))),
        Set.of(),
        Set.of()));

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(
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
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
    var role = new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE));
    when(policyAnalyzer.findEntitlements(
      eq(SAMPLE_REQUESTING_USER),
      eq(SAMPLE_PROJECT),
      eq(EnumSet.of(ActivationType.MPA)),
      any()))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(new Entitlement<>(
          role,
          "-",
          ActivationType.MPA,
          Entitlement.Status.AVAILABLE))),
        Set.of(),
        Set.of()));
    when(policyAnalyzer
      .findEntitlementHolders(
        eq(role),
        eq(ActivationType.MPA)))
      .thenReturn(Set.of(SAMPLE_APPROVIING_USER, SAMPLE_REQUESTING_USER));

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(
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
