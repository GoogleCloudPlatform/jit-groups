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
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege.Status;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestMpaProjectRoleCatalog {

  private static final UserEmail SAMPLE_REQUESTING_USER = new UserEmail("user@example.com");
  private static final UserEmail SAMPLE_APPROVING_USER = new UserEmail("approver@example.com");
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");
  private static final String SAMPLE_ROLE = "roles/resourcemanager.role1";

  // ---------------------------------------------------------------------------
  // validateRequest.
  // ---------------------------------------------------------------------------

  @Test
  public void whenDurationExceedsMax_ThenValidateRequestThrowsException() throws Exception {
    var catalog = new MpaProjectRoleCatalog(
        Mockito.mock(PolicyAnalyzerRepository.class),
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(
            null,
            Duration.ofMinutes(30),
            1,
            2));

    var request = Mockito.mock(ActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().maxActivationDuration().plusMinutes(1));

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
            2));

    var request = Mockito.mock(ActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration().minusMinutes(1));

    assertThrows(
        IllegalArgumentException.class,
        () -> catalog.validateRequest(request));
  }

  @Test
  public void whenReviewersMissingAndTypeSelfApproval_ThenValidateRequestReturns() throws Exception {
    var catalog = new MpaProjectRoleCatalog(
        Mockito.mock(PolicyAnalyzerRepository.class),
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(
            null,
            Duration.ofMinutes(30),
            1,
            2));

    var request = Mockito.mock(ActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.reviewers()).thenReturn(null);
    when(request.activationType()).thenReturn(new SelfApproval());

    assertDoesNotThrow(() -> catalog.validateRequest(request));
  }

  @Test
  public void whenReviewersMissingAndTypeRequiresApproval_ThenValidateRequestThrowsException() throws Exception {
    var catalog = new MpaProjectRoleCatalog(
        Mockito.mock(PolicyAnalyzerRepository.class),
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(
            null,
            Duration.ofMinutes(30),
            1,
            2));

    var request = Mockito.mock(ActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.reviewers()).thenReturn(null);
    when(request.activationType()).thenReturn(new ExternalApproval("topic"));

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
            2));

    var request = Mockito.mock(ActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.reviewers()).thenReturn(Set.of(
        new UserEmail("user-1@example.com"),
        new UserEmail("user-2@example.com"),
        new UserEmail("user-3@example.com")));
    when(request.activationType()).thenReturn(new ExternalApproval("topic"));

    assertThrows(
        IllegalArgumentException.class,
        () -> catalog.validateRequest(request));
  }

  @Test
  public void whenNumberOfReviewersBelowMinAndRequiresApproval_ThenValidateRequestThrowsException() throws Exception {
    var catalog = new MpaProjectRoleCatalog(
        Mockito.mock(PolicyAnalyzerRepository.class),
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(
            null,
            Duration.ofMinutes(30),
            2,
            2));

    var request = Mockito.mock(ActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.reviewers()).thenReturn(Set.of(
        new UserEmail("user-1@example.com")));
    when(request.activationType()).thenReturn(new PeerApproval("topic"));

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
            2));

    var request = Mockito.mock(ActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.reviewers()).thenReturn(Set.of(
        new UserEmail("user-1@example.com")));
    when(request.activationType()).thenReturn(new ExternalApproval("topic"));

    catalog.validateRequest(request);
  }

  // ---------------------------------------------------------------------------
  // verifyUserCanActivatePrivileges.
  // ---------------------------------------------------------------------------

  @Test
  public void whenPrivilegeNotFound_ThenVerifyUserCanActivateRequesterPrivilegesThrowsException() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
        policyAnalyzer,
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var selfApproval = new SelfApproval();

    when(policyAnalyzer
        .findRequesterPrivileges(
            eq(SAMPLE_REQUESTING_USER),
            eq(SAMPLE_PROJECT),
            eq(Set.of(selfApproval)),
            eq(EnumSet.of(RequesterPrivilege.Status.INACTIVE))))
        .thenReturn(RequesterPrivilegeSet.empty());

    assertThrows(
        AccessDeniedException.class,
        () -> catalog.verifyUserCanActivateRequesterPrivileges(
            SAMPLE_REQUESTING_USER,
            SAMPLE_PROJECT,
            selfApproval,
            List.of(new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)))));
  }

  @Test
  public void whenActivationTypeMismatches_ThenVerifyUserCanActivateRequesterPrivilegesThrowsException()
      throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
        policyAnalyzer,
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var peerApprovalPrivilege = new RequesterPrivilege<>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
        "-",
        new PeerApproval("topic"),
        RequesterPrivilege.Status.INACTIVE);

    var selfApproval = new SelfApproval();

    when(policyAnalyzer
        .findRequesterPrivileges(
            eq(SAMPLE_REQUESTING_USER),
            eq(SAMPLE_PROJECT),
            eq(Set.of(selfApproval)),
            eq(EnumSet.of(RequesterPrivilege.Status.INACTIVE))))
        .thenReturn(RequesterPrivilegeSet.empty());

    assertThrows(
        AccessDeniedException.class,
        () -> catalog.verifyUserCanActivateRequesterPrivileges(
            SAMPLE_REQUESTING_USER,
            SAMPLE_PROJECT,
            selfApproval,
            List.of(peerApprovalPrivilege.id())));
  }

  @Test
  public void whenTopicMismatches_ThenVerifyUserCanActivateRequesterPrivilegesThrowsException()
      throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
        policyAnalyzer,
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var peerApprovalPrivilege = new RequesterPrivilege<>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
        "-",
        new PeerApproval("topic"),
        RequesterPrivilege.Status.INACTIVE);

    var peerApproval = new PeerApproval("topic2");

    when(policyAnalyzer
        .findRequesterPrivileges(
            eq(SAMPLE_REQUESTING_USER),
            eq(SAMPLE_PROJECT),
            eq(Set.of(peerApproval)),
            eq(EnumSet.of(RequesterPrivilege.Status.INACTIVE))))
        .thenReturn(RequesterPrivilegeSet.empty());

    assertThrows(
        AccessDeniedException.class,
        () -> catalog.verifyUserCanActivateRequesterPrivileges(
            SAMPLE_REQUESTING_USER,
            SAMPLE_PROJECT,
            peerApproval,
            List.of(peerApprovalPrivilege.id())));
  }

  @Test
  public void whenUserHasTopicEmpty_ThenVerifyUserCanActivateRequesterPrivilegesReturns()
      throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
        policyAnalyzer,
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var peerApprovalPrivilege = new RequesterPrivilege<>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
        "-",
        new PeerApproval(""),
        RequesterPrivilege.Status.INACTIVE);

    var peerApproval = new PeerApproval("topic");

    when(policyAnalyzer
        .findRequesterPrivileges(
            eq(SAMPLE_REQUESTING_USER),
            eq(SAMPLE_PROJECT),
            eq(Set.of(peerApproval)),
            eq(EnumSet.of(RequesterPrivilege.Status.INACTIVE))))
        .thenReturn(new RequesterPrivilegeSet<>(
            new TreeSet(Set.of(peerApprovalPrivilege)),
            new TreeSet<>(),
            Set.of()));

    assertDoesNotThrow(
        () -> catalog.verifyUserCanActivateRequesterPrivileges(
            SAMPLE_REQUESTING_USER,
            SAMPLE_PROJECT,
            peerApproval,
            List.of(peerApprovalPrivilege.id())));
  }

  @Test
  public void whenRequestHasTopicEmpty_ThenVerifyUserCanActivateRequesterPrivilegesThrows()
      throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
        policyAnalyzer,
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var peerApprovalPrivilege = new RequesterPrivilege<>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
        "-",
        new PeerApproval("topic"),
        RequesterPrivilege.Status.INACTIVE);

    var peerApproval = new PeerApproval("");

    when(policyAnalyzer
        .findRequesterPrivileges(
            eq(SAMPLE_REQUESTING_USER),
            eq(SAMPLE_PROJECT),
            eq(Set.of(peerApproval)),
            eq(EnumSet.of(RequesterPrivilege.Status.INACTIVE))))
        .thenReturn(new RequesterPrivilegeSet<>(
            new TreeSet<>(Set.of(peerApprovalPrivilege)),
            new TreeSet<>(),
            Set.of()));

    assertThrows(
        AccessDeniedException.class,
        () -> catalog.verifyUserCanActivateRequesterPrivileges(
            SAMPLE_REQUESTING_USER,
            SAMPLE_PROJECT,
            peerApproval,
            List.of(peerApprovalPrivilege.id())));
  }

  // ---------------------------------------------------------------------------
  // verifyUserCanRequest.
  // ---------------------------------------------------------------------------

  @Test
  public void whenUserNotAllowedToActivate_ThenVerifyUserCanRequestThrowsException() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
        policyAnalyzer,
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var selfApprovalPrivilege = new RequesterPrivilege<>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
        "-",
        new SelfApproval(),
        RequesterPrivilege.Status.INACTIVE);

    var selfApproval = new SelfApproval();

    when(policyAnalyzer
        .findRequesterPrivileges(
            eq(SAMPLE_REQUESTING_USER),
            eq(SAMPLE_PROJECT),
            eq(Set.of(selfApproval)),
            eq(EnumSet.of(RequesterPrivilege.Status.INACTIVE))))
        .thenReturn(RequesterPrivilegeSet.empty());

    var request = Mockito.mock(ActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.requesterPrivilege()).thenReturn(selfApprovalPrivilege.id());
    when(request.activationType()).thenReturn(selfApproval);

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

    var selfApprovalPrivilege = new RequesterPrivilege<>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
        "-",
        new SelfApproval(),
        RequesterPrivilege.Status.INACTIVE);

    var selfApproval = new SelfApproval();

    when(policyAnalyzer
        .findRequesterPrivileges(
            eq(SAMPLE_REQUESTING_USER),
            eq(SAMPLE_PROJECT),
            eq(Set.of(selfApproval)),
            eq(EnumSet.of(RequesterPrivilege.Status.INACTIVE))))
        .thenReturn(new RequesterPrivilegeSet(
            new TreeSet<>(Set.of(selfApprovalPrivilege)),
            new TreeSet<>(),
            Set.of()));

    var request = Mockito.mock(ActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.requesterPrivilege()).thenReturn(selfApprovalPrivilege.id());
    when(request.activationType()).thenReturn(selfApproval);

    catalog.verifyUserCanRequest(request);
  }

  // ---------------------------------------------------------------------------
  // verifyUserCanApprove.
  // ---------------------------------------------------------------------------

  @Test
  public void whenUserNotAllowedToActivate_ThenVerifyUserCanApproveThrowsException() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
        policyAnalyzer,
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var peerApprovalPrivilege = new RequesterPrivilege<>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
        "-",
        new PeerApproval("topic"),
        RequesterPrivilege.Status.INACTIVE);

    when(policyAnalyzer
        .findReviewerPrivelegeHolders(
            peerApprovalPrivilege.id(),
            peerApprovalPrivilege.activationType()))
        .thenReturn(Set.of());

    var request = Mockito.mock(ActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.reviewers()).thenReturn(Set.of(SAMPLE_APPROVING_USER));
    when(request.requesterPrivilege()).thenReturn(peerApprovalPrivilege.id());
    when(request.activationType()).thenReturn(new PeerApproval("topic"));

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

    var peerApproval = new PeerApproval("topic");

    var peerApprovalPrivilege = new RequesterPrivilege<>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
        "-",
        peerApproval,
        RequesterPrivilege.Status.INACTIVE);

    when(policyAnalyzer
        .findReviewerPrivelegeHolders(
            peerApprovalPrivilege.id(),
            peerApprovalPrivilege.activationType()))
        .thenReturn(Set.of(SAMPLE_APPROVING_USER));

    var request = Mockito.mock(ActivationRequest.class);
    when(request.duration()).thenReturn(catalog.options().minActivationDuration());
    when(request.requestingUser()).thenReturn(SAMPLE_REQUESTING_USER);
    when(request.reviewers()).thenReturn(Set.of(SAMPLE_APPROVING_USER));
    when(request.requesterPrivilege()).thenReturn(peerApprovalPrivilege.id());
    when(request.activationType()).thenReturn(peerApproval);

    catalog.verifyUserCanApprove(SAMPLE_APPROVING_USER, request);
  }

  // ---------------------------------------------------------------------------
  // listScopes.
  // ---------------------------------------------------------------------------

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
            1));

    var projects = catalog.listScopes(SAMPLE_REQUESTING_USER);
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
    when(policyAnalyzer.findProjectsWithRequesterPrivileges(eq(SAMPLE_REQUESTING_USER)))
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
            1));

    var projects = catalog.listScopes(SAMPLE_REQUESTING_USER);
    assertIterableEquals(
        List.of( // Sorted
            new ProjectId("project-1"),
            new ProjectId("project-2"),
            new ProjectId("project-3")),
        projects);
  }

  // ---------------------------------------------------------------------------
  // listRequesterPrivileges.
  // ---------------------------------------------------------------------------

  @Test
  public void listRequesterPrivilegesReturnsAvailableAndActiveRequesterPrivileges() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    when(policyAnalyzer.findRequesterPrivileges(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        any(),
        any()))
        .thenReturn(RequesterPrivilegeSet.empty());

    var catalog = new MpaProjectRoleCatalog(
        policyAnalyzer,
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(
            null,
            Duration.ofMinutes(5),
            1,
            1));

    var privileges = catalog.listRequesterPrivileges(SAMPLE_REQUESTING_USER, SAMPLE_PROJECT);
    assertNotNull(privileges);

    verify(policyAnalyzer, times(1)).findRequesterPrivileges(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        argThat(types -> types.stream().map(type -> type.name()).collect(Collectors.toList())
            .containsAll(List.of("SELF_APPROVAL", "PEER_APPROVAL()", "EXTERNAL_APPROVAL()"))),
        eq(EnumSet.of(RequesterPrivilege.Status.INACTIVE, RequesterPrivilege.Status.ACTIVE)));
  }

  // ---------------------------------------------------------------------------
  // listReviewers.
  // ---------------------------------------------------------------------------

  @Test
  public void whenUserNotAllowedToActivateRole_ThenListReviewersThrowsException() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var peerApproval = new PeerApproval("topic");

    when(policyAnalyzer.findRequesterPrivileges(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(Set.of(peerApproval)),
        any()))
        .thenReturn(RequesterPrivilegeSet.empty());

    var catalog = new MpaProjectRoleCatalog(
        policyAnalyzer,
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(
            null,
            Duration.ofMinutes(5),
            1,
            1));

    var roleBinding = new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE);
    assertThrows(
        AccessDeniedException.class,
        () -> catalog.listReviewers(SAMPLE_REQUESTING_USER, new RequesterPrivilege<ProjectRoleBinding>(
            new ProjectRoleBinding(roleBinding), roleBinding.role(), peerApproval,
            Status.INACTIVE)));
  }

  @Test
  public void whenUserAllowedToActivateRoleWithoutMpa_ThenListReviewersReturnsList() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
    var roleBinding = new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE);
    var peerApproval = new PeerApproval("topic");
    var privilege = new RequesterPrivilege<ProjectRoleBinding>(new ProjectRoleBinding(roleBinding),
        roleBinding.role(), peerApproval, Status.INACTIVE);
    when(policyAnalyzer.findRequesterPrivileges(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(Set.of(peerApproval)),
        any()))
        .thenReturn(new RequesterPrivilegeSet(
            new TreeSet<>(Set.of(new RequesterPrivilege<>(
                new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, "roles/different-role")),
                "-",
                peerApproval,
                RequesterPrivilege.Status.INACTIVE))),
            new TreeSet<>(),
            Set.of()));

    var catalog = new MpaProjectRoleCatalog(
        policyAnalyzer,
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(
            null,
            Duration.ofMinutes(5),
            1,
            1));

    assertThrows(
        AccessDeniedException.class,
        () -> catalog.listReviewers(SAMPLE_REQUESTING_USER, privilege));
  }

  @Test
  public void whenUserAllowedToActivatePeerApprovalPrivilege_ThenListReviewersExcludesUser() throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);
    var role = new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE));
    when(policyAnalyzer.findRequesterPrivileges(
        eq(SAMPLE_REQUESTING_USER),
        eq(SAMPLE_PROJECT),
        eq(Set.of(new PeerApproval("topic"))),
        any()))
        .thenReturn(new RequesterPrivilegeSet((new TreeSet<>(
            Set.of(
                new RequesterPrivilege<>(
                    role,
                    "-",
                    new PeerApproval("topic"),
                    RequesterPrivilege.Status.INACTIVE)))),
            new TreeSet<>(),
            Set.of()));
    when(policyAnalyzer
        .findReviewerPrivelegeHolders(
            eq(role),
            eq(new PeerApproval("topic"))))
        .thenReturn(Set.of(SAMPLE_APPROVING_USER, SAMPLE_REQUESTING_USER));

    var catalog = new MpaProjectRoleCatalog(
        policyAnalyzer,
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var peerApproval = new PeerApproval("topic");

    var peerApprovalPrivilege = new RequesterPrivilege<>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
        "-",
        peerApproval,
        RequesterPrivilege.Status.INACTIVE);

    when(policyAnalyzer
        .findRequesterPrivileges(
            eq(SAMPLE_REQUESTING_USER),
            eq(SAMPLE_PROJECT),
            eq(Set.of(peerApproval)),
            eq(EnumSet.of(RequesterPrivilege.Status.INACTIVE))))
        .thenReturn(new RequesterPrivilegeSet(
            new TreeSet<>(Set.of(peerApprovalPrivilege)),
            new TreeSet<>(),
            Set.of()));

    when(policyAnalyzer
        .findReviewerPrivelegeHolders(
            eq(peerApprovalPrivilege.id()),
            eq(peerApproval)))
        .thenReturn(Set.of(SAMPLE_REQUESTING_USER, SAMPLE_APPROVING_USER));

    var reviewers = catalog.listReviewers(SAMPLE_REQUESTING_USER, peerApprovalPrivilege);
    assertIterableEquals(Set.of(SAMPLE_APPROVING_USER), reviewers);
  }

  @Test
  public void whenUserAllowedToActivateExternalApprovalPrivilege_ThenListReviewersIncludesReviewers()
      throws Exception {
    var policyAnalyzer = Mockito.mock(PolicyAnalyzerRepository.class);

    var catalog = new MpaProjectRoleCatalog(
        policyAnalyzer,
        Mockito.mock(ResourceManagerClient.class),
        new MpaProjectRoleCatalog.Options(null, Duration.ofMinutes(30), 1, 2));

    var externalApproval = new ExternalApproval("topic");

    var externalApprovalPrivilege = new RequesterPrivilege<>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
        "-",
        externalApproval,
        RequesterPrivilege.Status.INACTIVE);

    var reviewerEntitlement = new ReviewerPrivilege<ProjectRoleBinding>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE)),
        "-",
        Set.of(externalApproval));

    when(policyAnalyzer
        .findRequesterPrivileges(
            eq(SAMPLE_REQUESTING_USER),
            eq(SAMPLE_PROJECT),
            eq(Set.of(externalApproval)),
            eq(EnumSet.of(RequesterPrivilege.Status.INACTIVE))))
        .thenReturn(new RequesterPrivilegeSet(
            new TreeSet<>(Set.of(externalApprovalPrivilege)),
            new TreeSet<>(),
            Set.of()));

    when(policyAnalyzer
        .findReviewerPrivelegeHolders(
            eq(reviewerEntitlement.id()),
            eq(externalApproval)))
        .thenReturn(Set.of(SAMPLE_REQUESTING_USER, SAMPLE_APPROVING_USER));

    var reviewers = catalog.listReviewers(SAMPLE_REQUESTING_USER, externalApprovalPrivilege);
    assertIterableEquals(Set.of(SAMPLE_APPROVING_USER), reviewers);
  }
}
