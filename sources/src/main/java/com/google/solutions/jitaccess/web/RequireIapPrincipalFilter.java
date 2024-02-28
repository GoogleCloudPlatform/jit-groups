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
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Verifies that requests have a valid IAP assertion, and makes the assertion available as
 * SecurityContext.
 */
@Dependent
@Provider
@Priority(Priorities.AUTHENTICATION)
@RequireIapPrincipal
public class RequireIapPrincipalFilter implements ContainerRequestFilter {

  private static final String IAP_ISSUER_URL = "https://cloud.google.com/iap";
  private static final String IAP_ASSERTION_HEADER = "x-goog-iap-jwt-assertion";
  private static final String DEBUG_PRINCIPAL_HEADER = "x-debug-principal";

  @Inject
  Logger logger;

  @Inject
  Options options;

  @Inject
  RequestContext requestContext;

  /**
   * Authenticate request using IAP assertion.
   */
  private void authenticateIapRequest(@NotNull ContainerRequestContext requestContext) {
    //
    // Read IAP assertion header and validate it.
    //
    var assertion = requestContext.getHeaderString(IAP_ASSERTION_HEADER);
    if (assertion == null) {
      this.logger.warn(
        EventIds.API_AUTHENTICATE,
        "Missing IAP assertion in header, IAP might be disabled");

      throw new ForbiddenException("Identity-Aware Proxy must be enabled for this application");
    }

    try {
      final var verifiedAssertion = new IapAssertion(
        TokenVerifier.newBuilder()
          .setAudience(this.options.expectedAudience)
          .setIssuer(IAP_ISSUER_URL)
          .build()
          .verify(assertion));

      this.requestContext.authenticate(
        verifiedAssertion.email(),
        verifiedAssertion.device());
    }
    catch (TokenVerifier.VerificationException | IllegalArgumentException e) {
      if (this.options.expectedAudience != null) {
        this.logger.error(
          EventIds.API_AUTHENTICATE,
          String.format(
              "Verifying IAP assertion failed. This might be because the " +
                "IAP assertion was tampered with, or because it had the wrong audience " +
                "(expected audience: %s).", this.options.expectedAudience),
          e);
      }
      else {
        this.logger.error(
          EventIds.API_AUTHENTICATE,
          "Verifying IAP assertion failed. This might be because the " +
            "IAP assertion was tampered with",
          e);
      }

      throw new ForbiddenException("Invalid IAP assertion", e);
    }
  }

  /**
   * Pseudo-authenticate request using debug header. Only used in debug mode.
   */
  private void authenticateDebugRequest(@NotNull ContainerRequestContext context) {
    assert this.options.enableDebugAuthentication;

    var debugPrincipalName = context.getHeaderString(DEBUG_PRINCIPAL_HEADER);
    if (debugPrincipalName == null || debugPrincipalName.isEmpty()) {
      throw new ForbiddenException(DEBUG_PRINCIPAL_HEADER + " not set");
    }

    this.requestContext.authenticate(
      new UserId(debugPrincipalName),
      IapDevice.UNKNOWN);
  }

  @Override
  public void filter(@NotNull ContainerRequestContext requestContext) {
    Preconditions.checkNotNull(this.logger, "log");
    Preconditions.checkNotNull(this.options, "options");

    if (this.options.enableDebugAuthentication) {
      authenticateDebugRequest(requestContext);
    }
    else {
      authenticateIapRequest(requestContext);
    }

    this.logger.info(
      EventIds.API_AUTHENTICATE,
      "Authenticated IAP principal");
  }

  public record Options(
    /**
     * Enable pseudo-authentication for debugging.
     */
    boolean enableDebugAuthentication,
    
    /**
     * Expected audience in IAP assertions. If null, the audience
     * check is skipped.
     */
    @Nullable String expectedAudience
  ) {
    public Options {
      if (enableDebugAuthentication) {
        Preconditions.checkArgument(expectedAudience == null);
      }
    }
  }
}