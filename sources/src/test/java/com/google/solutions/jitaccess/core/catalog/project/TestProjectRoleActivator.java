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

import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.Catalog;
import com.google.solutions.jitaccess.core.catalog.JustificationPolicy;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestProjectRoleActivator {

  private static final UserId SAMPLE_REQUESTING_USER = new UserId("user@example.com");
  private static final UserId SAMPLE_APPROVING_USER = new UserId("approver@example.com");
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");
  private static final String SAMPLE_ROLE_1 = "roles/resourcemanager.role1";
  private static final String SAMPLE_ROLE_2 = "roles/resourcemanager.role2";

  private static final Instant JAN1_2040 = Instant.ofEpochSecond(2208988800L);

  // -------------------------------------------------------------------------
  // provisionAccess - JIT.
  // -------------------------------------------------------------------------

  @Test
  public void provisionAccessForJitRequest() throws Exception {
    var resourceManagerClient = Mockito.mock(ResourceManagerClient.class);
    var activator = new ProjectRoleActivator(
      Mockito.mock(Catalog.class),
      resourceManagerClient,
      Mockito.mock(JustificationPolicy.class),
      new ProjectRoleActivator.Options(2));

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    var request = activator.createJitRequest(
      requestingUserContext,
      Set.of(
        new ProjectRole(SAMPLE_PROJECT, SAMPLE_ROLE_1),
        new ProjectRole(SAMPLE_PROJECT, SAMPLE_ROLE_2)),
      "justification",
      JAN1_2040,
      Duration.ofMinutes(5));

    var activation = activator.activate(requestingUserContext, request);

    assertNotNull(activation);
    assertEquals(request.startTime(), activation.validity().start());
    assertEquals(request.endTime(), activation.validity().end());

    verify(resourceManagerClient, times(2))
      .addProjectIamBinding(
        eq(SAMPLE_PROJECT),
        argThat(b ->
          b.getCondition().getExpression().equals(
            "(request.time >= timestamp(\"2040-01-01T00:00:00Z\") && request.time < timestamp(\"2040-01-01T00:05:00Z\"))") &&
          b.getCondition().getTitle().equals(ProjectRole.ActivationCondition.TITLE)),
        eq(EnumSet.of(ResourceManagerClient.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS)),
        eq("Self-approved, justification: justification"));
  }

  @Test
  public void provisionAccessForJitRequestWithResourceCondition() throws Exception {
    var resourceManagerClient = Mockito.mock(ResourceManagerClient.class);
    var activator = new ProjectRoleActivator(
      Mockito.mock(Catalog.class),
      resourceManagerClient,
      Mockito.mock(JustificationPolicy.class),
      new ProjectRoleActivator.Options(2));

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    var request = activator.createJitRequest(
      requestingUserContext,
      Set.of(
        new ProjectRole(SAMPLE_PROJECT, SAMPLE_ROLE_1, "resource.name=='x' || resource.name=='y'")),
      "justification",
      JAN1_2040,
      Duration.ofMinutes(5));

    var activation = activator.activate(requestingUserContext, request);

    assertNotNull(activation);
    assertEquals(request.startTime(), activation.validity().start());
    assertEquals(request.endTime(), activation.validity().end());

    verify(resourceManagerClient, times(1))
      .addProjectIamBinding(
        eq(SAMPLE_PROJECT),
        argThat(b ->
          b.getCondition().getExpression().equals(
            "((request.time >= timestamp(\"2040-01-01T00:00:00Z\") && request.time < timestamp(\"2040-01-01T00:05:00Z\"))) && (resource.name=='x' || resource.name=='y')") &&
            b.getCondition().getTitle().equals(ProjectRole.ActivationCondition.TITLE)),
        eq(EnumSet.of(ResourceManagerClient.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS)),
        eq("Self-approved, justification: justification"));
  }

  // -------------------------------------------------------------------------
  // provisionAccess - MPA.
  // -------------------------------------------------------------------------

  @Test
  public void provisionAccessForMpaRequest() throws Exception {
    var resourceManagerClient = Mockito.mock(ResourceManagerClient.class);
    var activator = new ProjectRoleActivator(
      Mockito.mock(Catalog.class),
      resourceManagerClient,
      Mockito.mock(JustificationPolicy.class),
      new ProjectRoleActivator.Options(1));

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    var request = activator.createMpaRequest(
      requestingUserContext,
      Set.of(new ProjectRole(SAMPLE_PROJECT, SAMPLE_ROLE_1)),
      Set.of(SAMPLE_APPROVING_USER),
      "justification",
      JAN1_2040,
      Duration.ofMinutes(5));

    var approvingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_APPROVING_USER);
    var activation = activator.approve(
      approvingUserContext,
      request);

    assertNotNull(activation);
    assertEquals(request.startTime(), activation.validity().start());
    assertEquals(request.endTime(), activation.validity().end());

    verify(resourceManagerClient, times(1))
      .addProjectIamBinding(
        eq(SAMPLE_PROJECT),
        argThat(b ->
          b.getCondition().getExpression().equals(
            "(request.time >= timestamp(\"2040-01-01T00:00:00Z\") && request.time < timestamp(\"2040-01-01T00:05:00Z\"))") &&
          b.getCondition().getTitle().equals(ProjectRole.ActivationCondition.TITLE)),
        eq(EnumSet.of(ResourceManagerClient.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS)),
        eq("Approved by approver@example.com, justification: justification"));
  }

  @Test
  public void provisionAccessForMpaRequestWithResourceCondition() throws Exception {
    var resourceManagerClient = Mockito.mock(ResourceManagerClient.class);
    var activator = new ProjectRoleActivator(
      Mockito.mock(Catalog.class),
      resourceManagerClient,
      Mockito.mock(JustificationPolicy.class),
      new ProjectRoleActivator.Options(1));

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    var request = activator.createMpaRequest(
      requestingUserContext,
      Set.of(new ProjectRole(SAMPLE_PROJECT, SAMPLE_ROLE_1, "resource.type=='foo'")),
      Set.of(SAMPLE_APPROVING_USER),
      "justification",
      JAN1_2040,
      Duration.ofMinutes(5));

    var approvingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_APPROVING_USER);
    var activation = activator.approve(
      approvingUserContext,
      request);

    assertNotNull(activation);
    assertEquals(request.startTime(), activation.validity().start());
    assertEquals(request.endTime(), activation.validity().end());

    verify(resourceManagerClient, times(1))
      .addProjectIamBinding(
        eq(SAMPLE_PROJECT),
        argThat(b ->
          b.getCondition().getExpression().equals(
            "((request.time >= timestamp(\"2040-01-01T00:00:00Z\") && request.time < timestamp(\"2040-01-01T00:05:00Z\"))) && (resource.type=='foo')") &&
            b.getCondition().getTitle().equals(ProjectRole.ActivationCondition.TITLE)),
        eq(EnumSet.of(ResourceManagerClient.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS)),
        eq("Approved by approver@example.com, justification: justification"));
  }

  // -------------------------------------------------------------------------
  // createTokenConverter.
  // -------------------------------------------------------------------------

  @Test
  public void createTokenConverter() throws Exception {
    var activator = new ProjectRoleActivator(
      Mockito.mock(Catalog.class),
      Mockito.mock(ResourceManagerClient.class),
      Mockito.mock(JustificationPolicy.class),
      new ProjectRoleActivator.Options(1));

    var requestingUserContext = new MpaProjectRoleCatalog.UserContext(SAMPLE_REQUESTING_USER);
    var inputRequest = activator.createMpaRequest(
      requestingUserContext,
      Set.of(new ProjectRole(SAMPLE_PROJECT, SAMPLE_ROLE_1)),
      Set.of(SAMPLE_APPROVING_USER),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    var payload = activator
      .createTokenConverter()
      .convert(inputRequest);

    var outputRequest = activator
      .createTokenConverter()
      .convert(payload);

    assertEquals(inputRequest.requestingUser(), outputRequest.requestingUser());
    assertIterableEquals(inputRequest.reviewers(), outputRequest.reviewers());
    assertIterableEquals(inputRequest.entitlements(), outputRequest.entitlements());
    assertEquals(inputRequest.justification(), outputRequest.justification());
    assertEquals(inputRequest.startTime().getEpochSecond(), outputRequest.startTime().getEpochSecond());
    assertEquals(inputRequest.endTime().getEpochSecond(), outputRequest.endTime().getEpochSecond());
  }
}
