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

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
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
  private static final Pattern JUSTIFICATION_PATTERN = Pattern.compile(".*");

  // ---------------------------------------------------------------------
  // activateProjectRoleForSelf.
  // ---------------------------------------------------------------------

  @Test
  public void whenResourceIsNotAProject_ThenActivateProjectRoleForSelfThrowsException() throws Exception {
    var resourceAdapter = Mockito.mock(ResourceManagerAdapter.class);
    var discoveryService = Mockito.mock(RoleDiscoveryService.class);

    var service = new RoleActivationService(
      discoveryService,
      Mockito.mock(ActivationTokenService.class),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

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
      Mockito.mock(ActivationTokenService.class),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(AccessDeniedException.class,
      () -> service.activateProjectRoleForSelf(
        caller,
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE),
        "justification"));
  }

  @Test
  public void whenJustificationDoesNotMatch_ThenActivateProjectRoleForSelfThrowsException() throws Exception {
    var service = new RoleActivationService(
      Mockito.mock(RoleDiscoveryService.class),
      Mockito.mock(ActivationTokenService.class),
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        Pattern.compile("^\\d+$"),
        Duration.ofMinutes(1)));

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
      Mockito.mock(ActivationTokenService.class),
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

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
    assertTrue(activation.expiry.isAfter(OffsetDateTime.now()));
    assertTrue(activation.expiry.isBefore(OffsetDateTime.now().plusMinutes(2)));

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
  public void whenTokenInvalid_ThenActivateProjectRoleForPeerThrowsException() throws Exception {
    var token = "invalid-token";

    var tokenService = Mockito.mock(ActivationTokenService.class);
    when (tokenService.verifyToken(eq(token)))
      .thenThrow(new TokenVerifier.VerificationException("mock"));

    var service = new RoleActivationService(
      Mockito.mock(RoleDiscoveryService.class),
      tokenService,
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(TokenVerifier.VerificationException.class,
      () -> service.activateProjectRoleForPeer(SAMPLE_USER, token));
  }

  @Test
  public void whenCallerSameAsBeneficiary_ThenActivateProjectRoleForPeerThrowsException() throws Exception {
    var token = "token";
    var caller = SAMPLE_USER;

    var tokenService = Mockito.mock(ActivationTokenService.class);
    when (tokenService.verifyToken(eq(token)))
      .thenReturn(new ActivationTokenService.Payload.Builder()
        .setId("mpa-1")
        .setBeneficiary(caller)
        .setReviewers(List.of(caller, SAMPLE_USER_2))
        .setRoleBinding(new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE))
        .build());

    var service = new RoleActivationService(
      Mockito.mock(RoleDiscoveryService.class),
      tokenService,
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(IllegalArgumentException.class,
      () -> service.activateProjectRoleForPeer(caller, token));
  }

  @Test
  public void whenCallerNotListedAsReviewer_ThenActivateProjectRoleForPeerThrowsException() throws Exception {
    var token = "token";
    var caller = SAMPLE_USER_3;

    var tokenService = Mockito.mock(ActivationTokenService.class);
    when (tokenService.verifyToken(eq(token)))
      .thenReturn(new ActivationTokenService.Payload.Builder()
        .setId("mpa-1")
        .setBeneficiary(SAMPLE_USER)
        .setReviewers(List.of(SAMPLE_USER_2))
        .setRoleBinding(new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE))
        .build());

    var service = new RoleActivationService(
      Mockito.mock(RoleDiscoveryService.class),
      tokenService,
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(AccessDeniedException.class,
      () -> service.activateProjectRoleForPeer(caller, token));
  }

  @Test
  public void whenJustificationDoesNotMatch_ThenActivateProjectRoleForPeerThrowsException() throws Exception {
    var token = "token";

    var tokenService = Mockito.mock(ActivationTokenService.class);
    when(tokenService.verifyToken(eq(token)))
      .thenReturn(new ActivationTokenService.Payload.Builder()
        .setId("mpa-1")
        .setBeneficiary(SAMPLE_USER)
        .setReviewers(List.of(SAMPLE_USER_2))
        .setRoleBinding(new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE))
        .setJustification("not-numeric")
        .build());

    var service = new RoleActivationService(
      Mockito.mock(RoleDiscoveryService.class),
      tokenService,
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        Pattern.compile("^\\d+$"),
        Duration.ofMinutes(1)));

    assertThrows(AccessDeniedException.class,
      () -> service.activateProjectRoleForPeer(SAMPLE_USER_2, token));
  }

  @Test
  public void whenRoleNotMpaEligibleForCaller_ThenActivateProjectRoleForPeerThrowsException() throws Exception {
    var token = "token";
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

    var tokenService = Mockito.mock(ActivationTokenService.class);
    when(tokenService.verifyToken(eq(token)))
      .thenReturn(new ActivationTokenService.Payload.Builder()
        .setId("mpa-1")
        .setBeneficiary(peer)
        .setReviewers(List.of(caller))
        .setRoleBinding(roleBinding)
        .setJustification("justification")
        .build());

    var service = new RoleActivationService(
      discoveryService,
      tokenService,
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(AccessDeniedException.class,
      () -> service.activateProjectRoleForPeer(caller, token));
  }

  @Test
  public void whenRoleIsJitEligibleForCaller_ThenActivateProjectRoleForPeerThrowsException() throws Exception {
    var token = "token";
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

    var tokenService = Mockito.mock(ActivationTokenService.class);
    when(tokenService.verifyToken(eq(token)))
      .thenReturn(new ActivationTokenService.Payload.Builder()
        .setId("mpa-1")
        .setBeneficiary(peer)
        .setReviewers(List.of(caller))
        .setRoleBinding(roleBinding)
        .setJustification("justification")
        .build());

    var service = new RoleActivationService(
      discoveryService,
      tokenService,
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(AccessDeniedException.class,
      () -> service.activateProjectRoleForPeer(caller, token));
  }

  @Test
  public void whenRoleNotEligibleForPeer_ThenActivateProjectRoleForPeerThrowsException() throws Exception {
    var token = "token";
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

    var tokenService = Mockito.mock(ActivationTokenService.class);
    when(tokenService.verifyToken(eq(token)))
      .thenReturn(new ActivationTokenService.Payload.Builder()
        .setId("mpa-1")
        .setBeneficiary(peer)
        .setReviewers(List.of(caller))
        .setRoleBinding(roleBinding)
        .setJustification("justification")
        .build());

    var service = new RoleActivationService(
      discoveryService,
      tokenService,
      Mockito.mock(ResourceManagerAdapter.class),
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    assertThrows(AccessDeniedException.class,
      () -> service.activateProjectRoleForPeer(caller, token));
  }

  @Test
  public void whenPeerAndCallerEligible_ThenActivateProjectRoleAddsBinding() throws Exception {
    var token = "token";
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

    var issuedAt = 1000L;
    var tokenService = Mockito.mock(ActivationTokenService.class);
    when(tokenService.verifyToken(eq(token)))
      .thenReturn(new ActivationTokenService.Payload(new JsonWebToken.Payload()
        .setIssuer("issuer")
        .setAudience("audience")
        .setJwtId("mpa-1")
        .setIssuedAtTimeSeconds(issuedAt)
        .setExpirationTimeSeconds(2000L)
        .set("beneficiary", peer.email)
        .set("reviewers", List.of(caller.email))
        .set("role", roleBinding.role)
        .set("resource", roleBinding.fullResourceName)
        .set("justification", "justification")));

    var service = new RoleActivationService(
      discoveryService,
      tokenService,
      resourceAdapter,
      new RoleActivationService.Options(
        "hint",
        JUSTIFICATION_PATTERN,
        Duration.ofMinutes(1)));

    var activation = service.activateProjectRoleForPeer(caller, token);

    assertNotNull(activation);
    assertEquals("mpa-1", activation.id.toString());
    assertEquals(ProjectRole.Status.ACTIVATED, activation.projectRole.status);
    assertEquals(roleBinding, activation.projectRole.roleBinding);
    assertEquals(Instant.ofEpochSecond(issuedAt).plusSeconds(60).atOffset(ZoneOffset.UTC), activation.expiry);

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