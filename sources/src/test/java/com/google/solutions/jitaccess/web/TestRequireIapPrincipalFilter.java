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

import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.catalog.auth.SubjectResolver;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

public class TestRequireIapPrincipalFilter {
  @Test()
  public void filter_whenHeaderMissing_thenThrowsForbiddenException() {
    RequireIapPrincipalFilter filter = new RequireIapPrincipalFilter();
    filter.options = new RequireIapPrincipalFilter.Options(false, "audience");
    filter.requestContext = new RequestContext(Mockito.mock(SubjectResolver.class));
    filter.logger = Mockito.mock(Logger.class);

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);
    when(request.getHeaderString(anyString())).thenReturn(null);

    assertThrows(ForbiddenException.class, () -> filter.filter(request));
  }

  @Test
  public void filter_whenHeaderContainsMalformedJwt_thenThrowsForbiddenException() {
    RequireIapPrincipalFilter filter = new RequireIapPrincipalFilter();
    filter.options = new RequireIapPrincipalFilter.Options(false, "audience");
    filter.requestContext = new RequestContext(Mockito.mock(SubjectResolver.class));
    filter.logger = Mockito.mock(Logger.class);

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);
    when(request.getHeaderString(anyString())).thenReturn("ey00");

    assertThrows(ForbiddenException.class, () -> filter.filter(request));
  }

  @Test
  public void filter_whenHeaderContainsInvalidJwt_thenThrowsForbiddenException() {
    RequireIapPrincipalFilter filter = new RequireIapPrincipalFilter();
    filter.options = new RequireIapPrincipalFilter.Options(false, "audience");
    filter.requestContext = new RequestContext(Mockito.mock(SubjectResolver.class));
    filter.logger = Mockito.mock(Logger.class);

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
  public void filter_whenDebugModeEnabledAndDebugHeaderMissing_thenThrowsForbiddenException() {
    RequireIapPrincipalFilter filter = new RequireIapPrincipalFilter();
    filter.options = new RequireIapPrincipalFilter.Options(true, null);
    filter.requestContext = new RequestContext(Mockito.mock(SubjectResolver.class));
    filter.logger = Mockito.mock(Logger.class);

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);
    assertThrows(ForbiddenException.class, () -> filter.filter(request));
  }

  @Test
  public void filter_whenDebugModeEnabled_thenUsesDebugHeader() {
    var filter = new RequireIapPrincipalFilter();
    filter.options = new RequireIapPrincipalFilter.Options(true, null);
    filter.requestContext = new RequestContext(Mockito.mock(SubjectResolver.class));
    filter.logger = Mockito.mock(Logger.class);

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);
    when(request.getHeaderString(eq("x-debug-principal"))).thenReturn("bob");

    filter.filter(request);

    assertEquals("bob", filter.requestContext.user().email);
  }
}
