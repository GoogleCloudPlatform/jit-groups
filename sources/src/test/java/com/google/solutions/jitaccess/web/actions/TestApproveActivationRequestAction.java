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

import com.google.auth.oauth2.TokenVerifier;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRole;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRoleActivator;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.core.notifications.NotificationService;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.MockitoUtils;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import com.google.solutions.jitaccess.web.TokenObfuscator;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TestApproveActivationRequestAction {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");
  private static final UserId SAMPLE_USER_2 = new UserId("user-2@example.com");
  private static final String SAMPLE_TOKEN = "eySAMPLE";

  @Test
  public void whenTokenInvalid_ThenActionThrowsException() throws Exception {
    var tokenSigner = Mockito.mock(TokenSigner.class);
    when(tokenSigner
      .verify(
        any(),
        eq(SAMPLE_TOKEN)))
      .thenThrow(new TokenVerifier.VerificationException("mock"));

    var action = new ApproveActivationRequestAction(
      new LogAdapter(),
      Mockito.mock(RuntimeEnvironment.class),
      Mockito.mock(ProjectRoleActivator.class),
      Mockito.mock(Instance.class),
      Mocks.createMpaProjectRoleCatalogMock(),
      tokenSigner);

    assertThrows(
      AccessDeniedException.class,
      () -> action.execute(
        Mocks.createIapPrincipalMock(SAMPLE_USER),
        TokenObfuscator.encode(SAMPLE_TOKEN),
        Mockito.mock(UriInfo.class)));
  }

  @Test
  public void whenActivatorThrowsException_ThenActionThrowsException() throws Exception {
    var request = new ProjectRoleActivator(
      Mockito.mock(Catalog.class),
      Mockito.mock(ResourceManagerClient.class),
      Mockito.mock(JustificationPolicy.class),
      new ProjectRoleActivator.Options(1))
      .createMpaRequest(
        new MpaProjectRoleCatalog.UserContext(SAMPLE_USER),
        Set.of(new ProjectRole(new RoleBinding(new ProjectId("project-1"), "roles/mock"))),
        Set.of(SAMPLE_USER_2),
        "a justification",
        Instant.now(),
        Duration.ofSeconds(60));

    var tokenSigner = Mockito.mock(TokenSigner.class);
    when(tokenSigner
      .verify(
        any(),
        eq(SAMPLE_TOKEN)))
      .thenReturn(request);

    var activator = Mockito.mock(ProjectRoleActivator.class);
    when(activator
      .approve(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER)),
        eq(request)))
      .thenThrow(new AccessDeniedException("mock"));

    var action = new ApproveActivationRequestAction(
      new LogAdapter(),
      Mockito.mock(RuntimeEnvironment.class),
      activator,
      Mockito.mock(Instance.class),
      Mocks.createMpaProjectRoleCatalogMock(),
      tokenSigner);

    assertThrows(
      AccessDeniedException.class,
      () -> action.execute(
        Mocks.createIapPrincipalMock(SAMPLE_USER),
        TokenObfuscator.encode(SAMPLE_TOKEN),
        Mockito.mock(UriInfo.class)));
  }

  @Test
  public void whenTokenValid_ThenApproveActivationSendsNotification() throws Exception {
    var request = new ProjectRoleActivator(
      Mockito.mock(Catalog.class),
      Mockito.mock(ResourceManagerClient.class),
      Mockito.mock(JustificationPolicy.class),
      new ProjectRoleActivator.Options(1))
      .createMpaRequest(
        new MpaProjectRoleCatalog.UserContext(SAMPLE_USER),
        Set.of(new ProjectRole(new RoleBinding(new ProjectId("project-1"), "roles/mock"))),
        Set.of(SAMPLE_USER_2),
        "a justification",
        Instant.now(),
        Duration.ofSeconds(60));

    var tokenSigner = Mockito.mock(TokenSigner.class);
    when(tokenSigner
      .verify(
        any(),
        eq(SAMPLE_TOKEN)))
      .thenReturn(request);

    var activator = Mockito.mock(ProjectRoleActivator.class);
    when(activator
      .approve(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER)),
        eq(request)))
      .thenReturn(new Activation(request.startTime(), request.duration()));

    var notificationService = Mockito.mock(NotificationService.class);
    when(notificationService.canSendNotifications()).thenReturn(true);

    var action = new ApproveActivationRequestAction(
      new LogAdapter(),
      Mocks.createRuntimeEnvironmentMock(),
      activator,
      MockitoUtils.toCdiInstance(notificationService),
      Mocks.createMpaProjectRoleCatalogMock(),
      tokenSigner);

    var response = action.execute(
      Mocks.createIapPrincipalMock(SAMPLE_USER),
      TokenObfuscator.encode(SAMPLE_TOKEN),
      Mocks.createUriInfoMock());

    verify(notificationService, times(1))
      .sendNotification(argThat(n -> n instanceof ApproveActivationRequestAction.ActivationApprovedNotification));
  }

  @Test
  public void whenTokenValid_ThenActionSucceeds() throws Exception {
    var request = new ProjectRoleActivator(
      Mockito.mock(Catalog.class),
      Mockito.mock(ResourceManagerClient.class),
      Mockito.mock(JustificationPolicy.class),
      new ProjectRoleActivator.Options(1))
      .createMpaRequest(
        new MpaProjectRoleCatalog.UserContext(SAMPLE_USER),
        Set.of(new ProjectRole(new RoleBinding(new ProjectId("project-1"), "roles/mock"))),
        Set.of(SAMPLE_USER_2),
        "a justification",
        Instant.now(),
        Duration.ofSeconds(60));

    var tokenSigner = Mockito.mock(TokenSigner.class);
    when(tokenSigner
      .verify(
        any(),
        eq(SAMPLE_TOKEN)))
      .thenReturn(request);

    var activator = Mockito.mock(ProjectRoleActivator.class);
    when(activator
      .approve(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER_2)),
        eq(request)))
      .thenReturn(new Activation(request.startTime(), request.duration()));

    var action = new ApproveActivationRequestAction(
      new LogAdapter(),
      Mocks.createRuntimeEnvironmentMock(),
      activator,
      MockitoUtils.toCdiInstance(Mocks.createNotificationServiceMock(false)),
      Mocks.createMpaProjectRoleCatalogMock(),
      tokenSigner);

    var response = action.execute(
      Mocks.createIapPrincipalMock(SAMPLE_USER_2),
      TokenObfuscator.encode(SAMPLE_TOKEN),
      Mocks.createUriInfoMock());

    assertEquals(request.requestingUser().email, response.beneficiary.email);
    assertIterableEquals(Set.of(SAMPLE_USER_2), request.reviewers());
    assertFalse(response.isBeneficiary);
    assertTrue(response.isReviewer);
    assertEquals(request.justification(), response.justification);
    assertEquals(1, response.items.size());
    assertEquals(request.id().toString(), response.items.get(0).activationId);
    assertEquals("project-1", response.items.get(0).projectId);
    assertEquals("ACTIVE", response.items.get(0).status.name());
    assertEquals(request.startTime().getEpochSecond(), response.items.get(0).startTime);
    assertEquals(request.endTime().getEpochSecond(), response.items.get(0).endTime);
  }
}
