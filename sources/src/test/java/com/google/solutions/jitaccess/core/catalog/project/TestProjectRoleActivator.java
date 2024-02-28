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

import com.google.solutions.jitaccess.cel.TemporaryIamCondition;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilegeCatalog;
import com.google.solutions.jitaccess.core.catalog.SelfApproval;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege.Status;
import com.google.solutions.jitaccess.core.catalog.JustificationPolicy;
import com.google.solutions.jitaccess.core.catalog.PeerApproval;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege;
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

  private static final UserEmail SAMPLE_REQUESTING_USER = new UserEmail("user@example.com");
  private static final UserEmail SAMPLE_APPROVING_USER = new UserEmail("approver@example.com");
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");
  private static final String SAMPLE_ROLE_1 = "roles/resourcemanager.role1";
  private static final String SAMPLE_ROLE_2 = "roles/resourcemanager.role2";

  // -------------------------------------------------------------------------
  // provisionAccess - JIT.
  // -------------------------------------------------------------------------

  @Test
  public void provisionAccessForJitRequest() throws Exception {
    var resourceManagerClient = Mockito.mock(ResourceManagerClient.class);
    var activator = new ProjectRoleActivator(
        Mockito.mock(RequesterPrivilegeCatalog.class),
        resourceManagerClient,
        Mockito.mock(JustificationPolicy.class));

    var privilege = new RequesterPrivilege<ProjectRoleBinding>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE_1)),
        SAMPLE_ROLE_1,
        new SelfApproval(),
        Status.INACTIVE);

    var request = activator.createActivationRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(),
        privilege,
        "justification",
        Instant.now(),
        Duration.ofMinutes(5));

    var activation = activator.approve(SAMPLE_REQUESTING_USER, request);

    assertNotNull(activation);
    assertSame(request, activation.request());

    verify(resourceManagerClient, times(1))
        .addProjectIamBinding(
            eq(SAMPLE_PROJECT),
            argThat(b -> TemporaryIamCondition.isTemporaryAccessCondition(b.getCondition().getExpression())
                &&
                b.getCondition().getTitle().equals(PrivilegeFactory.ACTIVATION_CONDITION_TITLE)),
            eq(EnumSet.of(ResourceManagerClient.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS)),
            eq("Approved by user@example.com, justification: justification"));
  }

  // -------------------------------------------------------------------------
  // provisionAccess - MPA.
  // -------------------------------------------------------------------------

  @Test
  public void provisionAccessForMpaRequest() throws Exception {
    var resourceManagerClient = Mockito.mock(ResourceManagerClient.class);
    var activator = new ProjectRoleActivator(
        Mockito.mock(RequesterPrivilegeCatalog.class),
        resourceManagerClient,
        Mockito.mock(JustificationPolicy.class));

    var privilege = new RequesterPrivilege<ProjectRoleBinding>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE_1)),
        SAMPLE_ROLE_1,
        new PeerApproval("topic"),
        Status.INACTIVE);

    var request = activator.createActivationRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(SAMPLE_APPROVING_USER),
        privilege,
        "justification",
        Instant.now(),
        Duration.ofMinutes(5));

    var activation = activator.approve(
        SAMPLE_APPROVING_USER,
        request);

    assertNotNull(activation);
    assertSame(request, activation.request());

    verify(resourceManagerClient, times(1))
        .addProjectIamBinding(
            eq(SAMPLE_PROJECT),
            argThat(b -> TemporaryIamCondition.isTemporaryAccessCondition(b.getCondition().getExpression())
                &&
                b.getCondition().getTitle().equals(PrivilegeFactory.ACTIVATION_CONDITION_TITLE)),
            eq(EnumSet.of(ResourceManagerClient.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS)),
            eq("Approved by approver@example.com, justification: justification"));
  }

  // -------------------------------------------------------------------------
  // createTokenConverter.
  // -------------------------------------------------------------------------

  @Test
  public void createTokenConverter() throws Exception {
    var activator = new ProjectRoleActivator(
        Mockito.mock(RequesterPrivilegeCatalog.class),
        Mockito.mock(ResourceManagerClient.class),
        Mockito.mock(JustificationPolicy.class));

    var privilege = new RequesterPrivilege<ProjectRoleBinding>(
        new ProjectRoleBinding(new RoleBinding(SAMPLE_PROJECT, SAMPLE_ROLE_1)),
        SAMPLE_ROLE_1,
        new PeerApproval("topic"),
        Status.INACTIVE);

    var inputRequest = activator.createActivationRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(SAMPLE_APPROVING_USER),
        privilege,
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
    assertEquals(inputRequest.requesterPrivilege(), outputRequest.requesterPrivilege());
    assertEquals(inputRequest.justification(), outputRequest.justification());
    assertEquals(inputRequest.startTime().getEpochSecond(), outputRequest.startTime().getEpochSecond());
    assertEquals(inputRequest.endTime().getEpochSecond(), outputRequest.endTime().getEpochSecond());
    assertEquals(inputRequest.activationType().name(), outputRequest.activationType().name());
  }
}
