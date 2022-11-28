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
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestRoleActivationService {
  private static final UserId SAMPLE_USER = new UserId("user-1", "user-1@example.com");
  private static final UserId SAMPLE_USER_2 = new UserId("user-2", "user-2@example.com");
  private static final ProjectId SAMPLE_PROJECT_ID = new ProjectId("project-1");
  private static final String SAMPLE_PROJECT_RESOURCE_1 = "//cloudresourcemanager.googleapis.com/projects/project-1";
  private static final String SAMPLE_ROLE = "roles/resourcemanager.projectIamAdmin";
  private static final Pattern JUSTIFICATION_PATTERN = Pattern.compile(".*");

  // ---------------------------------------------------------------------
  // activateProjectRole.
  // ---------------------------------------------------------------------

  @Test
  public void whenResourceIsNotAProject_ThenActivateProjectRoleThrowsException() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(TokenService.class),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(
      IllegalArgumentException.class,
      () -> service.activateProjectRole(
        SAMPLE_USER,
        SAMPLE_USER,
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1 + "/foo/bar",
          SAMPLE_ROLE),
        RoleActivationService.ActivationType.JIT,
        "justification"));
  }

  @Test
  public void whenUserLacksRoleBinding_ThenActivateProjectRoleThrowsException() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleProjectRoles(eq(SAMPLE_USER), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          new RoleBinding(
            SAMPLE_PROJECT_RESOURCE_1,
            "roles/compute.viewer"), // Different role
          ProjectRole.Status.ELIGIBLE_FOR_JIT)),
        List.<String>of()));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(TokenService.class),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(
      AccessDeniedException.class,
      () -> service.activateProjectRole(
        SAMPLE_USER,
        SAMPLE_USER,
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE),
        RoleActivationService.ActivationType.JIT,
        "justification"));
  }

  @Test
  public void whenRoleIsMpaEligibleButCallerIsSameAsBeneficiary_ThenActivateProjectRoleThrowsException() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleProjectRoles(eq(SAMPLE_USER), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          new RoleBinding(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE),
          ProjectRole.Status.ELIGIBLE_FOR_MPA)),
        List.<String>of()));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(TokenService.class),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(
      IllegalArgumentException.class,
      () -> service.activateProjectRole(
        SAMPLE_USER,
        SAMPLE_USER,
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE),
        RoleActivationService.ActivationType.MPA,
        "justification"));
    assertThrows(
      AccessDeniedException.class,
      () -> service.activateProjectRole(
        SAMPLE_USER,
        SAMPLE_USER,
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE),
        RoleActivationService.ActivationType.JIT,
        "justification"));
  }

  @Test
  public void whenRoleIsMpaEligibleForCallerButNotForBeneficiary_ThenActivateProjectRoleThrowsException() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleProjectRoles(eq(SAMPLE_USER), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          new RoleBinding(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE),
          ProjectRole.Status.ELIGIBLE_FOR_MPA)),
        List.<String>of()));
    when(discoveryService.listEligibleProjectRoles(eq(SAMPLE_USER_2), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.<ProjectRole>of(),
        List.<String>of()));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(TokenService.class),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(
      AccessDeniedException.class,
      () -> service.activateProjectRole(
        SAMPLE_USER,
        SAMPLE_USER_2,
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE),
        RoleActivationService.ActivationType.MPA,
        "justification"));
  }

  @Test
  public void whenRoleIsMpaEligibleForBeneficiaryButNotForCaller_ThenActivateProjectRoleThrowsException() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleProjectRoles(eq(SAMPLE_USER), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.<ProjectRole>of(),
        List.<String>of()));
    when(discoveryService.listEligibleProjectRoles(eq(SAMPLE_USER_2), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          new RoleBinding(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE),
          ProjectRole.Status.ELIGIBLE_FOR_MPA)),
        List.<String>of()));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(TokenService.class),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(
      AccessDeniedException.class,
      () -> service.activateProjectRole(
        SAMPLE_USER,
        SAMPLE_USER_2,
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE),
        RoleActivationService.ActivationType.MPA,
        "justification"));
  }

  @Test
  public void whenRoleIsJitEligibleAndCallerIsDifferentFromBeneficiary_ThenActivateProjectRoleThrowsException() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleProjectRoles(eq(SAMPLE_USER), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          new RoleBinding(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE),
          ProjectRole.Status.ELIGIBLE_FOR_JIT)),
        List.<String>of()));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(TokenService.class),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(
      IllegalArgumentException.class,
      () -> service.activateProjectRole(
        SAMPLE_USER,
        SAMPLE_USER_2,
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE),
        RoleActivationService.ActivationType.JIT,
        "justification"));
  }

  @Test
  public void whenRoleIsJitEligible_ThenActivateProjectRoleAddsBinding() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleProjectRoles(eq(SAMPLE_USER), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          new RoleBinding(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE),
          ProjectRole.Status.ELIGIBLE_FOR_JIT)),
        List.<String>of()));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(TokenService.class),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    var roleBinding = new RoleBinding(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE);
    var activation = service.activateProjectRole(
        SAMPLE_USER,
        SAMPLE_USER,
        roleBinding,
        RoleActivationService.ActivationType.JIT,
        "justification");

    assertEquals(activation.projectRole.roleBinding, roleBinding);
    assertEquals(ProjectRole.Status.ACTIVATED, activation.projectRole.status);
    assertTrue(activation.expiry.isAfter(OffsetDateTime.now()));
    assertTrue(activation.expiry.isBefore(OffsetDateTime.now().plusMinutes(2)));

    verify(resourceAdapter)
      .addProjectIamBinding(
        eq(SAMPLE_PROJECT_ID),
        argThat(b -> b.getRole().equals(SAMPLE_ROLE)
          && b.getCondition().getExpression().contains("request.time < timestamp")
          && b.getCondition().getDescription().contains("justification")),
        eq(
          EnumSet.of(
            ResourceManagerAdapter.IamBindingOptions
              .REPLACE_BINDINGS_FOR_SAME_PRINCIPAL_AND_ROLE)),
        eq("justification"));
  }

  @Test
  public void whenRoleIsJitEligibleButJustificationDoesNotMatch_ThenActivateProjectRoleThrowsException()
    throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleProjectRoles(eq(SAMPLE_USER), eq(SAMPLE_PROJECT_ID)))
      .thenReturn(new Result<ProjectRole>(
        List.of(new ProjectRole(
          new RoleBinding(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE),
          ProjectRole.Status.ELIGIBLE_FOR_JIT)),
        List.<String>of()));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(TokenService.class),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
          Pattern.compile("^\\d+$"),
          Duration.ofMinutes(1)));

    assertThrows(
      AccessDeniedException.class,
      () ->
        service.activateProjectRole(
          SAMPLE_USER,
          SAMPLE_USER,
          new RoleBinding(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE),
          RoleActivationService.ActivationType.JIT,
          "not-numeric"));
  }
}