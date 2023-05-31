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
import com.google.solutions.jitaccess.core.adapters.LogAdapter;
import com.google.solutions.jitaccess.core.data.DeviceInfo;
import com.google.solutions.jitaccess.core.data.UserId;
import com.google.solutions.jitaccess.core.data.UserPrincipal;

import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;
import java.security.Principal;

/**
 * Verifies that requests have a valid IAP assertion, and makes the assertion available as
 * SecurityContext.
 */
@Dependent
@Provider
@Priority(Priorities.AUTHENTICATION)
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
        this.runtimeEnvironment.getProjectNumber(), this.runtimeEnvironment.getBackendServiceId().toString()
      );
    }
  }
  /**
   * Authenticate request using IAP assertion.
   */
  private UserPrincipal authenticateIapRequest(ContainerRequestContext requestContext) {
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
      return new UserPrincipal() {
        @Override
        public String getName() {
          return getId().toString();
        }

        @Override
        public UserId getId() {
          return verifiedAssertion.getUserId();
        }

        @Override
        public DeviceInfo getDevice() {
          return verifiedAssertion.getDeviceInfo();
        }
      };
    }
    catch (TokenVerifier.VerificationException | IllegalArgumentException e) {
      this.log
        .newErrorEntry(EVENT_AUTHENTICATE, "Verifying IAP assertion failed", e)
        .write();

      throw new ForbiddenException("Invalid IAP assertion", e);
    }
  }

  /**
   * Pseudo-authenticate request using debug header. Only used in debug mode.
   */
  private UserPrincipal authenticateDebugRequest(ContainerRequestContext requestContext) {
    assert this.runtimeEnvironment.isDebugModeEnabled();

    var debugPrincipalName = requestContext.getHeaderString(DEBUG_PRINCIPAL_HEADER);
    if (debugPrincipalName == null || debugPrincipalName.isEmpty()) {
      throw new ForbiddenException(DEBUG_PRINCIPAL_HEADER + " not set");
    }

    return new UserPrincipal() {
      @Override
      public String getName() {
        return debugPrincipalName;
      }

      @Override
      public UserId getId() {
        return new UserId(debugPrincipalName);
      }

      @Override
      public DeviceInfo getDevice() {
        return DeviceInfo.UNKNOWN;
      }
    };
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    Preconditions.checkNotNull(this.log, "log");
    Preconditions.checkNotNull(this.runtimeEnvironment, "runtimeEnvironment");

    var principal = this.runtimeEnvironment.isDebugModeEnabled()
      ? authenticateDebugRequest(requestContext)
      : authenticateIapRequest(requestContext);

    this.log.setPrincipal(principal);

    requestContext.setSecurityContext(
      new SecurityContext() {
        @Override
        public Principal getUserPrincipal() {
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
        public String getAuthenticationScheme() {
          return "IAP";
        }
      });

    this.log.newInfoEntry(EVENT_AUTHENTICATE, "Authenticated IAP principal").write();
  }
}
