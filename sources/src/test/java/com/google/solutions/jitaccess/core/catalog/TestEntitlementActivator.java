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

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AlreadyExistsException;
import com.google.solutions.jitaccess.core.UserId;
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

public class TestEntitlementActivator {
  private static final UserId SAMPLE_REQUESTING_USER = new UserId("user@example.com");
  private static final UserId SAMPLE_APPROVING_USER = new UserId("peer@example.com");
  private static final UserId SAMPLE_UNKNOWN_USER = new UserId("unknown@example.com");

  private class SampleActivator extends EntitlementActivator<SampleEntitlementId> {
    protected SampleActivator(
      EntitlementCatalog<SampleEntitlementId> catalog,
      JustificationPolicy policy
    ) {
      super(catalog, policy);
    }

    @Override
    protected void provisionAccess(
      UserId approvingUser,
      ActivationRequest<SampleEntitlementId> request
    ) throws AccessException, AlreadyExistsException, IOException {
    }

    @Override
    public JsonWebTokenConverter<ActivationRequest<SampleEntitlementId>> createTokenConverter() {
      return null;
    }
  }

  // -------------------------------------------------------------------------
  // createSelfApprovalRequest.
  // -------------------------------------------------------------------------

  @Test
  public void createSelfApprovalRequestDoesNotCheckAccess() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);

    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var entitlements = Set.of(new SampleEntitlementId("cat", "1"));
    var request = activator.createSelfApprovalRequest(
      SAMPLE_REQUESTING_USER,
      entitlements,
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertNotNull(request);
    assertEquals(SAMPLE_REQUESTING_USER, request.requestingUser());
    assertIterableEquals(entitlements, request.entitlements());

    verify(catalog, times(0)).verifyUserCanRequest(request);
  }

  // -------------------------------------------------------------------------
  // createPeerApprovalRequest.
  // -------------------------------------------------------------------------

  @Test
  public void  whenUserNotAllowedToRequest_ThenCreatePeerApprovalRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);

    Mockito.doThrow(new AccessDeniedException("mock"))
      .when(catalog)
      .verifyUserCanRequest(any());

    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    assertThrows(
      AccessDeniedException.class,
      () -> activator.createPeerApprovalRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(new SampleEntitlementId("cat", "1")),
        Set.of(SAMPLE_APPROVING_USER),
        "justification",
        Instant.now(),
        Duration.ofMinutes(5)));
  }

  @Test
  public void  whenMultipleEntitlementsRequest_ThenCreatePeerApprovalRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);

    Mockito.doNothing()
      .when(catalog)
      .verifyUserCanRequest(any());

    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    assertThrows(
      IllegalArgumentException.class,
      () -> activator.createPeerApprovalRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(new SampleEntitlementId("cat", "1"), new SampleEntitlementId("cat", "2")),
        Set.of(SAMPLE_APPROVING_USER),
        "justification",
        Instant.now(),
        Duration.ofMinutes(5)));
  }

  // -------------------------------------------------------------------------
  // createExternalApprovalRequest.
  // -------------------------------------------------------------------------

  @Test
  public void  whenUserNotAllowedToRequest_ThenCreateExternalApprovalRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);

    Mockito.doThrow(new AccessDeniedException("mock"))
      .when(catalog)
      .verifyUserCanRequest(any());

    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    assertThrows(
      AccessDeniedException.class,
      () -> activator.createExternalApprovalRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(new SampleEntitlementId("cat", "1")),
        Set.of(SAMPLE_APPROVING_USER),
        "justification",
        Instant.now(),
        Duration.ofMinutes(5)));
  }

  @Test
  public void  whenMultipleEntitlementsRequest_ThenCreateExternalApprovalRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);

    Mockito.doNothing()
      .when(catalog)
      .verifyUserCanRequest(any());

    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    assertThrows(
      IllegalArgumentException.class,
      () -> activator.createExternalApprovalRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(new SampleEntitlementId("cat", "1"), new SampleEntitlementId("cat", "2")),
        Set.of(SAMPLE_APPROVING_USER),
        "justification",
        Instant.now(),
        Duration.ofMinutes(5)));
  }

  // -------------------------------------------------------------------------
  // approve (self approval).
  // -------------------------------------------------------------------------

  @Test
  public void whenJustificationInvalid_ThenApproveSelfApprovalRequestThrowsException() throws Exception {
    var justificationPolicy = Mockito.mock(JustificationPolicy.class);

    Mockito.doThrow(new InvalidJustificationException("mock"))
      .when(justificationPolicy)
      .checkJustification(eq(SAMPLE_REQUESTING_USER), anyString());

    var activator = new SampleActivator(
      Mockito.mock(EntitlementCatalog.class),
      justificationPolicy);

    var request = activator.createSelfApprovalRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new SampleEntitlementId("cat", "1")),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertThrows(
      InvalidJustificationException.class,
      () -> activator.approve(SAMPLE_REQUESTING_USER, request));
  }

  @Test
  public void whenUserNotAllowedToRequest_ThenApproveSelfApprovalRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);

    Mockito.doThrow(new AccessDeniedException("mock"))
      .when(catalog)
      .verifyUserCanRequest(any());

    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createSelfApprovalRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new SampleEntitlementId("cat", "1")),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertThrows(
      AccessDeniedException.class,
      () -> activator.approve(SAMPLE_REQUESTING_USER, request));
  }

  // -------------------------------------------------------------------------
  // approve (peer approval).
  // -------------------------------------------------------------------------

  @Test
  public void whenApprovingUserUnknown_ThenApprovePeerApprovalRequestThrowsException() throws Exception {
    var activator = new SampleActivator(
      Mockito.mock(EntitlementCatalog.class),
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createPeerApprovalRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertThrows(
      AccessDeniedException.class,
      () -> activator.approve(SAMPLE_UNKNOWN_USER, request));
  }

  @Test
  public void whenJustificationInvalid_ThenApprovePeerApprovalRequestThrowsException() throws Exception {
    var justificationPolicy = Mockito.mock(JustificationPolicy.class);

    Mockito.doThrow(new InvalidJustificationException("mock"))
      .when(justificationPolicy)
      .checkJustification(eq(SAMPLE_REQUESTING_USER), anyString());

    var activator = new SampleActivator(
      Mockito.mock(EntitlementCatalog.class),
      justificationPolicy);

    var request = activator.createPeerApprovalRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertThrows(
      InvalidJustificationException.class,
      () -> activator.approve(SAMPLE_APPROVING_USER, request));
  }

  @Test
  public void whenRequestingUserNotAllowedToRequestAnymore_ThenApprovePeerApprovalRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);
    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createPeerApprovalRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
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
  public void whenApprovingUserNotAllowedToApprove_ThenApprovePeerApprovalRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);
    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createPeerApprovalRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
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

  // -------------------------------------------------------------------------
  // approve (external approval).
  // -------------------------------------------------------------------------

  @Test
  public void whenApprovingUserUnknown_ThenApproveExternalApprovalRequestThrowsException() throws Exception {
    var activator = new SampleActivator(
      Mockito.mock(EntitlementCatalog.class),
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createExternalApprovalRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertThrows(
      AccessDeniedException.class,
      () -> activator.approve(SAMPLE_UNKNOWN_USER, request));
  }

  @Test
  public void whenJustificationInvalid_ThenApproveExternalApprovalRequestThrowsException() throws Exception {
    var justificationPolicy = Mockito.mock(JustificationPolicy.class);

    Mockito.doThrow(new InvalidJustificationException("mock"))
      .when(justificationPolicy)
      .checkJustification(eq(SAMPLE_REQUESTING_USER), anyString());

    var activator = new SampleActivator(
      Mockito.mock(EntitlementCatalog.class),
      justificationPolicy);

    var request = activator.createExternalApprovalRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertThrows(
      InvalidJustificationException.class,
      () -> activator.approve(SAMPLE_APPROVING_USER, request));
  }

  @Test
  public void whenRequestingUserNotAllowedToRequestAnymore_ThenApproveExternalApprovalRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);
    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createExternalApprovalRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
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
  public void whenApprovingUserNotAllowedToApprove_ThenApproveExternalApprovalRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);
    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createPeerApprovalRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
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
