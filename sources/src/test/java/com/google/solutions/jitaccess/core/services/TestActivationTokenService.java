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
import com.google.solutions.jitaccess.core.data.ProjectId;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestActivationTokenService {
  private static final UserId SAMPLE_USER_1 = new UserId("user-1@example.com");
  private static final UserId SAMPLE_USER_2 = new UserId("user-2@example.com");
  private static final UserId SAMPLE_USER_3 = new UserId("user-3@example.com");

  // -------------------------------------------------------------------------
  // createToken.
  // -------------------------------------------------------------------------

  @Test
  public void createTokenAddsObligatoryClaims() throws Exception {
    var credentialsAdapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;
    var tokenService = new ActivationTokenService(
      credentialsAdapter,
      new ActivationTokenService.Options(
        serviceAccount,
        Duration.ofMinutes(5)));

    var startTime = Instant.now();
    var request = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      SAMPLE_USER_1,
      Set.of(SAMPLE_USER_2, SAMPLE_USER_3),
      new RoleBinding(new ProjectId("project-1"), "roles/role-1"),
      "justification",
      startTime,
      startTime.plusSeconds(60));

    var token = tokenService.createToken(request);
    assertNotNull(token.token);
    assertEquals(startTime.plus(tokenService.getOptions().tokenValidity), token.expiryTime);

    var verifiedPayload = TokenVerifier
      .newBuilder()
      .setCertificatesLocation(IamCredentialsAdapter.getJwksUrl(serviceAccount))
      .setIssuer(serviceAccount.email)
      .setAudience(serviceAccount.email)
      .build()
      .verify(token.token)
      .getPayload();

    assertEquals(serviceAccount.email, verifiedPayload.getIssuer());
    assertEquals(serviceAccount.email, verifiedPayload.getAudience());
    assertNull(verifiedPayload.getIssuedAtTimeSeconds());
    assertNotNull(verifiedPayload.getExpirationTimeSeconds());
    assertEquals(
      startTime.plus(Duration.ofMinutes(5)).getEpochSecond(),
      verifiedPayload.getExpirationTimeSeconds());
  }

  // -------------------------------------------------------------------------
  // verifyToken.
  // -------------------------------------------------------------------------

  @Test
  public void whenJwtMissesAudienceClaim_ThenVerifyTokenThrowsException() throws Exception {
    var credentialsAdapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;
    var tokenService = new ActivationTokenService(
      credentialsAdapter,
      new ActivationTokenService.Options(
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
    var tokenService = new ActivationTokenService(
      credentialsAdapter,
      new ActivationTokenService.Options(
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
    var tokenService = new ActivationTokenService(
      credentialsAdapter,
      new ActivationTokenService.Options(
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
    var tokenService = new ActivationTokenService(
      credentialsAdapter,
      new ActivationTokenService.Options(
        serviceAccount,
        Duration.ofMinutes(5)));

    var inputRequest = RoleActivationService.ActivationRequest.createForTestingOnly(
      RoleActivationService.ActivationId.newId(RoleActivationService.ActivationType.MPA),
      SAMPLE_USER_1,
      Set.of(SAMPLE_USER_2, SAMPLE_USER_3),
      new RoleBinding(new ProjectId("project-1"), "roles/role-1"),
      "justification",
      Instant.now(),
      Instant.now().plusSeconds(60));

    var token = tokenService.createToken(inputRequest);
    var outputRequest = tokenService.verifyToken(token.token);

    assertEquals(inputRequest.beneficiary, outputRequest.beneficiary);
    assertEquals(inputRequest.reviewers, outputRequest.reviewers);
    assertEquals(inputRequest.justification, outputRequest.justification);
    assertEquals(inputRequest.reviewers, outputRequest.reviewers);
    assertEquals(inputRequest.startTime.getEpochSecond(), outputRequest.startTime.getEpochSecond());
  }
}
