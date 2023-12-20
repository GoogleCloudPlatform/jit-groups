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
      JitActivationRequest<SampleEntitlementId> request
    ) throws AccessException, AlreadyExistsException, IOException {
    }

    @Override
    protected void provisionAccess(
      UserId approvingUser,
      MpaActivationRequest<SampleEntitlementId> request
    ) throws AccessException, AlreadyExistsException, IOException {
    }

    @Override
    public JsonWebTokenConverter<MpaActivationRequest<SampleEntitlementId>> createTokenConverter() {
      return null;
    }
  }

  // -------------------------------------------------------------------------
  // createJitRequest.
  // -------------------------------------------------------------------------

  @Test
  public void createJitRequestDoesNotCheckAccess() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);

    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var entitlements = Set.of(new SampleEntitlementId("cat", "1"));
    var request = activator.createJitRequest(
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
  // createMpaRequest.
  // -------------------------------------------------------------------------

  @Test
  public void  whenUserNotAllowedToRequest_ThenCreateMpaRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);

    Mockito.doThrow(new AccessDeniedException("mock"))
      .when(catalog)
      .verifyUserCanRequest(any());

    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    assertThrows(
      AccessDeniedException.class,
      () -> activator.createMpaRequest(
        SAMPLE_REQUESTING_USER,
        Set.of(new SampleEntitlementId("cat", "1")),
        Set.of(SAMPLE_APPROVING_USER),
        "justification",
        Instant.now(),
        Duration.ofMinutes(5)));
  }

  // -------------------------------------------------------------------------
  // activate (JIT).
  // -------------------------------------------------------------------------

  @Test
  public void whenJustificationInvalid_ThenActivateJitRequestThrowsException() throws Exception {
    var justificationPolicy = Mockito.mock(JustificationPolicy.class);

    Mockito.doThrow(new InvalidJustificationException("mock"))
      .when(justificationPolicy)
      .checkJustification(eq(SAMPLE_REQUESTING_USER), anyString());

    var activator = new SampleActivator(
      Mockito.mock(EntitlementCatalog.class),
      justificationPolicy);

    var request = activator.createJitRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new SampleEntitlementId("cat", "1")),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertThrows(
      InvalidJustificationException.class,
      () -> activator.activate(request));
  }

  @Test
  public void whenUserNotAllowedToRequest_ThenActivateJitRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);

    Mockito.doThrow(new AccessDeniedException("mock"))
      .when(catalog)
      .verifyUserCanRequest(any());

    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createJitRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new SampleEntitlementId("cat", "1")),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertThrows(
      AccessDeniedException.class,
      () -> activator.activate(request));
  }

  // -------------------------------------------------------------------------
  // approve (MPA).
  // -------------------------------------------------------------------------

  @Test
  public void whenApprovingUserSameAsRequestingUser_ThenApproveMpaRequestThrowsException() throws Exception {
    var activator = new SampleActivator(
      Mockito.mock(EntitlementCatalog.class),
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createMpaRequest(
      SAMPLE_REQUESTING_USER,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertThrows(
      IllegalArgumentException.class,
      () -> activator.approve(SAMPLE_REQUESTING_USER, request));
  }

  @Test
  public void whenApprovingUserUnknown_ThenApproveMpaRequestThrowsException() throws Exception {
    var activator = new SampleActivator(
      Mockito.mock(EntitlementCatalog.class),
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createMpaRequest(
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
  public void whenJustificationInvalid_ThenApproveMpaRequestThrowsException() throws Exception {
    var justificationPolicy = Mockito.mock(JustificationPolicy.class);

    Mockito.doThrow(new InvalidJustificationException("mock"))
      .when(justificationPolicy)
      .checkJustification(eq(SAMPLE_REQUESTING_USER), anyString());

    var activator = new SampleActivator(
      Mockito.mock(EntitlementCatalog.class),
      justificationPolicy);

    var request = activator.createMpaRequest(
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
  public void whenRequestingUserNotAllowedToRequestAnymore_ThenApproveMpaRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);
    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createMpaRequest(
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
  public void whenApprovingUserNotAllowedToApprove_ThenApproveMpaRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(EntitlementCatalog.class);
    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createMpaRequest(
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
