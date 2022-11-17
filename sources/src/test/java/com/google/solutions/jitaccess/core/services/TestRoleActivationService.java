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
import com.google.solutions.jitaccess.core.adapters.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class TestRoleActivationService {
  private static final UserId SAMPLE_USER = new UserId("user-1", "user-1@example.com");
  private static final UserId SAMPLE_USER_2 = new UserId("user-2", "user-2@example.com");
  private static final String SAMPLE_PROJECT_RESOURCE = "//cloudresourcemanager.googleapis.com/projects/project-1";
  private static final String SAMPLE_ROLE = "roles/resourcemanager.projectIamAdmin";
  private static final Pattern JUSTIFICATION_PATTERN = Pattern.compile(".*");

  // ---------------------------------------------------------------------
  // activateEligibleRoleBinding.
  // ---------------------------------------------------------------------

  @Test
  public void whenRoleIsNotEligible_ThenActivateEligibleRoleBindingAsyncThrowsException()
    throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleRoleBindings(eq(SAMPLE_USER)))
      .thenReturn(new EligibleRoleBindings(
        List.of(new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          "roles/compute.viewer", // Different role
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_JIT_APPROVAL)),
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
      () -> service.activateEligibleRoleBinding(
        SAMPLE_USER,
        SAMPLE_USER,
        new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_JIT_APPROVAL),
        "justification"));
  }

  @Test
  public void whenRoleIsMpaEligibleAndCallerIsSameAsBeneficiary_ThenActivateEligibleRoleBindingThrowsException() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleRoleBindings(eq(SAMPLE_USER)))
      .thenReturn(new EligibleRoleBindings(
        List.of(new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_MPA_APPROVAL)),
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
      () -> service.activateEligibleRoleBinding(
        SAMPLE_USER,
        SAMPLE_USER,
        new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_MPA_APPROVAL),
        "justification"));
    assertThrows(
      AccessDeniedException.class,
      () -> service.activateEligibleRoleBinding(
        SAMPLE_USER,
        SAMPLE_USER,
        new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_JIT_APPROVAL),
        "justification"));
  }

  @Test
  public void whenRoleIsMpaEligibleForCallerButNotForBeneficiary_ThenActivateEligibleRoleBindingThrowsException() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleRoleBindings(eq(SAMPLE_USER)))
      .thenReturn(new EligibleRoleBindings(
        List.of(new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_MPA_APPROVAL)),
        List.<String>of()));
    when(discoveryService.listEligibleRoleBindings(eq(SAMPLE_USER_2)))
      .thenReturn(new EligibleRoleBindings(
        List.<RoleBinding>of(),
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
      () -> service.activateEligibleRoleBinding(
        SAMPLE_USER,
        SAMPLE_USER_2,
        new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_MPA_APPROVAL),
        "justification"));
  }

  @Test
  public void whenRoleIsMpaEligibleForBeneficiaryButNotForCaller_ThenActivateEligibleRoleBindingThrowsException() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleRoleBindings(eq(SAMPLE_USER)))
      .thenReturn(new EligibleRoleBindings(
        List.<RoleBinding>of(),
        List.<String>of()));
    when(discoveryService.listEligibleRoleBindings(eq(SAMPLE_USER_2)))
      .thenReturn(new EligibleRoleBindings(
        List.of(new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_MPA_APPROVAL)),
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
      () -> service.activateEligibleRoleBinding(
        SAMPLE_USER,
        SAMPLE_USER_2,
        new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_MPA_APPROVAL),
        "justification"));
  }

  @Test
  public void whenRoleIsJitEligibleAndCallerIsDifferentFromBeneficiary_ThenActivateEligibleRoleBindingThrowsException() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleRoleBindings(eq(SAMPLE_USER)))
      .thenReturn(new EligibleRoleBindings(
        List.of(new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_JIT_APPROVAL)),
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
      () -> service.activateEligibleRoleBinding(
        SAMPLE_USER,
        SAMPLE_USER_2,
        new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_JIT_APPROVAL),
        "justification"));
  }

  @Test
  public void whenRoleIsJitEligible_ThenActivateEligibleRoleBindingAddsBinding() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleRoleBindings(eq(SAMPLE_USER)))
      .thenReturn(new EligibleRoleBindings(
        List.of(new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_JIT_APPROVAL)),
        List.<String>of()));

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(TokenService.class),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    var expiry =
      service.activateEligibleRoleBinding(
        SAMPLE_USER,
        SAMPLE_USER,
        new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_JIT_APPROVAL),
        "justification");

    assertTrue(expiry.isAfter(OffsetDateTime.now()));
    assertTrue(expiry.isBefore(OffsetDateTime.now().plusMinutes(2)));

    verify(resourceAdapter)
      .addIamBinding(
        eq("project-1"),
        argThat(
          b ->
            b.getRole().equals(SAMPLE_ROLE)
              && b.getCondition().getExpression().contains("request.time < timestamp")
              && b.getCondition().getDescription().contains("justification")),
        eq(
          EnumSet.of(
            ResourceManagerAdapter.IamBindingOptions
              .REPLACE_BINDINGS_FOR_SAME_PRINCIPAL_AND_ROLE)),
        eq("justification"));
  }

  @Test
  public void whenRoleIsJitEligibleButJustificationDoesNotMatch_ThenActivateEligibleRoleBindingThrowsException()
    throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    when(discoveryService.listEligibleRoleBindings(eq(SAMPLE_USER)))
      .thenReturn(new EligibleRoleBindings(
        List.of(new RoleBinding(
          "project-1",
          SAMPLE_PROJECT_RESOURCE,
          SAMPLE_ROLE,
          RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_JIT_APPROVAL)),
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
        service.activateEligibleRoleBinding(
          SAMPLE_USER,
          SAMPLE_USER,
          new RoleBinding(
            "project-1",
            SAMPLE_PROJECT_RESOURCE,
            SAMPLE_ROLE,
            RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_JIT_APPROVAL),
          "not-numeric"));
  }
}