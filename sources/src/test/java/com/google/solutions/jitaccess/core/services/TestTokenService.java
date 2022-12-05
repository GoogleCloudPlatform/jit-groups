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
import com.google.solutions.jitaccess.core.adapters.IamCredentialsAdapter;
import com.google.solutions.jitaccess.core.adapters.IntegrationTestEnvironment;
import com.google.solutions.jitaccess.core.data.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class TestTokenService {
  // -------------------------------------------------------------------------
  // createToken.
  // -------------------------------------------------------------------------

  @Test
  public void whenPayloadEmpty_ThenCreateTokenAddsObligatoryClaims() throws Exception {
    var credentialsAdapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;
    var tokenService = new TokenService(
      credentialsAdapter,
      new TokenService.Options(
        serviceAccount,
        Duration.ofMinutes(5)));

    var payload = new JsonWebToken.Payload();

    var token = tokenService.createToken(payload);
    var verifiedPayload = tokenService.verifyToken(token);

    assertEquals(serviceAccount.email, verifiedPayload.getIssuer());
    assertEquals(serviceAccount.email, verifiedPayload.getAudience());
    assertNotNull(verifiedPayload.getExpirationTimeSeconds());
  }

  // -------------------------------------------------------------------------
  // verifyToken.
  // -------------------------------------------------------------------------

  @Test
  public void whenJwtMissesAudienceClaim_ThenVerifyTokenThrowsException() throws Exception {
    var credentialsAdapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;
    var tokenService = new TokenService(
      credentialsAdapter,
      new TokenService.Options(
        serviceAccount,
        Duration.ofMinutes(5)));

    var payload = new JsonWebToken.Payload()
      .setIssuer(serviceAccount.email);

    var jwt = credentialsAdapter.signJwt(serviceAccount, payload);

    assertThrows(TokenVerifier.VerificationException.class,
      () -> tokenService.verifyToken(jwt));
  }

  @Test
  public void whenJwtMissesIssuerClaim_ThenVerifyThrowsException() throws Exception {
    var credentialsAdapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;
    var tokenService = new TokenService(
      credentialsAdapter,
      new TokenService.Options(
        serviceAccount,
        Duration.ofMinutes(5)));

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.email);

    var jwt = credentialsAdapter.signJwt(serviceAccount, payload);

    assertThrows(TokenVerifier.VerificationException.class,
      () -> tokenService.verifyToken(jwt));
  }

  @Test
  public void whenJwtSignedByWrongServiceAccount_ThenVerifyThrowsException() throws Exception {
    var credentialsAdapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.TEMPORARY_ACCESS_USER;
    var tokenService = new TokenService(
      credentialsAdapter,
      new TokenService.Options(
        serviceAccount,
        Duration.ofMinutes(5)));

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.email)
      .setIssuer(serviceAccount.email);

    var jwt = credentialsAdapter.signJwt(IntegrationTestEnvironment.NO_ACCESS_USER, payload);

    assertThrows(TokenVerifier.VerificationException.class,
      () -> tokenService.verifyToken(jwt));
  }

  @Test
  public void whenJwtValid_ThenVerifySucceeds() throws Exception {
    var credentialsAdapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;
    var tokenService = new TokenService(
      credentialsAdapter,
      new TokenService.Options(
        serviceAccount,
        Duration.ofMinutes(5)));

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.email)
      .setIssuer(serviceAccount.email);

    var jwt = credentialsAdapter.signJwt(serviceAccount, payload);

    tokenService.verifyToken(jwt);
  }
}
