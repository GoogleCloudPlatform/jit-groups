//
// Copyright 2022 Google LLC
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

package com.google.solutions.jitaccess.core.services;

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.adapters.ResourceManagerAdapter;
import com.google.solutions.jitaccess.core.data.ProjectId;
import com.google.solutions.jitaccess.core.data.ProjectRole;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestRoleActivationService {
  private static final UserId SAMPLE_USER = new UserId("user-1", "user-1@example.com");
  private static final UserId SAMPLE_USER_2 = new UserId("user-2", "user-2@example.com");
  private static final UserId SAMPLE_USER_3 = new UserId("user-2", "user-3@example.com");
  private static final ProjectId SAMPLE_PROJECT_ID = new ProjectId("project-1");
  private static final String SAMPLE_PROJECT_RESOURCE_1 = "//cloudresourcemanager.googleapis.com/projects/project-1";
  private static final String SAMPLE_ROLE = "roles/resourcemanager.projectIamAdmin";
  private static final Pattern DEFAULT_JUSTIFICATION_PATTERN = Pattern.compile(".*");
  private static final int DEFAULT_MAX_NUMBER_OF_REVIEWERS = 10;

  // ---------------------------------------------------------------------
  // activateProjectRoleForSelf.
  // ---------------------------------------------------------------------

  @Test
  public void whenResourceIsNotAProject_ThenActivateProjectRoleForSelfThrowsException() {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    var service = new RoleActivationService(
      discoveryService,
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    assertThrows(IllegalArgumentException.class,
      () -> service.activateProjectRoleForSelf(
        SAMPLE_USER,
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1 + "/foo/bar",
          SAMPLE_ROLE),
        "justification"));
  }

  @Test
  public void whenCallerLacksRoleBinding_ThenActivateProjectRoleForSelfThrowsException() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    var caller = SAMPLE_USER;

    when(discoveryService.listEligibleProjectRoles(eq(caller), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          new RoleBinding(
            SAMPLE_PROJECT_RESOURCE_1,
            "roles/compute.viewer"), // Different role
          ProjectRole.Status.ELIGIBLE_FOR_JIT)),
        List.of()));

    var service = new RoleActivationService(
      discoveryService,
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    assertThrows(AccessDeniedException.class,
      () -> service.activateProjectRoleForSelf(
        caller,
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE),
        "justification"));
  }

  @Test
  public void whenJustificationDoesNotMatch_ThenActivateProjectRoleForSelfThrowsException() {
    var service = new RoleActivationService(
      Mockito.mock(RoleDiscoveryService.class),
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        Pattern.compile("^\\d+$"),
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    assertThrows(AccessDeniedException.class,
      () -> service.activateProjectRoleForSelf(
        SAMPLE_USER,
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE),
        "not-numeric"));
  }

  @Test
  public void whenCallerIsJitEligible_ThenActivateProjectRoleForSelfAddsBinding() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    var caller = SAMPLE_USER;

    when(discoveryService.listEligibleProjectRoles(eq(caller), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          new RoleBinding(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE),
          ProjectRole.Status.ELIGIBLE_FOR_JIT)),
        List.of()));

    var service = new RoleActivationService(
      discoveryService,
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var roleBinding = new RoleBinding(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE);
    var activation = service.activateProjectRoleForSelf(
      caller,
      roleBinding,
      "justification");

    assertTrue(activation.id.toString().startsWith("jit-"));
    assertEquals(activation.projectRole.roleBinding, roleBinding);
    assertEquals(ProjectRole.Status.ACTIVATED, activation.projectRole.status);
    assertTrue(activation.endTime.isAfter(activation.startTime));
    assertTrue(activation.endTime.isAfter(Instant.now().minusSeconds(60)));
    assertTrue(activation.endTime.isBefore(Instant.now().plusSeconds(120)));

    verify(resourceAdapter)
      .addProjectIamBinding(
        eq(SAMPLE_PROJECT_ID),
        argThat(b -> b.getRole().equals(SAMPLE_ROLE)
          && b.getCondition().getExpression().contains("request.time < timestamp")
          && b.getCondition().getDescription().contains("justification")),
        eq(EnumSet.of(ResourceManagerAdapter.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS)),
        eq("justification"));
  }

  // ---------------------------------------------------------------------
  // activateProjectRoleForPeer.
  // ---------------------------------------------------------------------

  @Test
  public void whenCallerSameAsBeneficiary_ThenActivateProjectRoleForPeerThrowsException() {
    var service = new RoleActivationService(
      Mockito.mock(RoleDiscoveryService.class),
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      SAMPLE_USER,
      Set.of(SAMPLE_USER),
      new RoleBinding(
        SAMPLE_PROJECT_RESOURCE_1,
        SAMPLE_ROLE),
      "justification",
      Instant.now(),
      Instant.now().plusSeconds(60));

    assertThrows(IllegalArgumentException.class,
      () -> service.activateProjectRoleForPeer(request.beneficiary, request));
  }

  @Test
  public void whenCallerNotListedAsReviewer_ThenActivateProjectRoleForPeerThrowsException() {
    var service = new RoleActivationService(
      Mockito.mock(RoleDiscoveryService.class),
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      SAMPLE_USER,
      Set.of(SAMPLE_USER_2),
      new RoleBinding(
        SAMPLE_PROJECT_RESOURCE_1,
        SAMPLE_ROLE),
      "justification",
      Instant.now(),
      Instant.now().plusSeconds(60));

    assertThrows(AccessDeniedException.class,
      () -> service.activateProjectRoleForPeer(SAMPLE_USER_3, request));
  }

  @Test
  public void whenRoleNotMpaEligibleForCaller_ThenActivateProjectRoleForPeerThrowsException() throws Exception {
    var caller = SAMPLE_USER;
    var peer = SAMPLE_USER_2;
    var roleBinding = new RoleBinding(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE);

    var discoveryService = Mockito.mock(RoleDiscoveryService.class);
    when(discoveryService.listEligibleProjectRoles(eq(caller), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          roleBinding,
          ProjectRole.Status.ACTIVATED)),
        List.of()));

    var request = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      peer,
      Set.of(caller),
      new RoleBinding(
        SAMPLE_PROJECT_RESOURCE_1,
        SAMPLE_ROLE),
      "justification",
      Instant.now(),
      Instant.now().plusSeconds(60));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    assertThrows(AccessDeniedException.class,
      () -> service.activateProjectRoleForPeer(caller, request));
  }

  @Test
  public void whenRoleIsJitEligibleForCaller_ThenActivateProjectRoleForPeerThrowsException() throws Exception {
    var caller = SAMPLE_USER;
    var peer = SAMPLE_USER_2;
    var roleBinding = new RoleBinding(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE);

    var discoveryService = Mockito.mock(RoleDiscoveryService.class);
    when(discoveryService.listEligibleProjectRoles(eq(caller), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          roleBinding,
          ProjectRole.Status.ELIGIBLE_FOR_JIT)),
        List.of()));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      peer,
      Set.of(caller),
      new RoleBinding(
        SAMPLE_PROJECT_RESOURCE_1,
        SAMPLE_ROLE),
      "justification",
      Instant.now(),
      Instant.now().plusSeconds(60));

    assertThrows(AccessDeniedException.class,
      () -> service.activateProjectRoleForPeer(caller, request));
  }

  @Test
  public void whenRoleNotEligibleForPeer_ThenActivateProjectRoleForPeerThrowsException() throws Exception {
    var caller = SAMPLE_USER;
    var peer = SAMPLE_USER_2;
    var roleBinding = new RoleBinding(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE);

    var discoveryService = Mockito.mock(RoleDiscoveryService.class);
    when(discoveryService.listEligibleProjectRoles(eq(caller), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          roleBinding,
          ProjectRole.Status.ELIGIBLE_FOR_MPA)),
        List.of()));
    when(discoveryService.listEligibleProjectRoles(eq(peer), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(),
        List.of()));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      peer,
      Set.of(caller),
      new RoleBinding(
        SAMPLE_PROJECT_RESOURCE_1,
        SAMPLE_ROLE),
      "justification",
      Instant.now(),
      Instant.now().plusSeconds(60));

    assertThrows(AccessDeniedException.class,
      () -> service.activateProjectRoleForPeer(caller, request));
  }

  @Test
  public void whenPeerAndCallerEligible_ThenActivateProjectRoleAddsBinding() throws Exception {
    var caller = SAMPLE_USER;
    var peer = SAMPLE_USER_2;
    var roleBinding = new RoleBinding(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE);

    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);
    when(discoveryService.listEligibleProjectRoles(eq(caller), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          roleBinding,
          ProjectRole.Status.ELIGIBLE_FOR_MPA)),
        List.of()));
    when(discoveryService.listEligibleProjectRoles(eq(peer), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          roleBinding,
          ProjectRole.Status.ELIGIBLE_FOR_MPA)),
        List.of()));

    var service = new RoleActivationService(
      discoveryService,
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var issuedAt = 1000L;
    var request = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      peer,
      Set.of(caller),
      new RoleBinding(
        SAMPLE_PROJECT_RESOURCE_1,
        SAMPLE_ROLE),
      "justification",
      Instant.ofEpochSecond(issuedAt),
      Instant.ofEpochSecond(issuedAt).plusSeconds(60));

    var activation = service.activateProjectRoleForPeer(caller, request);

    assertNotNull(activation);
    assertEquals(request.id, activation.id);
    assertEquals(ProjectRole.Status.ACTIVATED, activation.projectRole.status);
    assertEquals(roleBinding, activation.projectRole.roleBinding);
    assertEquals(request.startTime, activation.startTime);
    assertEquals(request.endTime, activation.endTime);

    verify(resourceAdapter)
      .addProjectIamBinding(
        eq(SAMPLE_PROJECT_ID),
        argThat(b -> b.getRole().equals(SAMPLE_ROLE)
          && b.getCondition().getExpression().contains("request.time < timestamp")
          && b.getCondition().getDescription().contains("justification")),
        eq(EnumSet.of(
          ResourceManagerAdapter.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS,
          ResourceManagerAdapter.IamBindingOptions.FAIL_IF_BINDING_EXISTS)),
        eq("justification"));
  }

  @Test
  public void whenRoleAlreadyActivated_ThenActivateProjectRoleAddsBinding() throws Exception {
    var caller = SAMPLE_USER;
    var peer = SAMPLE_USER_2;
    var roleBinding = new RoleBinding(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE);

    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);
    when(discoveryService.listEligibleProjectRoles(eq(caller), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          roleBinding,
          ProjectRole.Status.ELIGIBLE_FOR_MPA)),
        List.of()));
    when(discoveryService.listEligibleProjectRoles(eq(peer), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          roleBinding,
          ProjectRole.Status.ACTIVATED)), // Pretend someone else approved already
        List.of()));

    var service = new RoleActivationService(
      discoveryService,
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var issuedAt = 1000L;
    var request = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      peer,
      Set.of(caller),
      new RoleBinding(
        SAMPLE_PROJECT_RESOURCE_1,
        SAMPLE_ROLE),
      "justification",
      Instant.ofEpochSecond(issuedAt),
      Instant.ofEpochSecond(issuedAt).plusSeconds(60));

    assertThrows(AccessDeniedException.class,
      () -> service.activateProjectRoleForPeer(caller, request));

    verify(resourceAdapter, times(0))
      .addProjectIamBinding(
        eq(SAMPLE_PROJECT_ID),
        any(),
        any(),
        any());
  }

  // ---------------------------------------------------------------------
  // createActivationRequestForPeer.
  // ---------------------------------------------------------------------

  @Test
  public void whenNumberOfReviewersExceedLimit_ThenCreateActivationRequestForPeerThrowsException() {
    var service = new RoleActivationService(
      Mockito.mock(RoleDiscoveryService.class),
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        2));

    assertThrows(IllegalArgumentException.class,
      () -> service.createActivationRequestForPeer(
        SAMPLE_USER,
        Set.of(SAMPLE_USER, SAMPLE_USER_2, SAMPLE_USER_3),
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE),
        "justification"));
  }

  @Test
  public void whenReviewerIncludesBeneficiary_ThenCreateActivationRequestForPeerThrowsException() {
    var service = new RoleActivationService(
      Mockito.mock(RoleDiscoveryService.class),
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    assertThrows(IllegalArgumentException.class,
      () -> service.createActivationRequestForPeer(
        SAMPLE_USER,
        Set.of(SAMPLE_USER),
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE),
        "justification"));
  }

  @Test
  public void whenJustificationDoesNotMatch_ThenCreateActivationRequestForPeerThrowsException() {
    var service = new RoleActivationService(
      Mockito.mock(RoleDiscoveryService.class),
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        Pattern.compile("^\\d+$"),
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    assertThrows(AccessDeniedException.class,
      () -> service.createActivationRequestForPeer(
        SAMPLE_USER,
        Set.of(SAMPLE_USER_2),
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE),
        "non-numeric justification"));
  }

  @Test
  public void whenRoleNotMpaEligibleForCaller_ThenCreateActivationRequestForPeerThrowsException() throws Exception {
    var caller = SAMPLE_USER;
    var peer = SAMPLE_USER_2;
    var roleBinding = new RoleBinding(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE);

    var discoveryService = Mockito.mock(RoleDiscoveryService.class);
    when(discoveryService.listEligibleProjectRoles(eq(caller), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          roleBinding,
          ProjectRole.Status.ACTIVATED)),
        List.of()));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    assertThrows(AccessDeniedException.class,
      () -> service.createActivationRequestForPeer(
        caller,
        Set.of(peer),
        roleBinding,
        "justification"));
  }

  @Test
  public void whenRoleIsJitEligibleForCaller_ThenCreateActivationRequestForPeerThrowsException() throws Exception {
    var caller = SAMPLE_USER;
    var peer = SAMPLE_USER_2;
    var roleBinding = new RoleBinding(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE);

    var discoveryService = Mockito.mock(RoleDiscoveryService.class);
    when(discoveryService.listEligibleProjectRoles(eq(caller), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          roleBinding,
          ProjectRole.Status.ELIGIBLE_FOR_JIT)),
        List.of()));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    assertThrows(AccessDeniedException.class,
      () -> service.createActivationRequestForPeer(
        caller,
        Set.of(peer),
        roleBinding,
        "justification"));
  }

  @Test
  public void whenCallerEligible_ThenCreateActivationRequestForPeerSucceeds() throws Exception {
    var caller = SAMPLE_USER;
    var peer = SAMPLE_USER_2;
    var roleBinding = new RoleBinding(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE);

    var discoveryService = Mockito.mock(RoleDiscoveryService.class);
    when(discoveryService.listEligibleProjectRoles(eq(caller), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          roleBinding,
          ProjectRole.Status.ELIGIBLE_FOR_MPA)),
        List.of()));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        DEFAULT_JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1),
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = service.createActivationRequestForPeer(
        caller,
        Set.of(peer),
        roleBinding,
        "justification");

    assertTrue(request.id.toString().startsWith("mpa-"));
    assertEquals("justification", request.justification);
    assertEquals(Set.of(peer), request.reviewers);
    assertEquals(caller, request.beneficiary);
    assertEquals(roleBinding, request.roleBinding);
  }

  // ---------------------------------------------------------------------
  // ActivationId.
  // ---------------------------------------------------------------------

  @Test
  public void whenTypeIsMpa_ThenNewActivationIdUsesPrefix() {
    var id = RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA);
    assertTrue(id.toString().startsWith("mpa-"));
  }

  @Test
  public void whenTypeIsJit_ThenNewActivationIdUsesPrefix() {
    var id = RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.JIT);
    assertTrue(id.toString().startsWith("jit-"));
  }
}