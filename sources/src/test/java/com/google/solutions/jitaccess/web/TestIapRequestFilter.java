//
// Copyright 2021 Google LLC
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

import com.google.solutions.jitaccess.core.adapters.LogAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.container.ContainerRequestContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestIapRequestFilter {
  @Test()
  public void whenHeaderMissing_ThenFilterThrowsForbiddenException() {
    RuntimeEnvironment environment = Mockito.mock(RuntimeEnvironment.class);
    when(environment.getProjectId()).thenReturn("123");
    when(environment.getProjectNumber()).thenReturn("123");
    when(environment.isRunningOnAppEngine()).thenReturn(true);
    when(environment.isDebugModeEnabled()).thenReturn(false);

    IapRequestFilter filter = new IapRequestFilter();
    filter.runtimeEnvironment = environment;
    filter.log = new LogAdapter();

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);
    when(request.getHeaderString(anyString())).thenReturn(null);

    assertThrows(ForbiddenException.class, () -> filter.filter(request));
  }

  @Test
  public void whenHeaderContainsMalformedJwt_ThenFilterThrowsForbiddenException() {
    RuntimeEnvironment environment = Mockito.mock(RuntimeEnvironment.class);
    when(environment.getProjectId()).thenReturn("123");
    when(environment.getProjectNumber()).thenReturn("123");
    when(environment.isRunningOnAppEngine()).thenReturn(true);
    when(environment.isDebugModeEnabled()).thenReturn(false);

    IapRequestFilter filter = new IapRequestFilter();
    filter.runtimeEnvironment = environment;
    filter.log = new LogAdapter();

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);
    when(request.getHeaderString(anyString())).thenReturn("ey00");

    assertThrows(ForbiddenException.class, () -> filter.filter(request));
  }

  @Test
  public void whenHeaderContainsInvalidJwt_ThenFilterThrowsForbiddenException() {
    RuntimeEnvironment environment = Mockito.mock(RuntimeEnvironment.class);
    when(environment.getProjectId()).thenReturn("123");
    when(environment.getProjectNumber()).thenReturn("123");
    when(environment.isRunningOnAppEngine()).thenReturn(true);
    when(environment.isDebugModeEnabled()).thenReturn(false);

    IapRequestFilter filter = new IapRequestFilter();
    filter.runtimeEnvironment = environment;
    filter.log = new LogAdapter();

    // Random JWT that doesn't even come from IAP.
    String randomJwt =
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMj"
        + "M0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.S"
        + "flKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);
    when(request.getHeaderString(anyString())).thenReturn(randomJwt);

    assertThrows(ForbiddenException.class, () -> filter.filter(request));
  }

  // -------------------------------------------------------------------------
  // Debug mode.
  // -------------------------------------------------------------------------

  @Test
  public void whenDebugModeEnabledAndDebugHeaderMissing_ThenFilterThrowsForbiddenException() {
    RuntimeEnvironment environment = Mockito.mock(RuntimeEnvironment.class);
    when(environment.isDebugModeEnabled()).thenReturn(true);

    IapRequestFilter filter = new IapRequestFilter();
    filter.runtimeEnvironment = environment;
    filter.log = new LogAdapter();

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);
    assertThrows(ForbiddenException.class, () -> filter.filter(request));
  }

  @Test
  public void whenDebugModeEnabled_ThenFilterUsesDebugHeader() {
    RuntimeEnvironment environment = Mockito.mock(RuntimeEnvironment.class);
    when(environment.isDebugModeEnabled()).thenReturn(true);

    IapRequestFilter filter = new IapRequestFilter();
    filter.runtimeEnvironment = environment;
    filter.log = new LogAdapter();

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);
    when(request.getHeaderString(eq("x-debug-principal"))).thenReturn("bob");

    filter.filter(request);

    verify(request, times(1))
      .setSecurityContext(argThat(a -> a.getUserPrincipal().getName().equals("bob")));
  }

  @Test
  public void whenRunsInCloudRun_returnsCorrectAudience() {
    RuntimeEnvironment environment = Mockito.mock(RuntimeEnvironment.class);
    when(environment.getProjectId()).thenReturn("123");
    when(environment.getProjectNumber()).thenReturn("123");
    when(environment.isDebugModeEnabled()).thenReturn(false);
    when(environment.isRunningOnCloudRun()).thenReturn(true);
    when(environment.getBackendServiceId()).thenReturn("12345");

    IapRequestFilter filter = new IapRequestFilter();
    filter.runtimeEnvironment = environment;

    assertEquals(filter.getExpectedAudience(), "/projects/123/global/backendServices/12345");
  }
}
