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

package com.google.solutions.jitaccess.web;

import com.google.auth.oauth2.TokenVerifier;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.adapters.LogAdapter;
import com.google.solutions.jitaccess.core.data.ProjectId;
import com.google.solutions.jitaccess.core.data.ProjectRole;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserId;
import com.google.solutions.jitaccess.core.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TestApiResource {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");
  private static final UserId SAMPLE_USER_2 = new UserId("user-2@example.com");

  private static final String SAMPLE_TOKEN = "eySAMPLE";
  private static final Pattern DEFAULT_JUSTIFICATION_PATTERN = Pattern.compile("pattern");
  private static final int DEFAULT_MIN_NUMBER_OF_REVIEWERS = 1;
  private static final int DEFAULT_MAX_NUMBER_OF_REVIEWERS = 10;
  private static final String DEFAULT_HINT = "hint";
  private static final Duration DEFAULT_ACTIVATION_DURATION = Duration.ofMinutes(5);
  private static final ActivationTokenService.TokenWithExpiry SAMPLE_TOKEN_WITH_EXPIRY =
    new ActivationTokenService.TokenWithExpiry(SAMPLE_TOKEN, Instant.now().plusSeconds(10));

  private ApiResource resource;

  @BeforeEach
  public void before() {
    this.resource = new ApiResource();
    this.resource.logAdapter = new LogAdapter();
    this.resource.runtimeEnvironment = Mockito.mock(RuntimeEnvironment.class);
    this.resource.roleDiscoveryService = Mockito.mock(RoleDiscoveryService.class);
    this.resource.roleActivationService = Mockito.mock(RoleActivationService.class);
    this.resource.activationTokenService = Mockito.mock(ActivationTokenService.class);
    this.resource.notificationService = Mockito.mock(NotificationService.class);

    when(this.resource.notificationService.canSendNotifications()).thenReturn(true);
    when(this.resource.runtimeEnvironment.createAbsoluteUriBuilder(any(UriInfo.class)))
      .thenReturn(UriBuilder.fromUri("https://localhost/"));
  }

  // -------------------------------------------------------------------------
  // getPolicy.
  // -------------------------------------------------------------------------

  @Test
  public void whenPathNotMapped_ThenGetReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/unknown", ExceptionMappers.ErrorEntity.class);

    assertEquals(404, response.getStatus());
  }

  // -------------------------------------------------------------------------
  // getPolicy.
  // -------------------------------------------------------------------------

  @Test
  public void getPolicyReturnsJustificationHint() throws Exception {
    when(this.resource.roleActivationService.getOptions())
      .thenReturn(new RoleActivationService.Options(
        DEFAULT_HINT,
        DEFAULT_JUSTIFICATION_PATTERN,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var response = new RestDispatcher<>(resource, SAMPLE_USER)
      .get("/api/policy", ApiResource.PolicyResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body);
    assertEquals(DEFAULT_HINT, body.justificationHint);
  }

  @Test
  public void getPolicyReturnsSignedInUser() throws Exception {
    when(this.resource.roleActivationService.getOptions())
      .thenReturn(new RoleActivationService.Options(
        DEFAULT_HINT,
        DEFAULT_JUSTIFICATION_PATTERN,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var response = new RestDispatcher<>(resource, SAMPLE_USER)
      .get("/api/policy", ApiResource.PolicyResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body);
    assertEquals(SAMPLE_USER, body.signedInUser);
  }

  // -------------------------------------------------------------------------
  // listProjects.
  // -------------------------------------------------------------------------

  @Test
  public void postProjectsReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post("/api/projects", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void whenProjectDiscoveryThrowsAccessDeniedException_ThenListProjectsReturnsError() throws Exception {
    when(this.resource.roleDiscoveryService.listAvailableProjects(eq(SAMPLE_USER)))
      .thenThrow(new AccessDeniedException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenProjectDiscoveryThrowsIOException_ThenListProjectsReturnsError() throws Exception {
    when(this.resource.roleDiscoveryService.listAvailableProjects(eq(SAMPLE_USER)))
      .thenThrow(new IOException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenProjectDiscoveryReturnsNoProjects_ThenListProjectsReturnsEmptyList() throws Exception {
    when(this.resource.roleDiscoveryService.listAvailableProjects(eq(SAMPLE_USER)))
      .thenReturn(Set.of());

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects", ApiResource.ProjectsResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.projects);
    assertEquals(0, body.projects.size());
  }

  @Test
  public void whenProjectDiscoveryReturnsProjects_ThenListProjectsReturnsList() throws Exception {
    when(this.resource.roleDiscoveryService.listAvailableProjects(eq(SAMPLE_USER)))
      .thenReturn(Set.of(new ProjectId("project-1"), new ProjectId("project-2")));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects", ApiResource.ProjectsResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.projects);
    assertEquals(2, body.projects.size());
  }

  // -------------------------------------------------------------------------
  // listPeers.
  // -------------------------------------------------------------------------

  @Test
  public void postPeersReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post("/api/projects/project-1/peers", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void getPeersWithoutRoleReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/peers", ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());
  }

  @Test
  public void whenPeerDiscoveryThrowsAccessDeniedException_ThenListPeersReturnsError() throws Exception {
    when(this.resource.roleDiscoveryService.listEligibleUsersForProjectRole(eq(SAMPLE_USER), any(RoleBinding.class)))
      .thenThrow(new AccessDeniedException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/peers?role=roles/browser", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenPeerDiscoveryThrowsIOException_ThenListPeersReturnsError() throws Exception {
    when(this.resource.roleDiscoveryService.listEligibleUsersForProjectRole(eq(SAMPLE_USER), any(RoleBinding.class)))
      .thenThrow(new IOException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/peers?role=roles/browser", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenPeerDiscoveryReturnsNoPeers_ThenListPeersReturnsEmptyList() throws Exception {
    when(this.resource.roleDiscoveryService
      .listEligibleUsersForProjectRole(
        eq(SAMPLE_USER),
        argThat(r -> r.role.equals("roles/browser"))))
      .thenReturn(Set.of());

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/peers?role=roles/browser", ApiResource.ProjectRolePeersResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.peers);
    assertEquals(0, body.peers.size());
  }

  @Test
  public void whenPeerDiscoveryReturnsProjects_ThenListPeersReturnsList() throws Exception {
    when(this.resource.roleDiscoveryService
      .listEligibleUsersForProjectRole(
        eq(SAMPLE_USER),
        argThat(r -> r.role.equals("roles/browser"))))
      .thenReturn(Set.of(new UserId("peer-1@example.com"), new UserId("peer-2@example.com")));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/peers?role=roles/browser", ApiResource.ProjectRolePeersResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.peers);
    assertEquals(2, body.peers.size());
  }

  // -------------------------------------------------------------------------
  // listRoles.
  // -------------------------------------------------------------------------

  @Test
  public void postRolesReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post("/api/projects/project-1/roles", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void whenProjectIsEmpty_ThenListRolesReturnsError() throws Exception {
    when(this.resource.roleDiscoveryService.listAvailableProjects(eq(SAMPLE_USER)))
      .thenThrow(new AccessDeniedException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/%20/roles", ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("projectId"));
  }

  @Test
  public void whenRoleDiscoveryThrowsAccessDeniedException_ThenListRolesReturnsError() throws Exception {
    when(this.resource.roleDiscoveryService
      .listEligibleProjectRoles(
        eq(SAMPLE_USER),
        eq(new ProjectId("project-1"))))
      .thenThrow(new AccessDeniedException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenRoleDiscoveryThrowsIOException_ThenListRolesReturnsError() throws Exception {
    when(this.resource.roleDiscoveryService
      .listEligibleProjectRoles(
        eq(SAMPLE_USER),
        eq(new ProjectId("project-1"))))
      .thenThrow(new IOException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenRoleDiscoveryReturnsNoRoles_ThenListRolesReturnsEmptyList() throws Exception {
    when(this.resource.roleDiscoveryService
      .listEligibleProjectRoles(
        eq(SAMPLE_USER),
        eq(new ProjectId("project-1"))))
      .thenReturn(new Result<>(
        List.of(),
        List.of("warning")));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles", ApiResource.ProjectRolesResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.roles);
    assertEquals(0, body.roles.size());
    assertNotNull(body.warnings);
    assertEquals(1, body.warnings.size());
    assertEquals("warning", body.warnings.get(0));
  }

  @Test
  public void whenRoleDiscoveryReturnsRoles_ThenListRolesReturnsList() throws Exception {
    var role1 = new ProjectRole(
      new RoleBinding(new ProjectId("project-1").getFullResourceName(), "roles/browser"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);
    var role2 = new ProjectRole(
      new RoleBinding(new ProjectId("project-1").getFullResourceName(), "roles/janitor"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);

    when(this.resource.roleDiscoveryService
      .listEligibleProjectRoles(
        eq(SAMPLE_USER),
        eq(new ProjectId("project-1"))))
      .thenReturn(new Result<>(
        List.of(role1, role2),
        null));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles", ApiResource.ProjectRolesResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.roles);
    assertEquals(2, body.roles.size());
    assertEquals(role1, body.roles.get(0));
    assertEquals(role2, body.roles.get(1));
    assertNull(body.warnings);
  }

  // -------------------------------------------------------------------------
  // selfApproveActivation.
  // -------------------------------------------------------------------------

  @Test
  public void getSelfApproveActivationReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles/self-activate", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void whenBodyIsEmpty_ThenSelfApproveActivationReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post("/api/projects/project-1/roles/self-activate", ExceptionMappers.ErrorEntity.class);

    assertEquals(415, response.getStatus());
  }

  @Test
  public void whenProjectIsNull_ThenSelfApproveActivationReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/%20/roles/self-activate",
      new ApiResource.SelfActivationRequest(),
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("projectId"));
  }

  @Test
  public void whenRolesEmpty_ThenSelfApproveActivationReturnsError() throws Exception {
    var request = new ApiResource.SelfActivationRequest();
    request.roles = List.of();

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/self-activate",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("role"));
  }

  @Test
  public void whenJustificationMissing_ThenSelfApproveActivationReturnsError() throws Exception {
    var request = new ApiResource.SelfActivationRequest();
    request.roles = List.of("roles/browser");

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/self-activate",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("justification"));
  }

  @Test
  public void whenActivationServiceThrowsException_ThenSelfApproveActivationReturnsError() throws Exception {
    when(this.resource.roleActivationService
      .activateProjectRoleForSelf(
        eq(SAMPLE_USER),
        any(RoleBinding.class),
        anyString(),
        any(Duration.class)))
      .thenThrow(new AccessDeniedException("mock"));

    var request = new ApiResource.SelfActivationRequest();
    request.roles = List.of("roles/browser", "roles/browser");
    request.justification = "justification";
    request.activationTimeout = 5;

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/self-activate",
      request,
      ExceptionMappers.ErrorEntity.class);

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("mock"));
  }

  @Test
  public void whenRolesContainDuplicates_ThenSelfApproveActivationSucceedsAndIgnoresDuplicates() throws Exception {
    var roleBinding = new RoleBinding(new ProjectId("project-1"), "roles/browser");

    when(this.resource.roleActivationService
      .activateProjectRoleForSelf(
        eq(SAMPLE_USER),
        eq(roleBinding),
        eq("justification"),
        eq(Duration.ofMinutes(5))))
      .thenReturn(RoleActivationService.Activation.createForTestingOnly(
        RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.JIT),
        new ProjectRole(roleBinding, ProjectRole.Status.ACTIVATED),
        Instant.now(),
        Instant.now().plusSeconds(60)));

    var request = new ApiResource.SelfActivationRequest();
    request.roles = List.of("roles/browser", "roles/browser");
    request.justification = "justification";
    request.activationTimeout = 5;

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/self-activate",
      request,
      ApiResource.ActivationStatusResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertEquals(SAMPLE_USER, body.beneficiary);
    assertEquals(0, body.reviewers.size());
    assertTrue(body.isBeneficiary);
    assertFalse(body.isReviewer);
    assertEquals("justification", body.justification);
    assertNotNull(body.items);
    assertEquals(1, body.items.size());
    assertEquals("project-1", body.items.get(0).projectId);
    assertEquals(roleBinding, body.items.get(0).roleBinding);
    assertEquals(ProjectRole.Status.ACTIVATED, body.items.get(0).status);
    assertNotNull(body.items.get(0).activationId);
  }

  // -------------------------------------------------------------------------
  // requestActivation.
  // -------------------------------------------------------------------------

  @Test
  public void getRequestActivationReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles/request", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void whenBodyIsEmpty_ThenRequestActivationReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post("/api/projects/project-1/roles/request", ExceptionMappers.ErrorEntity.class);

    assertEquals(415, response.getStatus());
  }

  @Test
  public void whenProjectIsNull_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.roleActivationService.getOptions())
      .thenReturn(new RoleActivationService.Options(
        DEFAULT_HINT,
        DEFAULT_JUSTIFICATION_PATTERN,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/%20/roles/request",
      new ApiResource.SelfActivationRequest(),
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("projectId"));
  }

  @Test
  public void whenRoleEmpty_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.roleActivationService.getOptions())
      .thenReturn(new RoleActivationService.Options(
        DEFAULT_HINT,
        DEFAULT_JUSTIFICATION_PATTERN,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = new ApiResource.ActivationRequest();
    request.peers = List.of(SAMPLE_USER.email);
    request.role = null;

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("role"));
  }

  @Test
  public void whenPeersEmpty_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.roleActivationService.getOptions())
      .thenReturn(new RoleActivationService.Options(
        DEFAULT_HINT,
        DEFAULT_JUSTIFICATION_PATTERN,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = new ApiResource.ActivationRequest();
    request.role = "roles/mock";
    request.peers = List.of();

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("reviewers are required"));
  }

  @Test
  public void whenTooFewPeersSelected_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.roleActivationService.getOptions())
        .thenReturn(new RoleActivationService.Options(
            DEFAULT_HINT,
            DEFAULT_JUSTIFICATION_PATTERN,
            DEFAULT_ACTIVATION_DURATION,
            2,
            DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = new ApiResource.ActivationRequest();
    request.role = "roles/mock";
    request.peers = List.of("peer@example.com");

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
        "/api/projects/project-1/roles/request",
        request,
        ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("reviewers are required"));
  }

  @Test
  public void whenTooManyPeersSelected_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.roleActivationService.getOptions())
        .thenReturn(new RoleActivationService.Options(
            DEFAULT_HINT,
            DEFAULT_JUSTIFICATION_PATTERN,
            DEFAULT_ACTIVATION_DURATION,
            DEFAULT_MIN_NUMBER_OF_REVIEWERS,
            DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = new ApiResource.ActivationRequest();
    request.role = "roles/mock";
    request.peers = Stream.generate(() -> "peer@example.com")
        .limit(DEFAULT_MAX_NUMBER_OF_REVIEWERS + 1)
        .collect(Collectors.toList());

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
        "/api/projects/project-1/roles/request",
        request,
        ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("exceed"));
  }

  @Test
  public void whenJustificationEmpty_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.roleActivationService.getOptions())
      .thenReturn(new RoleActivationService.Options(
        DEFAULT_HINT,
        DEFAULT_JUSTIFICATION_PATTERN,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = new ApiResource.ActivationRequest();
    request.peers = List.of(SAMPLE_USER.email);
    request.role = "roles/mock";

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("justification"));
  }

  @Test
  public void whenNotificationsNotConfigured_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.roleActivationService.getOptions())
      .thenReturn(new RoleActivationService.Options(
        DEFAULT_HINT,
        DEFAULT_JUSTIFICATION_PATTERN,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    this.resource.notificationService = Mockito.mock(NotificationService.class);
    when(this.resource.notificationService.canSendNotifications()).thenReturn(false);

    var request = new ApiResource.ActivationRequest();
    request.role = "roles/mock";
    request.peers = List.of(SAMPLE_USER_2.email, SAMPLE_USER_2.email);
    request.justification = "justification";

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(500, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("feature"));
  }

  @Test
  public void whenActivationServiceThrowsException_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.roleActivationService.getOptions())
      .thenReturn(new RoleActivationService.Options(
        DEFAULT_HINT,
        DEFAULT_JUSTIFICATION_PATTERN,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    when(this.resource.roleActivationService
      .createActivationRequestForPeer(
        eq(SAMPLE_USER),
        anySet(),
        any(RoleBinding.class),
        anyString(),
        any(Duration.class)))
      .thenThrow(new AccessDeniedException("mock"));

    var request = new ApiResource.ActivationRequest();
    request.role = "roles/mock";
    request.peers = List.of(SAMPLE_USER_2.email, SAMPLE_USER_2.email);
    request.justification = "justification";
    request.activationTimeout = 5;

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ExceptionMappers.ErrorEntity.class);

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("mock"));
  }

  @Test
  public void whenRequestValid_ThenRequestActivationSendsNotification() throws Exception {
    when(this.resource.roleActivationService.getOptions())
      .thenReturn(new RoleActivationService.Options(
        DEFAULT_HINT,
        DEFAULT_JUSTIFICATION_PATTERN,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var roleBinding = new RoleBinding(new ProjectId("project-1"), "roles/browser");

    when(this.resource.roleActivationService
      .createActivationRequestForPeer(
        eq(SAMPLE_USER),
        eq(Set.of(SAMPLE_USER_2)),
        argThat(r -> r.role.equals("roles/mock")),
        eq("justification"),
        eq(Duration.ofMinutes(5))))
      .thenReturn(RoleActivationService.ActivationRequest.createForTestingOnly(
        RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.JIT),
        SAMPLE_USER,
        Set.of(SAMPLE_USER_2),
        roleBinding,
        "justification",
        Instant.now(),
        Instant.now().plusSeconds(60)));
    when(this.resource.activationTokenService
      .createToken(any(RoleActivationService.ActivationRequest.class)))
      .thenReturn(SAMPLE_TOKEN_WITH_EXPIRY);

    var request = new ApiResource.ActivationRequest();
    request.role = "roles/mock";
    request.peers = List.of(SAMPLE_USER_2.email, SAMPLE_USER_2.email);
    request.justification = "justification";
    request.activationTimeout = 5;

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ApiResource.ActivationStatusResponse.class);
    assertEquals(200, response.getStatus());

    verify(this.resource.notificationService, times(1))
      .sendNotification(argThat(n -> n instanceof ApiResource.RequestActivationNotification));
  }

  @Test
  public void whenRequestValid_ThenRequestActivationReturnsSuccessResponse() throws Exception {
    when(this.resource.roleActivationService.getOptions())
      .thenReturn(new RoleActivationService.Options(
        DEFAULT_HINT,
        DEFAULT_JUSTIFICATION_PATTERN,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var roleBinding = new RoleBinding(new ProjectId("project-1"), "roles/browser");

    when(this.resource.roleActivationService
      .createActivationRequestForPeer(
        eq(SAMPLE_USER),
        eq(Set.of(SAMPLE_USER_2)),
        argThat(r -> r.role.equals("roles/mock")),
        eq("justification"),
        eq(Duration.ofMinutes(5))))
      .thenReturn(RoleActivationService.ActivationRequest.createForTestingOnly(
        RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.JIT),
        SAMPLE_USER,
        Set.of(SAMPLE_USER_2),
        roleBinding,
        "justification",
        Instant.now(),
        Instant.now().plusSeconds(60)));
    when(this.resource.activationTokenService
      .createToken(any(RoleActivationService.ActivationRequest.class)))
      .thenReturn(SAMPLE_TOKEN_WITH_EXPIRY);

    var request = new ApiResource.ActivationRequest();
    request.role = "roles/mock";
    request.peers = List.of(SAMPLE_USER_2.email, SAMPLE_USER_2.email);
    request.justification = "justification";
    request.activationTimeout = 5;

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ApiResource.ActivationStatusResponse.class);

    var body = response.getBody();
    assertEquals(SAMPLE_USER, body.beneficiary);
    assertEquals(Set.of(SAMPLE_USER_2), body.reviewers);
    assertTrue(body.isBeneficiary);
    assertFalse(body.isReviewer);
    assertEquals("justification", body.justification);
    assertNotNull(body.items);
    assertEquals(1, body.items.size());
    assertEquals("project-1", body.items.get(0).projectId);
    assertEquals(roleBinding, body.items.get(0).roleBinding);
    assertEquals(ProjectRole.Status.ACTIVATION_PENDING, body.items.get(0).status);
    assertNotNull(body.items.get(0).activationId);
  }
  
  // -------------------------------------------------------------------------
  // getActivationRequest.
  // -------------------------------------------------------------------------

  @Test
  public void whenTokenMissing_ThenGetActivationRequestReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/activation-request", ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenTokenInvalid_ThenGetActivationRequestReturnsError() throws Exception {
    when(this.resource.activationTokenService.verifyToken(eq(SAMPLE_TOKEN)))
      .thenThrow(new TokenVerifier.VerificationException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenCallerNotInvolvedInRequest_ThenGetActivationRequestReturnsError() throws Exception {
    var request = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      SAMPLE_USER,
      Set.of(SAMPLE_USER_2),
      new RoleBinding(new ProjectId("project-1"), "roles/mock"),
      "a justification",
      Instant.now(),
      Instant.now().plusSeconds(60));

    when(this.resource.activationTokenService.verifyToken(eq(SAMPLE_TOKEN)))
      .thenReturn(request);

    var response = new RestDispatcher<>(this.resource, new UserId("other-party@example.com"))
      .get(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenTokenValid_ThenGetActivationRequestSucceeds() throws Exception {
    var request = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      SAMPLE_USER,
      Set.of(SAMPLE_USER_2),
      new RoleBinding(new ProjectId("project-1"), "roles/mock"),
      "a justification",
      Instant.now(),
      Instant.now().plusSeconds(60));

    when(this.resource.activationTokenService.verifyToken(eq(SAMPLE_TOKEN)))
      .thenReturn(request);

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ApiResource.ActivationStatusResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertEquals(request.beneficiary, body.beneficiary);
    assertEquals(Set.of(SAMPLE_USER_2), request.reviewers);
    assertTrue(body.isBeneficiary);
    assertFalse(body.isReviewer);
    assertEquals(request.justification, body.justification);
    assertEquals(1, body.items.size());
    assertEquals(request.id.toString(), body.items.get(0).activationId);
    assertEquals("project-1", body.items.get(0).projectId);
    assertEquals("ACTIVATION_PENDING", body.items.get(0).status.name());
    assertEquals(request.startTime.getEpochSecond(), body.items.get(0).startTime);
    assertEquals(request.endTime.getEpochSecond(), body.items.get(0).endTime);
  }

  // -------------------------------------------------------------------------
  // approveActivationRequest.
  // -------------------------------------------------------------------------

  @Test
  public void whenTokenMissing_ThenApproveActivationRequestReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post("/api/activation-request", ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenTokenInvalid_ThenApproveActivationRequestReturnsError() throws Exception {
    when(this.resource.activationTokenService.verifyToken(eq(SAMPLE_TOKEN)))
      .thenThrow(new TokenVerifier.VerificationException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenActivationServiceThrowsException_ThenApproveActivationRequestReturnsError() throws Exception {
    var request = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      SAMPLE_USER,
      Set.of(SAMPLE_USER_2),
      new RoleBinding(new ProjectId("project-1"), "roles/mock"),
      "a justification",
      Instant.now(),
      Instant.now().plusSeconds(60));

    when(this.resource.activationTokenService.verifyToken(eq(SAMPLE_TOKEN)))
      .thenReturn(request);
    when(this.resource.roleActivationService
      .activateProjectRoleForPeer(
        eq(SAMPLE_USER),
        eq(request)))
      .thenThrow(new AccessDeniedException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("mock"));
  }

  @Test
  public void whenTokenValid_ThenApproveActivationSendsNotification() throws Exception {
    var request = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      SAMPLE_USER,
      Set.of(SAMPLE_USER_2),
      new RoleBinding(new ProjectId("project-1"), "roles/mock"),
      "a justification",
      Instant.now(),
      Instant.now().plusSeconds(60));
    when(this.resource.activationTokenService.verifyToken(eq(SAMPLE_TOKEN)))
      .thenReturn(request);
    when(this.resource.roleActivationService
      .activateProjectRoleForPeer(
        eq(SAMPLE_USER),
        eq(request)))
      .thenReturn(RoleActivationService.Activation.createForTestingOnly(
        request.id,
        new ProjectRole(request.roleBinding, ProjectRole.Status.ACTIVATED),
        request.startTime,
        request.endTime));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ApiResource.ActivationStatusResponse.class);

    assertEquals(200, response.getStatus());

    verify(this.resource.notificationService, times(1))
      .sendNotification(argThat(n -> n instanceof ApiResource.ActivationApprovedNotification));
  }

  @Test
  public void whenTokenValid_ThenApproveActivationRequestSucceeds() throws Exception {
    var request = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      SAMPLE_USER,
      Set.of(SAMPLE_USER_2),
      new RoleBinding(new ProjectId("project-1"), "roles/mock"),
      "a justification",
      Instant.now(),
      Instant.now().plusSeconds(60));
    when(this.resource.activationTokenService.verifyToken(eq(SAMPLE_TOKEN)))
      .thenReturn(request);
    when(this.resource.roleActivationService
      .activateProjectRoleForPeer(
        eq(SAMPLE_USER_2),
        eq(request)))
      .thenReturn(RoleActivationService.Activation.createForTestingOnly(
        request.id,
        new ProjectRole(request.roleBinding, ProjectRole.Status.ACTIVATED),
        request.startTime,
        request.endTime));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER_2)
      .post(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ApiResource.ActivationStatusResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertEquals(request.beneficiary, body.beneficiary);
    assertEquals(Set.of(SAMPLE_USER_2), request.reviewers);
    assertFalse(body.isBeneficiary);
    assertTrue(body.isReviewer);
    assertEquals(request.justification, body.justification);
    assertEquals(1, body.items.size());
    assertEquals(request.id.toString(), body.items.get(0).activationId);
    assertEquals("project-1", body.items.get(0).projectId);
    assertEquals("ACTIVATED", body.items.get(0).status.name());
    assertEquals(request.startTime.getEpochSecond(), body.items.get(0).startTime);
    assertEquals(request.endTime.getEpochSecond(), body.items.get(0).endTime);
  }
}