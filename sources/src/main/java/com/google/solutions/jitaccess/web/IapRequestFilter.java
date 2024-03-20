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

import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.web.iap.DeviceInfo;
import com.google.solutions.jitaccess.web.iap.IapAssertion;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import org.jetbrains.annotations.NotNull;

import java.security.Principal;

/**
 * Verifies that requests have a valid IAP assertion, and makes the assertion available as
 * SecurityContext.
 */
@Dependent
@Provider
@Priority(Priorities.AUTHENTICATION)
@RequireIapPrincipal
public class IapRequestFilter implements ContainerRequestFilter {
  private static final String EVENT_AUTHENTICATE = "iap.authenticate";

  private static final String IAP_ISSUER_URL = "https://cloud.google.com/iap";
  private static final String IAP_ASSERTION_HEADER = "x-goog-iap-jwt-assertion";
  private static final String DEBUG_PRINCIPAL_HEADER = "x-debug-principal";

  @Inject
  LogAdapter log;

  @Inject
  RuntimeEnvironment runtimeEnvironment;
  //
  // For AppEngine, we can derive the expected audience
  // from the project number and name.
  // For running it inside Cloud Run, we need to use provided backend service id
  // through the env variable
  //
  public String getExpectedAudience() {
    if (runtimeEnvironment.isRunningOnAppEngine()) {
      return String.format(
        "/projects/%s/apps/%s",
        this.runtimeEnvironment.getProjectNumber(), this.runtimeEnvironment.getProjectId());
    } else {
      return String.format(
        "/projects/%s/global/backendServices/%s",
        this.runtimeEnvironment.getProjectNumber(), this.runtimeEnvironment.getBackendServiceId()
      );
    }
  }
  /**
   * Authenticate request using IAP assertion.
   */
  private @NotNull IapPrincipal authenticateIapRequest(@NotNull ContainerRequestContext requestContext) {
    //
    // Read IAP assertion header and validate it.
    //
    
    String expectedAudience = getExpectedAudience();

    String assertion = requestContext.getHeaderString(IAP_ASSERTION_HEADER);
    if (assertion == null) {
      this.log
        .newErrorEntry(EVENT_AUTHENTICATE, "Missing IAP assertion in header, IAP might be disabled")
        .write();

      throw new ForbiddenException("Identity-Aware Proxy must be enabled for this application");
    }

    try {
      final var verifiedAssertion = new IapAssertion(
        TokenVerifier.newBuilder()
          .setAudience(expectedAudience)
          .setIssuer(IAP_ISSUER_URL)
          .build()
          .verify(assertion));

      //
      // Associate the token with the request so that controllers
      // can access it.
      //
      return verifiedAssertion;
    }
    catch (TokenVerifier.VerificationException | IllegalArgumentException e) {
      this.log
        .newErrorEntry(
          EVENT_AUTHENTICATE,
          String.format(
            "Verifying IAP assertion failed. This might be because the " +
            "IAP assertion was tampered with, or because it had the wrong audience " +
            "(expected audience: %s).", expectedAudience),
          e)
        .write();

      throw new ForbiddenException("Invalid IAP assertion", e);
    }
  }

  /**
   * Pseudo-authenticate request using debug header. Only used in debug mode.
   */
  private @NotNull IapPrincipal authenticateDebugRequest(@NotNull ContainerRequestContext requestContext) {
    assert this.runtimeEnvironment.isDebugModeEnabled();

    var debugPrincipalName = requestContext.getHeaderString(DEBUG_PRINCIPAL_HEADER);
    if (debugPrincipalName == null || debugPrincipalName.isEmpty()) {
      throw new ForbiddenException(DEBUG_PRINCIPAL_HEADER + " not set");
    }

    return new IapPrincipal() {
      @Override
      public @NotNull String getName() {
        return debugPrincipalName;
      }

      @Override
      public @NotNull UserId email() {
        return new UserId(debugPrincipalName);
      }

      @Override
      public String subjectId() {
        return "debug";
      }

      @Override
      public @NotNull DeviceInfo device() {
        return DeviceInfo.UNKNOWN;
      }
    };
  }

  @Override
  public void filter(@NotNull ContainerRequestContext requestContext) {
    Preconditions.checkNotNull(this.log, "log");
    Preconditions.checkNotNull(this.runtimeEnvironment, "runtimeEnvironment");

    var principal = this.runtimeEnvironment.isDebugModeEnabled()
      ? authenticateDebugRequest(requestContext)
      : authenticateIapRequest(requestContext);

    this.log.setPrincipal(principal);

    requestContext.setSecurityContext(
      new SecurityContext() {
        @Override
        public @NotNull Principal getUserPrincipal() {
          return principal;
        }

        @Override
        public boolean isUserInRole(String s) {
          return false;
        }

        @Override
        public boolean isSecure() {
          return true;
        }

        @Override
        public @NotNull String getAuthenticationScheme() {
          return "IAP";
        }
      });

    this.log.newInfoEntry(EVENT_AUTHENTICATE, "Authenticated IAP principal").write();
  }
}
