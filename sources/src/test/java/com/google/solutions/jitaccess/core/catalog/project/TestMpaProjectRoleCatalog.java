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
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestMpaProjectRoleCatalog {

  private static final UserId SAMPLE_REQUESTING_USER = new UserId("user@example.com");
  private static final UserId SAMPLE_APPROVING_USER = new UserId("approver@example.com");
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
        eq(EnumSet.of(ActivationType.JIT))))
      .thenReturn(EntitlementSet.empty());

    assertThrows(
      AccessDeniedException.class,
      () -> catalog.verifyUserCanActivateEntitlements(
        SAMPLE_REQUESTING_USER,
        SAMPLE_PROJECT,
        ActivationType.JIT,
        List.of(new ProjectRole(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)))));
  }

  @Test
  public void whenActivationTypeMismatches_ThenVerifyUserCanActivateEntitlementsThrowsException() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var mpaEntitlement = new Entitlement<>(
      new ProjectRole(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      ActivationType.MPA);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.JIT))))
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

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    when(policyAnalyzer
      .findEntitlements(
        eq(requestingUserContext.user()),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.MPA))))
      .thenReturn(EntitlementSet.empty());

    assertThrows(
      AccessDeniedException.class,
      () -> catalog.listReviewers(
        requestingUserContext,
        new ProjectRole(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE))));
  }

  @Test
  public void whenUserAllowedToActivateEntitlement_ThenListReviewersExcludesUser() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var mpaEntitlement = new Entitlement<>(
      new ProjectRole(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      ActivationType.MPA);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.MPA))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(mpaEntitlement)),
        Map.of(),
        Map.of(),
        Set.of()));

    when(policyAnalyzer
      .findEntitlementHolders(
        eq(mpaEntitlement.id()),
        eq(ActivationType.MPA)))
      .thenReturn(Set.of(SAMPLE_REQUESTING_USER, SAMPLE_APPROVING_USER));

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    var reviewers = catalog.listReviewers(requestingUserContext, mpaEntitlement.id());
    assertIterableEquals(Set.of(SAMPLE_APPROVING_USER), reviewers);
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
      new ProjectRole(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      ActivationType.JIT);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.MPA))))
      .thenReturn(EntitlementSet.empty());

    var request = Mockito.mock(JitActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.type()).thenReturn(ActivationType.MPA); // mismatch
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.entitlements()).thenReturn(Set.of(jitEntitlement.id()));

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    assertThrows(
      AccessDeniedException.class,
      () -> catalog.verifyUserCanRequest(requestingUserContext, request));
  }

  @Test
  public void whenUserAllowedToActivate_ThenVerifyUserCanRequestReturns() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var jitEntitlement = new Entitlement<>(
      new ProjectRole(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      ActivationType.JIT);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.JIT))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(jitEntitlement)),
        Map.of(),
        Map.of(),
        Set.of()));

    var request = Mockito.mock(JitActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.type()).thenReturn(ActivationType.JIT);
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.entitlements()).thenReturn(Set.of(jitEntitlement.id()));

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    catalog.verifyUserCanRequest(requestingUserContext, request);
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
      new ProjectRole(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      ActivationType.MPA);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_APPROVING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.MPA))))
      .thenReturn(EntitlementSet.empty());

    var request = Mockito.mock(MpaActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.type()).thenReturn(ActivationType.MPA);
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.reviewers()).thenReturn(Set.of(SAMPLE_APPROVING_USER));
    when(request.entitlements()).thenReturn(Set.of(mpaEntitlement.id()));

    var approvingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_APPROVING_USER);
    assertThrows(
      AccessDeniedException.class,
      () -> catalog.verifyUserCanApprove(approvingUserContext, request));
  }

  @Test
  public void whenUserAllowedToActivate_ThenVerifyUserCanApproveReturns() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var mpaEntitlement = new Entitlement<>(
      new ProjectRole(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      ActivationType.MPA);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_APPROVING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(ActivationType.MPA))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(mpaEntitlement)),
        Map.of(),
        Map.of(),
        Set.of()));

    var request = Mockito.mock(MpaActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.type()).thenReturn(ActivationType.MPA);
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.reviewers()).thenReturn(Set.of(SAMPLE_APPROVING_USER));
    when(request.entitlements()).thenReturn(Set.of(mpaEntitlement.id()));

    var approvingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_APPROVING_USER);
    catalog.verifyUserCanApprove(approvingUserContext, request);
  }

  //---------------------------------------------------------------------------
  // listScopes.
  //---------------------------------------------------------------------------

  @Test
  public void whenProjectQueryProvided_thenListScopesPerformsProjectSearch() throws Exception {
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

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    var projects = catalog.listScopes(requestingUserContext);
    assertIterableEquals(
      List.of( // Sorted
        new ProjectId("project-1"),
        new ProjectId("project-2"),
        new ProjectId("project-3")),
      projects);
  }

  @Test
  public void whenProjectQueryNotProvided_thenListScopesPerformsPolicySearch() throws Exception {
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

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    var projects = catalog.listScopes(requestingUserContext);
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
      eq(EnumSet.of(ActivationType.JIT, ActivationType.MPA))))
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

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    var entitlements = catalog.listEntitlements(requestingUserContext, SAMPLE_PROJECT);
    assertNotNull(entitlements);

    verify(policyAnalyzer, times(1)).findEntitlements(
      SAMPLE_REQUESTING_USER,
      SAMPLE_PROJECT,
      EnumSet.of(ActivationType.JIT, ActivationType.MPA));
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
      eq(EnumSet.of(ActivationType.MPA))))
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

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    assertThrows(
      AccessDeniedException.class,
      () -> catalog.listReviewers(
        requestingUserContext,
        new ProjectRole(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE))));
  }

  @Test
  public void whenUserAllowedToActivateRoleWithoutMpa_ThenListReviewersReturnsList() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
    var role = new ProjectRole(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE));
    when(policyAnalyzer.findEntitlements(
      eq(SAMPLE_REQUESTING_USER),
      eq(SAMPLE_PROJECT),
      eq(EnumSet.of(ActivationType.MPA))))
      .thenReturn(new EntitlementSet<>((
        new TreeSet<>(Set.of(new Entitlement<>(
          new ProjectRole(new RoleBinding(SAMPLE_PROJECT, "roles/different-role")),
          "-",
          ActivationType.MPA)))),
        Map.of(),
        Map.of(),
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

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    assertThrows(
      AccessDeniedException.class,
      () -> catalog.listReviewers(requestingUserContext, role));
  }

  @Test
  public void whenUserAllowedToActivateRole_ThenListReviewersReturnsList() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
    var role = new ProjectRole(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE));
    when(policyAnalyzer.findEntitlements(
      eq(SAMPLE_REQUESTING_USER),
      eq(SAMPLE_PROJECT),
      eq(EnumSet.of(ActivationType.MPA))))
      .thenReturn(new EntitlementSet<>((
        new TreeSet<>(Set.of(new Entitlement<>(
          role,
          "-",
          ActivationType.MPA)))),
        Map.of(),
        Map.of(),
        Set.of()));
    when(policyAnalyzer
      .findEntitlementHolders(
        eq(role),
        eq(ActivationType.MPA)))
      .thenReturn(Set.of(SAMPLE_APPROVING_USER, SAMPLE_REQUESTING_USER));

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(
        null,
        Duration.ofMinutes(5),
        1,
        1)
    );

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    var reviewers = catalog.listReviewers(requestingUserContext, role);
    assertIterableEquals(
      Set.of(SAMPLE_APPROVING_USER),
      reviewers);
  }
}
