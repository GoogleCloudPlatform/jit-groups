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
import com.google.solutions.jitaccess.apis.clients.HttpTransport;
import com.google.solutions.jitaccess.apis.clients.ITestEnvironment;
import com.google.solutions.jitaccess.apis.clients.IamCredentialsClient;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class ITestServiceAccountSigner {
  private static final UserId SAMPLE_USER_1 = new UserId("user-1@example.com");
  private static final UserId SAMPLE_USER_2 = new UserId("user-2@example.com");
  private static final UserId SAMPLE_USER_3 = new UserId("user-3@example.com");


  // -------------------------------------------------------------------------
  // sign.
  // -------------------------------------------------------------------------

  @Test
  public void sign_whenPayloadEmpty_thenTokenContainsObligatoryClaims() throws Exception {
    var credentialsAdapter = new IamCredentialsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);
    var serviceAccount = ITestEnvironment.NO_ACCESS_USER;

    var tokenSignerOptions = new ServiceAccountSigner.Options(serviceAccount);
    var tokenSigner = new ServiceAccountSigner(
      credentialsAdapter,
      tokenSignerOptions);

    var emptyPayload = new JsonWebToken.Payload();

    var expiry = Instant.now().plusSeconds(5);
    var token = tokenSigner.sign(emptyPayload, expiry);

    assertNotNull(token.token());
    assertTrue(token.issueTime().isBefore(expiry));
    assertEquals(expiry, token.expiryTime());

    var verifiedPayload = TokenVerifier
      .newBuilder()
      .setCertificatesLocation(IamCredentialsClient.getJwksUrl(serviceAccount))
      .setIssuer(serviceAccount.value())
      .setAudience(serviceAccount.value())
      .build()
      .verify(token.token())
      .getPayload();

    assertEquals(serviceAccount.value(), verifiedPayload.getIssuer());
    assertEquals(serviceAccount.value(), verifiedPayload.getAudience());
    assertEquals(token.issueTime().getEpochSecond(), verifiedPayload.getIssuedAtTimeSeconds());
    assertEquals(token.expiryTime().getEpochSecond(), verifiedPayload.getExpirationTimeSeconds());
  }

  // -------------------------------------------------------------------------
  // verify.
  // -------------------------------------------------------------------------

  @Test
  public void verify_whenJwtMissesAudienceClaim() throws Exception {
    var credentialsAdapter = new IamCredentialsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);
    var serviceAccount = ITestEnvironment.NO_ACCESS_USER;

    var tokenSigner = new ServiceAccountSigner(
      credentialsAdapter,
      new ServiceAccountSigner.Options(serviceAccount));

    var payload = new JsonWebToken.Payload()
      .setIssuer(serviceAccount.value());
    var jwt = credentialsAdapter.signJwt(serviceAccount, payload);

    assertThrows(
      TokenVerifier.VerificationException.class,
      () -> tokenSigner.verify(jwt));
  }

  @Test
  public void verify_whenJwtMissesIssuerClaim() throws Exception {
    var credentialsAdapter = new IamCredentialsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);
    var serviceAccount = ITestEnvironment.NO_ACCESS_USER;

    var tokenSigner = new ServiceAccountSigner(
      credentialsAdapter,
      new ServiceAccountSigner.Options(serviceAccount));

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.value());

    var jwt = credentialsAdapter.signJwt(serviceAccount, payload);

    assertThrows(
      TokenVerifier.VerificationException.class,
      () -> tokenSigner.verify(jwt));
  }

  @Test
  public void verify_whenJwtSignedByWrongServiceAccount() throws Exception {
    var credentialsAdapter = new IamCredentialsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);
    var serviceAccount = ITestEnvironment.TEMPORARY_ACCESS_USER;

    var tokenSigner = new ServiceAccountSigner(
      credentialsAdapter,
      new ServiceAccountSigner.Options(serviceAccount));

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.value())
      .setIssuer(serviceAccount.value());

    var jwt = credentialsAdapter.signJwt(ITestEnvironment.NO_ACCESS_USER, payload);

    assertThrows(
      TokenVerifier.VerificationException.class,
      () -> tokenSigner.verify(jwt));
  }

  @Test
  public void verify_whenJwtValid() throws Exception {
    var credentialsAdapter = new IamCredentialsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);
    var serviceAccount = ITestEnvironment.NO_ACCESS_USER;

    var tokenSigner = new ServiceAccountSigner(
      credentialsAdapter,
      new ServiceAccountSigner.Options(serviceAccount));

    var inputPayload = new JsonWebToken.Payload()
      .setJwtId("sample-1");

    var token = tokenSigner.sign(inputPayload, Instant.now().plusSeconds(300));
    var outputPayload = tokenSigner.verify(token.token());

    assertEquals(inputPayload.getJwtId(), outputPayload.getJwtId());
  }
}