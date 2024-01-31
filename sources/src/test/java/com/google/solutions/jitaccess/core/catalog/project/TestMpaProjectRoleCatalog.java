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
import com.google.solutions.jitaccess.core.catalog.Entitlement.Status;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    var request = Mockito.mock(PeerApprovalActivationRequest.class);
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

    var request = Mockito.mock(PeerApprovalActivationRequest.class);
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

    var request = Mockito.mock(PeerApprovalActivationRequest.class);
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

    var request = Mockito.mock(PeerApprovalActivationRequest.class);
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
        eq(EnumSet.of(EntitlementType.JIT)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(EntitlementSet.empty());

    assertThrows(
      AccessDeniedException.class,
      () -> catalog.verifyUserCanActivateEntitlements(
        SAMPLE_REQUESTING_USER,
        SAMPLE_PROJECT,
        ActivationType.SELF_APPROVAL,
        List.of(new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)))));
  }

  @Test
  public void whenActivationTypeMismatches_ThenVerifyUserCanActivateEntitlementsThrowsException() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var peerEntitlement = new Entitlement<>(
      new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      EntitlementType.PEER,
      Entitlement.Status.AVAILABLE);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(EntitlementType.JIT)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(EntitlementSet.empty());

    assertThrows(
      AccessDeniedException.class,
      () -> catalog.verifyUserCanActivateEntitlements(
        SAMPLE_REQUESTING_USER,
        SAMPLE_PROJECT,
        ActivationType.SELF_APPROVAL,
        List.of(peerEntitlement.id())));
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
      EntitlementType.JIT,
      Entitlement.Status.AVAILABLE);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(EntitlementType.PEER)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(EntitlementSet.empty());

    var request = Mockito.mock(SelfApprovalActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.type()).thenReturn(ActivationType.PEER_APPROVAL); // mismatch
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
      EntitlementType.JIT,
      Entitlement.Status.AVAILABLE);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(EntitlementType.JIT)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(jitEntitlement)),
        Set.of(),
        Set.of()));

    var request = Mockito.mock(SelfApprovalActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.type()).thenReturn(ActivationType.SELF_APPROVAL);
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

    var peerEntitlement = new Entitlement<>(
      new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      EntitlementType.PEER,
      Entitlement.Status.AVAILABLE);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_APPROVING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(EntitlementType.PEER)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(EntitlementSet.empty());

    var request = Mockito.mock(PeerApprovalActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.type()).thenReturn(ActivationType.PEER_APPROVAL);
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.reviewers()).thenReturn(Set.of(SAMPLE_APPROVING_USER));
    when(request.entitlements()).thenReturn(Set.of(peerEntitlement.id()));

    assertThrows(
      AccessDeniedException.class,
      () -> catalog.verifyUserCanApprove(SAMPLE_APPROVING_USER, request));
  }

  @Test
  public void whenUserAllowedToActivate_ThenVerifyUserCanApproveReturns() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var peerEntitlement = new Entitlement<>(
      new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      EntitlementType.PEER,
      Entitlement.Status.AVAILABLE);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_APPROVING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(EntitlementType.PEER)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(peerEntitlement)),
        Set.of(),
        Set.of()));

    var request = Mockito.mock(PeerApprovalActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.type()).thenReturn(ActivationType.PEER_APPROVAL);
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.reviewers()).thenReturn(Set.of(SAMPLE_APPROVING_USER));
    when(request.entitlements()).thenReturn(Set.of(peerEntitlement.id()));

    catalog.verifyUserCanApprove(SAMPLE_APPROVING_USER, request);
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
      eq(EnumSet.of(EntitlementType.JIT, EntitlementType.PEER, EntitlementType.REQUESTER)),
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
      EnumSet.of(EntitlementType.JIT, EntitlementType.PEER, EntitlementType.REQUESTER),
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
      eq(EnumSet.of(EntitlementType.PEER)),
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

    var roleBinding = new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE);
    assertThrows(
      AccessDeniedException.class,
      () -> catalog.listReviewers(SAMPLE_REQUESTING_USER, new Entitlement<ProjectRoleBinding>(new ProjectRoleBinding(roleBinding), roleBinding.role(), EntitlementType.PEER, Status.AVAILABLE)));
  }

  @Test
  public void whenUserAllowedToActivateRoleWithoutMpa_ThenListReviewersReturnsList() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
    var roleBinding = new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE);
    var entitlement = new Entitlement<ProjectRoleBinding>(new ProjectRoleBinding(roleBinding), roleBinding.role(), EntitlementType.PEER, Status.AVAILABLE);
    when(policyAnalyzer.findEntitlements(
      eq(SAMPLE_REQUESTING_USER),
      eq(SAMPLE_PROJECT),
      eq(EnumSet.of(EntitlementType.PEER)),
      any()))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(new Entitlement<>(
          new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, "roles/different-role")),
          "-",
          EntitlementType.PEER,
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
      () -> catalog.listReviewers(SAMPLE_REQUESTING_USER, entitlement));
  }

  @Test
  public void whenUserAllowedToActivatePeerEntitlement_ThenListReviewersExcludesUser() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var peerEntitlement = new Entitlement<>(
      new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      EntitlementType.PEER,
      Entitlement.Status.AVAILABLE);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(EntitlementType.PEER)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(peerEntitlement)),
        Set.of(),
        Set.of()));

    when(policyAnalyzer
      .findEntitlementHolders(
        eq(peerEntitlement.id()),
        eq(EntitlementType.PEER)))
      .thenReturn(Set.of(SAMPLE_REQUESTING_USER, SAMPLE_APPROVING_USER));

    var reviewers = catalog.listReviewers(SAMPLE_REQUESTING_USER, peerEntitlement);
    assertIterableEquals(Set.of(SAMPLE_APPROVING_USER), reviewers);
  }

  @Test
  public void whenUserAllowedToActivateExternalEntitlement_ThenListReviewersIEncludesReviewers() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
      policyAnalyzer,
      Mockito.mock(ResourceManagerClient.class),
      new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var externalApprovalEntitlement = new Entitlement<>(
      new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      EntitlementType.REQUESTER,
      Entitlement.Status.AVAILABLE);

    var reviewerEntitlement = new Entitlement<>(
      new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
      "-",
      EntitlementType.REVIEWER,
      Entitlement.Status.AVAILABLE);

    when(policyAnalyzer
      .findEntitlements(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(EnumSet.of(EntitlementType.REQUESTER)),
        eq(EnumSet.of(Entitlement.Status.AVAILABLE))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(externalApprovalEntitlement)),
        Set.of(),
        Set.of()));

    when(policyAnalyzer
      .findEntitlementHolders(
        eq(reviewerEntitlement.id()),
        eq(EntitlementType.REVIEWER)))
      .thenReturn(Set.of(SAMPLE_REQUESTING_USER, SAMPLE_APPROVING_USER));

    var reviewers = catalog.listReviewers(SAMPLE_REQUESTING_USER, externalApprovalEntitlement);
    assertIterableEquals(Set.of(SAMPLE_APPROVING_USER), reviewers);
  }
}
