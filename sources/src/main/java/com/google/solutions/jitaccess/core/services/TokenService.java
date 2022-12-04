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

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.adapters.IamCredentialsAdapter;
import com.google.solutions.jitaccess.core.data.UserId;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class TokenService {
  private final IamCredentialsAdapter iamCredentialsAdapter;
  private final Options options;
  private final TokenVerifier tokenVerifier;

  public TokenService(
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

  public String createToken(JsonWebToken.Payload payload) throws AccessException, IOException {
    Preconditions.checkNotNull(payload, "payload");

    //
    // Add obligatory claims.
    //
    payload = payload
      .clone()
      .setAudience(this.options.serviceAccount.email)
      .setIssuer(this.options.serviceAccount.email)
      .setExpirationTimeSeconds(Instant.now().plus(this.options.tokenValidity).getEpochSecond());

    return this.iamCredentialsAdapter.signJwt(
      this.options.serviceAccount,
      payload);
  }

  public JsonWebToken.Payload verifyToken(
    String token,
    UserId expectedSubject
  ) throws TokenVerifier.VerificationException {
    Preconditions.checkNotNull(token, "token");
    Preconditions.checkNotNull(expectedSubject, "expectedSubject");

    var decodedToken = this.tokenVerifier.verify(token);
    if (!decodedToken.getHeader().getAlgorithm().equals("RS256")) {
      //
      // Service account keys are RS256, anything else is fishy.
      //
      throw new TokenVerifier.VerificationException("The token uses the wrong algorithm");
    }

    if (!expectedSubject.email.equals(decodedToken.getPayload().getSubject())) {
      throw new TokenVerifier.VerificationException("The token was issued to a different subject");
    }

    return decodedToken.getPayload();
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public static class Options {
    public final UserId serviceAccount;
    public final Duration tokenValidity;

    public Options(UserId serviceAccount, Duration tokenValidity) {
      this.serviceAccount = serviceAccount;
      this.tokenValidity = tokenValidity;
    }
  }
}
