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

package com.google.solutions.jitaccess.core.catalog;

import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.UserEmail;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestRequesterPrivilegeActivator {
  private static final UserEmail SAMPLE_REQUESTING_USER = new UserEmail("user@example.com");
  private static final UserEmail SAMPLE_APPROVING_USER = new UserEmail("peer@example.com");
  private static final UserEmail SAMPLE_UNKNOWN_USER = new UserEmail("unknown@example.com");

  private class SampleActivator extends RequesterPrivilegeActivator<SamplePrivilegeId, ResourceId> {
    protected SampleActivator(
        RequesterPrivilegeCatalog<SamplePrivilegeId, ResourceId> catalog,
        JustificationPolicy policy) {
      super(catalog, policy);
    }

    @Override
    protected void provisionAccess(
        UserEmail approvingUser,
        ActivationRequest<SamplePrivilegeId> request)
        throws AccessException, AlreadyExistsException, IOException {
    }

    @Override
    public JsonWebTokenConverter<ActivationRequest<SamplePrivilegeId>> createTokenConverter() {
      return null;
    }
  }

  // -------------------------------------------------------------------------
  // createActivationRequest.
  // -------------------------------------------------------------------------

  @Test
  public void createActivationRequestChecksAccess() throws Exception {
    RequesterPrivilegeCatalog<SamplePrivilegeId, ResourceId> catalog = Mockito.mock(RequesterPrivilegeCatalog.class);

    var activator = new SampleActivator(
        catalog,
        Mockito.mock(JustificationPolicy.class));

    var privilege = new SamplePrivilegeId("cat", "1");
    var requesterPrivilege = new RequesterPrivilege<>(
        privilege,
        privilege.id(),
        new ExternalApproval("topic"),
        RequesterPrivilege.Status.INACTIVE);
    var request = activator.createActivationRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(SAMPLE_APPROVING_USER),
        requesterPrivilege,
        "justification",
        Instant.now(),
        Duration.ofMinutes(5));

    assertNotNull(request);
    assertEquals(SAMPLE_REQUESTING_USER, request.requestingUser());
    assertEquals(privilege, request.requesterPrivilege());

    verify(catalog, times(1)).verifyUserCanRequest(request);
  }

  @Test
  public void whenUserNotAllowedToRequest_ThenCreatePeerApprovalRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(RequesterPrivilegeCatalog.class);

    Mockito.doThrow(new AccessDeniedException("mock"))
        .when(catalog)
        .verifyUserCanRequest(any());

    var activator = new SampleActivator(
        catalog,
        Mockito.mock(JustificationPolicy.class));

    var privilege = new SamplePrivilegeId("cat", "1");
    var requesterPrivilege = new RequesterPrivilege<>(
        privilege,
        privilege.id(),
        new ExternalApproval("topic"),
        RequesterPrivilege.Status.INACTIVE);

    assertThrows(
        AccessDeniedException.class,
        () -> activator.createActivationRequest(
            SAMPLE_REQUESTING_USER,
            Set.of(SAMPLE_APPROVING_USER),
            requesterPrivilege,
            "justification",
            Instant.now(),
            Duration.ofMinutes(5)));
  }

  // -------------------------------------------------------------------------
  // approve.
  // -------------------------------------------------------------------------

  @Test
  public void whenJustificationInvalid_ThenApproveRequestThrowsException() throws Exception {
    var justificationPolicy = Mockito.mock(JustificationPolicy.class);

    Mockito.doThrow(new InvalidJustificationException("mock"))
        .when(justificationPolicy)
        .checkJustification(eq(SAMPLE_REQUESTING_USER), anyString());

    var activator = new SampleActivator(
        Mockito.mock(RequesterPrivilegeCatalog.class),
        justificationPolicy);

    var privilege = new SamplePrivilegeId("cat", "1");
    var requesterPrivilege = new RequesterPrivilege<>(
        privilege,
        privilege.id(),
        new ExternalApproval("topic"),
        RequesterPrivilege.Status.INACTIVE);

    var request = activator.createActivationRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(SAMPLE_APPROVING_USER),
        requesterPrivilege,
        "justification",
        Instant.now(),
        Duration.ofMinutes(5));

    assertThrows(
        InvalidJustificationException.class,
        () -> activator.approve(SAMPLE_APPROVING_USER, request));
  }

  @Test
  public void whenUserNotAllowedToRequest_ThenApproveRequestThrowsException() throws Exception {
    RequesterPrivilegeCatalog<SamplePrivilegeId, ResourceId> catalog = Mockito.mock(RequesterPrivilegeCatalog.class);

    Mockito.doThrow(new AccessDeniedException("mock"))
        .when(catalog)
        .verifyUserCanRequest(any());

    var activator = new SampleActivator(
        catalog,
        Mockito.mock(JustificationPolicy.class));

    var privilege = new SamplePrivilegeId("cat", "1");
    var requesterPrivilege = new RequesterPrivilege<>(
        privilege,
        privilege.id(),
        new ExternalApproval("topic"),
        RequesterPrivilege.Status.INACTIVE);

    var request = new ActivationRequest<SamplePrivilegeId>(
        ActivationId.newId(requesterPrivilege.activationType()),
        SAMPLE_REQUESTING_USER,
        Set.of(SAMPLE_APPROVING_USER),
        requesterPrivilege.id(),
        requesterPrivilege.activationType(),
        "justification",
        Instant.now(),
        Duration.ofMinutes(5));

    assertThrows(
        AccessDeniedException.class,
        () -> activator.approve(SAMPLE_APPROVING_USER, request));
  }

  @Test
  public void whenApprovingUserUnknown_ThenApproveRequestThrowsException() throws Exception {
    var activator = new SampleActivator(
        Mockito.mock(RequesterPrivilegeCatalog.class),
        Mockito.mock(JustificationPolicy.class));

    var privilege = new SamplePrivilegeId("cat", "1");
    var requesterPrivilege = new RequesterPrivilege<>(
        privilege,
        privilege.id(),
        new ExternalApproval("topic"),
        RequesterPrivilege.Status.INACTIVE);

    var request = activator.createActivationRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(SAMPLE_APPROVING_USER),
        requesterPrivilege,
        "justification",
        Instant.now(),
        Duration.ofMinutes(5));

    assertThrows(
        AccessDeniedException.class,
        () -> activator.approve(SAMPLE_UNKNOWN_USER, request));
  }

  @Test
  public void whenRequestingUserNotAllowedToRequestAnymore_ThenApproveRequestThrowsException()
      throws Exception {
    var catalog = Mockito.mock(RequesterPrivilegeCatalog.class);
    var activator = new SampleActivator(
        catalog,
        Mockito.mock(JustificationPolicy.class));

    var privilege = new SamplePrivilegeId("cat", "1");
    var requesterPrivilege = new RequesterPrivilege<>(
        privilege,
        privilege.id(),
        new ExternalApproval("topic"),
        RequesterPrivilege.Status.INACTIVE);

    var request = activator.createActivationRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(SAMPLE_APPROVING_USER),
        requesterPrivilege,
        "justification",
        Instant.now(),
        Duration.ofMinutes(5));

    Mockito.doThrow(new AccessDeniedException("mock"))
        .when(catalog)
        .verifyUserCanRequest(any());

    assertThrows(
        AccessDeniedException.class,
        () -> activator.approve(SAMPLE_APPROVING_USER, request));
  }

  @Test
  public void whenApprovingUserNotAllowedToApprove_ThenApproveRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(RequesterPrivilegeCatalog.class);
    var activator = new SampleActivator(
        catalog,
        Mockito.mock(JustificationPolicy.class));

    var privilege = new SamplePrivilegeId("cat", "1");
    var requesterPrivilege = new RequesterPrivilege<>(
        privilege,
        privilege.id(),
        new ExternalApproval("topic"),
        RequesterPrivilege.Status.INACTIVE);

    var request = activator.createActivationRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(SAMPLE_APPROVING_USER),
        requesterPrivilege,
        "justification",
        Instant.now(),
        Duration.ofMinutes(5));

    Mockito.doThrow(new AccessDeniedException("mock"))
        .when(catalog)
        .verifyUserCanApprove(eq(SAMPLE_APPROVING_USER), any());

    assertThrows(
        AccessDeniedException.class,
        () -> activator.approve(SAMPLE_APPROVING_USER, request));
  }
}