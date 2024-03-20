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

import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.clients.IamCredentialsClient;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Signs JWTs using a service account's Google-managed service account key.
 */
@Singleton
public class TokenSigner {
  private final IamCredentialsClient iamCredentialsClient;
  private final Options options;
  private final TokenVerifier tokenVerifier;

  public TokenSigner(
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

  /**
   * Create a signed JWT for a given payload.
   */
  public <T> @NotNull TokenWithExpiry sign(
    @NotNull JsonWebTokenConverter<T> converter,
    @NotNull T payload
  ) throws AccessException, IOException {

    Preconditions.checkNotNull(converter, "converter");
    Preconditions.checkNotNull(payload, "payload");

    //
    // Add obligatory claims.
    //
    var issueTime = Instant.now();
    var expiryTime = issueTime.plus(this.options.tokenValidity);
    var jwtPayload =  converter.convert(payload)
      .setAudience(this.options.serviceAccount.email)
      .setIssuer(this.options.serviceAccount.email)
      .setIssuedAtTimeSeconds(issueTime.getEpochSecond())
      .setExpirationTimeSeconds(expiryTime.getEpochSecond());

    return new TokenWithExpiry(
      this.iamCredentialsClient.signJwt(this.options.serviceAccount, jwtPayload),
      issueTime,
      expiryTime);
  }

  /**
   * Decode and verify a JWT.
   */
  public <T> T verify(
    @NotNull JsonWebTokenConverter<T> converter,
    @NotNull String token
  ) throws TokenVerifier.VerificationException {

    Preconditions.checkNotNull(converter, "converter");
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

    return converter.convert(decodedToken.getPayload());
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public record TokenWithExpiry(
    @NotNull String token,
    @NotNull Instant issueTime,
    @NotNull Instant expiryTime
  ) {
    public TokenWithExpiry {
      Preconditions.checkNotNull(token, "token");
      Preconditions.checkArgument(expiryTime.isAfter(issueTime));
      Preconditions.checkArgument(expiryTime.isAfter(Instant.now()));
    }
  }

  public record Options(
    @NotNull UserId serviceAccount,
    @NotNull Duration tokenValidity
  ) {
    public Options {
      Preconditions.checkNotNull(serviceAccount);
      Preconditions.checkArgument(!tokenValidity.isNegative());
    }
  }
}
