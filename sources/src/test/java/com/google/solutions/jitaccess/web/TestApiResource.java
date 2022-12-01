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

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.adapters.LogAdapter;
import com.google.solutions.jitaccess.core.data.ProjectId;
import com.google.solutions.jitaccess.core.data.ProjectRole;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserId;
import com.google.solutions.jitaccess.core.services.Result;
import com.google.solutions.jitaccess.core.services.RoleActivationService;
import com.google.solutions.jitaccess.core.services.RoleDiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class TestApiResource {
  private static final UserId SAMPLE_USER = new UserId("mock", "mock@example.com");

  private ApiResource resource;

  @BeforeEach
  public void before() {
    this.resource = new ApiResource();
    this.resource.logAdapter = new LogAdapter();
    this.resource.roleDiscoveryService = Mockito.mock(RoleDiscoveryService.class);
    this.resource.roleActivationService = Mockito.mock(RoleActivationService.class);
  }

  // -------------------------------------------------------------------------
  // Invalid path.
  // -------------------------------------------------------------------------

  @Test
  public void whenPathNotMapped_ThenGetReturnsError() throws Exception {
    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .get("/api/unknown", ExceptionMappers.ErrorEntity.class);

    assertEquals(404, response.getStatus());
  }

  // -------------------------------------------------------------------------
  // /api/policy.
  // -------------------------------------------------------------------------

  @Test
  public void getPolicyReturnsJustificationHint() throws Exception {
    when(this.resource.roleActivationService.getOptions())
      .thenReturn(new RoleActivationService.Options(
        "hint",
        Pattern.compile("pattern"),
        Duration.ofMinutes(5)));

    var response = new RestDispatcher<ApiResource>(resource, SAMPLE_USER)
      .get("/api/policy", ApiResource.PolicyResponseEntity.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body);
    assertEquals("hint", body.justificationHint);
  }

  // -------------------------------------------------------------------------
  // /api/projects.
  // -------------------------------------------------------------------------

  @Test
  public void postProjectsReturnsError() throws Exception {
    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .post("/api/projects", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void whenProjectDiscoveryThrowsAccessDeniedException_ThenGetProjectsReturnsError() throws Exception {
    when(this.resource.roleDiscoveryService.listAvailableProjects(eq(SAMPLE_USER)))
      .thenThrow(new AccessDeniedException("mock"));

    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .get("/api/projects", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenProjectDiscoveryThrowsIOException_ThenGetProjectsReturnsError() throws Exception {
    when(this.resource.roleDiscoveryService.listAvailableProjects(eq(SAMPLE_USER)))
      .thenThrow(new IOException("mock"));

    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .get("/api/projects", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenProjectDiscoveryyReturnsNoProjects_ThenGetProjectsReturnsEmptyList() throws Exception {
    when(this.resource.roleDiscoveryService.listAvailableProjects(eq(SAMPLE_USER)))
      .thenReturn(Set.<ProjectId>of());

    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .get("/api/projects", ApiResource.ProjectsResponseEntity.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.projects);
    assertEquals(0, body.projects.size());
  }

  @Test
  public void whenProjectDiscoveryReturnsProjects_ThenGetProjectsReturnsList() throws Exception {
    when(this.resource.roleDiscoveryService.listAvailableProjects(eq(SAMPLE_USER)))
      .thenReturn(Set.of(new ProjectId("project-1"), new ProjectId("project-2")));

    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .get("/api/projects", ApiResource.ProjectsResponseEntity.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.projects);
    assertEquals(2, body.projects.size());
  }

  // -------------------------------------------------------------------------
  // /api/projects/{projectId}/roles.
  // -------------------------------------------------------------------------

  @Test
  public void postProjectsRolesReturnsError() throws Exception {
    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .post("/api/projects/project-1/roles", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void whenProjectIsEmpty_ThenGetProjectRolesReturnsError() throws Exception {
    when(this.resource.roleDiscoveryService.listAvailableProjects(eq(SAMPLE_USER)))
      .thenThrow(new AccessDeniedException("mock"));

    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .get("/api/projects/%20/roles", ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("projectId"));
  }

  @Test
  public void whenRoleDiscoveryThrowsAccessDeniedException_ThenGetProjectsReturnsError() throws Exception {
    when(this.resource.roleDiscoveryService
      .listEligibleProjectRoles(
        eq(SAMPLE_USER),
        eq(new ProjectId("project-1"))))
      .thenThrow(new AccessDeniedException("mock"));

    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenRoleDiscoveryThrowsIOException_ThenGetProjectsReturnsError() throws Exception {
    when(this.resource.roleDiscoveryService
      .listEligibleProjectRoles(
        eq(SAMPLE_USER),
        eq(new ProjectId("project-1"))))
      .thenThrow(new IOException("mock"));

    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenRoleDiscoveryyReturnsNoRoles_ThenGetProjectsReturnsEmptyList() throws Exception {
    when(this.resource.roleDiscoveryService
      .listEligibleProjectRoles(
        eq(SAMPLE_USER),
        eq(new ProjectId("project-1"))))
      .thenReturn(new Result<ProjectRole>(
        List.<ProjectRole>of(),
        List.of("warning")));

    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles", ApiResource.ProjectRolesResponseEntity.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.roles);
    assertEquals(0, body.roles.size());
    assertNotNull(body.warnings);
    assertEquals(1, body.warnings.size());
    assertEquals("warning", body.warnings.get(0));
  }

  @Test
  public void whenRoleDiscoveryReturnsRoles_ThenGetProjectsReturnsList() throws Exception {
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
      .thenReturn(new Result<ProjectRole>(
        List.<ProjectRole>of(role1, role2),
        null));

    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles", ApiResource.ProjectRolesResponseEntity.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.roles);
    assertEquals(2, body.roles.size());
    assertEquals(role1, body.roles.get(0));
    assertEquals(role2, body.roles.get(1));
    assertNull(body.warnings);
  }


  // -------------------------------------------------------------------------
  // /api/projects/{projectId}/roles/self-activate.
  // -------------------------------------------------------------------------

  @Test
  public void getSelfActivateReturnsError() throws Exception {
    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles/self-activate", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void whenBodyIsEmpty_ThenSelfActivateReturnsError() throws Exception {
    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER)
      .post("/api/projects/project-1/roles/self-activate", ExceptionMappers.ErrorEntity.class);

    assertEquals(415, response.getStatus());
  }

  @Test
  public void whenProjectIsNull_ThenSelfActivateReturnsError() throws Exception {
    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER).post(
      "/api/projects/%20/roles/self-activate",
      new ApiResource.SelfActivationRequestEntity(),
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("projectId"));
  }

  @Test
  public void whenRolesEmpty_ThenSelfActivateReturnsError() throws Exception {
    var request = new ApiResource.SelfActivationRequestEntity();
    request.roles = List.<String>of();

    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/self-activate",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("role"));
  }

  @Test
  public void whenJustificationMissing_ThenSelfActivateReturnsError() throws Exception {
    var request = new ApiResource.SelfActivationRequestEntity();
    request.roles = List.<String>of("roles/browser");

    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/self-activate",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("justification"));
  }

  @Test
  public void whenRolesContainDuplicates_ThenDuplicatesAreIgnored() throws Exception {
    var roleBinding = new RoleBinding(new ProjectId("project-1"), "roles/browser");

    when(this.resource.roleActivationService
      .activateProjectRole(
        eq(SAMPLE_USER),
        eq(SAMPLE_USER),
        eq(roleBinding),
        eq(RoleActivationService.ActivationType.JIT),
        eq("justification")))
      .thenReturn(new RoleActivationService.Activation(
        new ProjectRole(roleBinding, ProjectRole.Status.ACTIVATED),
        OffsetDateTime.now()));

    var request = new ApiResource.SelfActivationRequestEntity();
    request.roles = List.<String>of("roles/browser", "roles/browser");
    request.justification = "justification";

    var response = new RestDispatcher<ApiResource>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/self-activate",
      request,
      ApiResource.SelfActivationResponseEntity.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.activatedRoles);
    assertEquals(1, body.activatedRoles.size());
    assertEquals(roleBinding, body.activatedRoles.get(0).projectRole.roleBinding);
    assertEquals(ProjectRole.Status.ACTIVATED, body.activatedRoles.get(0).projectRole.status);
  }
}