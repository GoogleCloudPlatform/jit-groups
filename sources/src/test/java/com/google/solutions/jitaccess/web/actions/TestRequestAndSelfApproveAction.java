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

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.Activation;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRole;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRoleActivator;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.MockitoUtils;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

public class TestRequestAndSelfApproveAction {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");

  @Test
  public void whenProjectIsNull_ThenActionThrowsException() throws Exception {
    var action = new RequestAndSelfApproveAction(
      new LogAdapter(),
      Mockito.mock(RuntimeEnvironment.class),
      Mockito.mock(ProjectRoleActivator.class),
      Mockito.mock(Instance.class),
      Mocks.createMpaProjectRoleCatalogMock());

    var exception = assertThrows(
      IllegalArgumentException.class,
      () -> action.execute(
        Mocks.createIapPrincipalMock(SAMPLE_USER),
        " ",
        new RequestAndSelfApproveAction.RequestEntity()));
    assertTrue(exception.getMessage().contains("projectId"));
  }

  @Test
  public void whenRolesEmpty_ThenActionThrowsException() throws Exception {
    var action = new RequestAndSelfApproveAction(
      new LogAdapter(),
      Mockito.mock(RuntimeEnvironment.class),
      Mockito.mock(ProjectRoleActivator.class),
      Mockito.mock(Instance.class),
      Mocks.createMpaProjectRoleCatalogMock());

    var request = new RequestAndSelfApproveAction.RequestEntity();
    request.entitlementIds = List.of();

    var exception = assertThrows(
      IllegalArgumentException.class,
      () -> action.execute(
        Mocks.createIapPrincipalMock(SAMPLE_USER),
        "project-1",
        request));
    assertTrue(exception.getMessage().contains("one or more roles"));
  }

  @Test
  public void whenJustificationMissing_ThenActionThrowsException() throws Exception {
    var action = new RequestAndSelfApproveAction(
      new LogAdapter(),
      Mockito.mock(RuntimeEnvironment.class),
      Mockito.mock(ProjectRoleActivator.class),
      Mockito.mock(Instance.class),
      Mocks.createMpaProjectRoleCatalogMock());

    var request = new RequestAndSelfApproveAction.RequestEntity();
    request.entitlementIds = List.of(new ProjectRole(SAMPLE_PROJECT, "roles/browser").toString());
    request.justification = "";

    var exception = assertThrows(
      IllegalArgumentException.class,
      () -> action.execute(
        Mocks.createIapPrincipalMock(SAMPLE_USER),
        "project-1",
        request));
    assertTrue(exception.getMessage().contains("justification"));
  }

  @Test
  public void whenActivatorThrowsException_ThenActionThrowsException() throws Exception {
    var activator = Mockito.mock(ProjectRoleActivator.class);
    when(activator.maximumNumberOfEntitlementsPerJitRequest()).thenReturn(1);
    when(activator
      .createJitRequest(any(), any(), any(), any(), any()))
      .thenCallRealMethod();
    when(activator
      .activate(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER)),
        any()))
      .thenThrow(new AccessDeniedException("mock"));

    var action = new RequestAndSelfApproveAction(
      new LogAdapter(),
      Mockito.mock(RuntimeEnvironment.class),
      activator,
      Mockito.mock(Instance.class),
      Mocks.createMpaProjectRoleCatalogMock());

    var request = new RequestAndSelfApproveAction.RequestEntity();
    request.entitlementIds = List.of(
      new ProjectRole(SAMPLE_PROJECT, "roles/browser").id(),
      new ProjectRole(SAMPLE_PROJECT, "roles/browser").id());
    request.justification = "justification";
    request.activationTimeout = 5;

    assertThrows(
      AccessDeniedException.class,
      () -> action.execute(
        Mocks.createIapPrincipalMock(SAMPLE_USER),
        "project-1",
        request));
  }

  @Test
  public void whenRolesContainDuplicates_ThenActionSucceedsAndIgnoresDuplicates() throws Exception {
    var roleBinding = new ProjectRole(new ProjectId("project-1"), "roles/browser");

    var activator = Mockito.mock(ProjectRoleActivator.class);
    when(activator.maximumNumberOfEntitlementsPerJitRequest()).thenReturn(1);
    when(activator
      .createJitRequest(any(), any(), any(), any(), any()))
      .thenCallRealMethod();
    when(activator
      .activate(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER)),
        argThat(r -> r.entitlements().size() == 1)))
      .then(r -> new Activation(Instant.MIN, Duration.ZERO));

    var action = new RequestAndSelfApproveAction(
      new LogAdapter(),
      Mockito.mock(RuntimeEnvironment.class),
      activator,
      MockitoUtils.toCdiInstance(Mocks.createNotificationServiceMock(false)),
      Mocks.createMpaProjectRoleCatalogMock());

    var request = new RequestAndSelfApproveAction.RequestEntity();
    request.entitlementIds = List.of(
      new ProjectRole(SAMPLE_PROJECT, "roles/browser").id(),
      new ProjectRole(SAMPLE_PROJECT, "roles/browser").id());
    request.justification = "justification";
    request.activationTimeout = 5;

    var response = action.execute(
      Mocks.createIapPrincipalMock(SAMPLE_USER),
      "project-1",
      request);

    assertEquals(SAMPLE_USER.email, response.beneficiary.email);
    assertEquals(0, response.reviewers.size());
    assertTrue(response.isBeneficiary);
    assertFalse(response.isReviewer);
    assertEquals("justification", response.justification);
    assertNotNull(response.entitlements);
    assertEquals(1, response.entitlements.size());
    assertEquals("project-1", response.entitlements.get(0).projectId);
    assertEquals(roleBinding.role(), response.entitlements.get(0).name);
    assertEquals(ActivationStatus.ACTIVE, response.entitlements.get(0).status);
    assertNotNull(response.entitlements.get(0).activationId);
  }
}
