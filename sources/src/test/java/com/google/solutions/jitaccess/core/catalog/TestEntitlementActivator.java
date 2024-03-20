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
import com.google.solutions.jitaccess.core.auth.UserId;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestEntitlementActivator {
  private static final UserId SAMPLE_REQUESTING_USER = new UserId("user@example.com");
  private static final UserId SAMPLE_APPROVING_USER = new UserId("peer@example.com");
  private static final UserId SAMPLE_UNKNOWN_USER = new UserId("unknown@example.com");

  private record UserContext(
    @NotNull UserId user
  ) implements CatalogUserContext {}

  private static class SampleActivator extends EntitlementActivator<SampleEntitlementId, ResourceId, UserContext> {
    @Override
    public int maximumNumberOfEntitlementsPerJitRequest() {
      return 10;
    }

    protected SampleActivator(
      Catalog<SampleEntitlementId, ResourceId, UserContext> catalog,
      JustificationPolicy policy
    ) {
      super(catalog, policy);
    }

    @Override
    protected Activation provisionAccess(
      JitActivationRequest<SampleEntitlementId> request
    ) throws AccessException, AlreadyExistsException, IOException {
      return new Activation(request.startTime(), request.duration());
    }

    @Override
    protected Activation provisionAccess(
      UserId approvingUser,
      MpaActivationRequest<SampleEntitlementId> request
    ) throws AccessException, AlreadyExistsException, IOException {
      return new Activation(request.startTime(), request.duration());
    }

    @Override
    public @NotNull JsonWebTokenConverter<MpaActivationRequest<SampleEntitlementId>> createTokenConverter() {
      return null;
    }
  }

  // -------------------------------------------------------------------------
  // createJitRequest.
  // -------------------------------------------------------------------------

  @Test
  public void createJitRequestDoesNotCheckAccess() throws Exception {
    var catalog = Mockito.mock(Catalog.class);

    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var entitlements = Set.of(new SampleEntitlementId("cat", "1"));

    var requestingUserContext = new UserContext(SAMPLE_REQUESTING_USER);
    var request = activator.createJitRequest(
      requestingUserContext,
      entitlements,
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertNotNull(request);
    assertEquals(SAMPLE_REQUESTING_USER, request.requestingUser());
    assertIterableEquals(entitlements, request.entitlements());

    verify(catalog, times(0)).verifyUserCanRequest(
      eq(requestingUserContext),
      eq(request));
  }

  @Test
  public void whenEntitlementsExceedsLimit_ThenCreateJitRequestThrowsException() throws Exception {
    var activator = new SampleActivator(
      Mockito.mock(Catalog.class),
      Mockito.mock(JustificationPolicy.class));

    var requestingUserContext = new UserContext(SAMPLE_REQUESTING_USER);
    var exception = assertThrows(
      IllegalArgumentException.class,
      () -> activator.createJitRequest(
        requestingUserContext,
        Stream
          .generate(() -> new SampleEntitlementId("roles/" + UUID.randomUUID()))
          .limit(activator.maximumNumberOfEntitlementsPerJitRequest() + 1)
          .collect(Collectors.toSet()),
        "justification",
        Instant.now(),
        Duration.ofMinutes(5)));
    assertTrue(exception.getMessage().contains("exceeds"));
  }

  // -------------------------------------------------------------------------
  // createMpaRequest.
  // -------------------------------------------------------------------------

  @Test
  public void  whenUserNotAllowedToRequest_ThenCreateMpaRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(Catalog.class);
    var requestingUserContext = new UserContext(SAMPLE_REQUESTING_USER);

    Mockito.doThrow(new AccessDeniedException("mock"))
      .when(catalog)
      .verifyUserCanRequest(eq(requestingUserContext), any());

    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    assertThrows(
      AccessDeniedException.class,
      () -> activator.createMpaRequest(
        requestingUserContext,
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
      Mockito.mock(Catalog.class),
      justificationPolicy);

    var requestingUserContext = new UserContext(SAMPLE_REQUESTING_USER);
    var request = activator.createJitRequest(
      requestingUserContext,
      Set.of(new SampleEntitlementId("cat", "1")),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertThrows(
      InvalidJustificationException.class,
      () -> activator.activate(requestingUserContext, request));
  }

  @Test
  public void whenUserNotAllowedToRequest_ThenActivateJitRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(Catalog.class);
    var requestingUserContext = new UserContext(SAMPLE_REQUESTING_USER);

    Mockito.doThrow(new AccessDeniedException("mock"))
      .when(catalog)
      .verifyUserCanRequest(eq(requestingUserContext), any());

    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var request = activator.createJitRequest(
      requestingUserContext,
      Set.of(new SampleEntitlementId("cat", "1")),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertThrows(
      AccessDeniedException.class,
      () -> activator.activate(requestingUserContext, request));
  }

  // -------------------------------------------------------------------------
  // approve (MPA).
  // -------------------------------------------------------------------------

  @Test
  public void whenApprovingUserSameAsRequestingUser_ThenApproveMpaRequestThrowsException() throws Exception {
    var activator = new SampleActivator(
      Mockito.mock(Catalog.class),
      Mockito.mock(JustificationPolicy.class));

    var requestingUserContext = new UserContext(SAMPLE_REQUESTING_USER);
    var request = activator.createMpaRequest(
      requestingUserContext,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    assertThrows(
      IllegalArgumentException.class,
      () -> activator.approve(requestingUserContext, request));
  }

  @Test
  public void whenApprovingUserUnknown_ThenApproveMpaRequestThrowsException() throws Exception {
    var activator = new SampleActivator(
      Mockito.mock(Catalog.class),
      Mockito.mock(JustificationPolicy.class));

    var requestingUserContext = new UserContext(SAMPLE_REQUESTING_USER);
    var request = activator.createMpaRequest(
      requestingUserContext,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    var approvingUserContext = new UserContext(SAMPLE_UNKNOWN_USER);
    assertThrows(
      AccessDeniedException.class,
      () -> activator.approve(approvingUserContext, request));
  }

  @Test
  public void whenJustificationInvalid_ThenApproveMpaRequestThrowsException() throws Exception {
    var justificationPolicy = Mockito.mock(JustificationPolicy.class);

    Mockito.doThrow(new InvalidJustificationException("mock"))
      .when(justificationPolicy)
      .checkJustification(eq(SAMPLE_REQUESTING_USER), anyString());

    var activator = new SampleActivator(
      Mockito.mock(Catalog.class),
      justificationPolicy);

    var requestingUserContext = new UserContext(SAMPLE_REQUESTING_USER);
    var request = activator.createMpaRequest(
      requestingUserContext,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    var approvingUserContext = new UserContext(SAMPLE_APPROVING_USER);
    assertThrows(
      InvalidJustificationException.class,
      () -> activator.approve(approvingUserContext, request));
  }

  @Test
  public void whenRequestingUserNotAllowedToRequestAnymore_ThenApproveMpaRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(Catalog.class);
    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var requestingUserContext = new UserContext(SAMPLE_REQUESTING_USER);
    var request = activator.createMpaRequest(
      requestingUserContext,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    var approvingUserContext = new UserContext(SAMPLE_APPROVING_USER);
    Mockito.doThrow(new AccessDeniedException("mock"))
      .when(catalog)
      .verifyUserCanRequest(any(), any());

    assertThrows(
      AccessDeniedException.class,
      () -> activator.approve(approvingUserContext, request));
  }

  @Test
  public void whenApprovingUserNotAllowedToApprove_ThenApproveMpaRequestThrowsException() throws Exception {
    var catalog = Mockito.mock(Catalog.class);
    var activator = new SampleActivator(
      catalog,
      Mockito.mock(JustificationPolicy.class));

    var requestingUserContext = new UserContext(SAMPLE_REQUESTING_USER);
    var request = activator.createMpaRequest(
      requestingUserContext,
      Set.of(new SampleEntitlementId("cat", "1")),
      Set.of(SAMPLE_APPROVING_USER),
      "justification",
      Instant.now(),
      Duration.ofMinutes(5));

    var approvingUserContext = new UserContext(SAMPLE_APPROVING_USER);
    Mockito.doThrow(new AccessDeniedException("mock"))
      .when(catalog)
      .verifyUserCanApprove(eq(approvingUserContext), any());

    assertThrows(
      AccessDeniedException.class,
      () -> activator.approve(approvingUserContext, request));
  }
}
