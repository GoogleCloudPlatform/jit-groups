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

package com.google.solutions.jitaccess.web.proposal;

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.apis.clients.IamCredentialsClient;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;

/**
 * Signs JWTs using a service account's Google-managed service account key.
 */
@Singleton
public class ServiceAccountSigner implements TokenSigner {
  private final IamCredentialsClient iamCredentialsClient;
  private final Options options;
  private final TokenVerifier tokenVerifier;

  public ServiceAccountSigner(
    @NotNull IamCredentialsClient iamCredentialsClient,
    @NotNull Options options
  ) {
    this.options = options;
    this.iamCredentialsClient = iamCredentialsClient;

    //
    // Create verifier to check signature and obligatory claims.
    //
    this.tokenVerifier = TokenVerifier
      .newBuilder()
      .setCertificatesLocation(IamCredentialsClient.getJwksUrl(options.serviceAccount))
      .setIssuer(options.serviceAccount.email)
      .setAudience(options.serviceAccount.email)
      .build();
  }

  @Override
  public @NotNull TokenWithExpiry sign(
    @NotNull JsonWebToken.Payload payload,
    @NotNull Instant expiry
  ) throws AccessException, IOException {

    Preconditions.checkNotNull(payload, "payload");
    Preconditions.checkNotNull(expiry, "expiry");

    //
    // Add obligatory claims.
    //
    var issueTime = Instant.now();
    var jwtPayload =  payload
      .setAudience(this.options.serviceAccount.email)
      .setIssuer(this.options.serviceAccount.email)
      .setIssuedAtTimeSeconds(issueTime.getEpochSecond())
      .setExpirationTimeSeconds(expiry.getEpochSecond());

    return new TokenWithExpiry(
      this.iamCredentialsClient.signJwt(this.options.serviceAccount, jwtPayload),
      issueTime,
      expiry);
  }

  @Override
  public JsonWebToken.Payload verify(
    @NotNull String token
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

    return decodedToken.getPayload();
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------


  public record Options(
    @NotNull UserId serviceAccount
  ) {
    public Options {
      Preconditions.checkNotNull(serviceAccount);
    }
  }
}