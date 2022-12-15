//
// Copyright 2022 Google LLC
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

package com.google.solutions.jitaccess.core.services;

import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.adapters.IamCredentialsAdapter;
import com.google.solutions.jitaccess.core.data.UserId;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Creates and verifies activation tokens.
 *
 * An activation token is a signed activation request that is passed to reviewers.
 * It contains all information necessary to review (and approve) the activation
 * request.
 *
 * We must ensure that the information that reviewers see (and base their approval
 * on) is authentic. Therefore, activation tokens are signed, using the service account
 * as signing authority.
 *
 * Although activation tokens are JWTs, and might look like credentials, they aren't
 * credentials: They don't grant access to any information, and are insufficient to
 * approve an activation request.
 */
@ApplicationScoped
public class ActivationTokenService {
  private final IamCredentialsAdapter iamCredentialsAdapter;
  private final Options options;
  private final TokenVerifier tokenVerifier;

  public ActivationTokenService(
    IamCredentialsAdapter iamCredentialsAdapter,
    Options options
  ) {
    Preconditions.checkNotNull(iamCredentialsAdapter, "iamCredentialsAdapter");
    Preconditions.checkNotNull(options, "options");

    this.options = options;
    this.iamCredentialsAdapter = iamCredentialsAdapter;

    //
    // Create verifier to check signature and obligatory claims.
    //
    this.tokenVerifier = TokenVerifier
      .newBuilder()
      .setCertificatesLocation(IamCredentialsAdapter.getJwksUrl(options.serviceAccount))
      .setIssuer(options.serviceAccount.email)
      .setAudience(options.serviceAccount.email)
      .build();
  }

  public TokenWithExpiry createToken(RoleActivationService.ActivationRequest request) throws AccessException, IOException {
    Preconditions.checkNotNull(request, "request");
    Preconditions.checkArgument(request.startTime.isBefore(Instant.now().plusSeconds(10)));
    Preconditions.checkArgument(request.startTime.isAfter(Instant.now().minusSeconds(10)));

    //
    // Add obligatory claims.
    //
    var expiryTime = request.startTime.plus(this.options.tokenValidity);
    var jwtPayload = request.toJsonWebTokenPayload()
      .setAudience(this.options.serviceAccount.email)
      .setIssuer(this.options.serviceAccount.email)
      .setExpirationTimeSeconds(expiryTime.getEpochSecond());

    return new TokenWithExpiry(
      this.iamCredentialsAdapter.signJwt(this.options.serviceAccount, jwtPayload),
      expiryTime);
  }

  public RoleActivationService.ActivationRequest verifyToken(
    String token
  ) throws TokenVerifier.VerificationException {
    Preconditions.checkNotNull(token, "token");

    //
    // Verify the token against the service account's JWKs. If that succeeds, we know
    // that the token has been issued by us.
    //
    var decodedToken = this.tokenVerifier.verify(token);
    if (!decodedToken.getHeader().getAlgorithm().equals("RS256")) {
      //
      // Service account keys are RS256, anything else is fishy.
      //
      throw new TokenVerifier.VerificationException("The token uses the wrong algorithm");
    }

    return RoleActivationService.ActivationRequest.fromJsonWebTokenPayload(decodedToken.getPayload());
  }

  public Options getOptions() {
    return options;
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public static class TokenWithExpiry {
    public final String token;
    public final Instant expiryTime;

    public TokenWithExpiry(String token, Instant expiryTime) {
      this.token = token;
      this.expiryTime = expiryTime;
    }
  }

  public static class Options {
    public final UserId serviceAccount;
    public final Duration tokenValidity;

    public Options(UserId serviceAccount, Duration tokenValidity) {
      this.serviceAccount = serviceAccount;
      this.tokenValidity = tokenValidity;
    }
  }
}
