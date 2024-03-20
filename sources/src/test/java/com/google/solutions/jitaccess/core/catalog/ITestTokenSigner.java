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

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.clients.HttpTransport;
import com.google.solutions.jitaccess.core.clients.ITestEnvironment;
import com.google.solutions.jitaccess.core.clients.IamCredentialsClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class ITestTokenSigner {
  private static final UserId SAMPLE_USER_1 = new UserId("user-1@example.com");
  private static final UserId SAMPLE_USER_2 = new UserId("user-2@example.com");
  private static final UserId SAMPLE_USER_3 = new UserId("user-3@example.com");

  private static class PseudoJsonConverter implements JsonWebTokenConverter<JsonWebToken.Payload> {
    @Override
    public JsonWebToken.Payload convert(JsonWebToken.Payload object) {
      return object;
    }
  }

  // -------------------------------------------------------------------------
  // sign.
  // -------------------------------------------------------------------------

  @Test
  public void signAddsObligatoryClaims() throws Exception {
    var credentialsAdapter = new IamCredentialsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);
    var serviceAccount = ITestEnvironment.NO_ACCESS_USER;

    var tokenSignerOptions = new TokenSigner.Options(serviceAccount, Duration.ofMinutes(5));
    var tokenSigner = new TokenSigner(
      credentialsAdapter,
      tokenSignerOptions);

    var emptyPayload = new JsonWebToken.Payload();

    var token = tokenSigner.sign(
      new PseudoJsonConverter(),
      emptyPayload);

    assertNotNull(token.token());
    assertTrue(token.issueTime().isBefore(Instant.now().plusSeconds(5)));
    assertTrue(token.issueTime().isAfter(Instant.now().minusSeconds(5)));

    assertEquals(
      token.issueTime().plus(tokenSignerOptions.tokenValidity()),
      token.expiryTime());

    var verifiedPayload = TokenVerifier
      .newBuilder()
      .setCertificatesLocation(IamCredentialsClient.getJwksUrl(serviceAccount))
      .setIssuer(serviceAccount.email)
      .setAudience(serviceAccount.email)
      .build()
      .verify(token.token())
      .getPayload();

    assertEquals(serviceAccount.email, verifiedPayload.getIssuer());
    assertEquals(serviceAccount.email, verifiedPayload.getAudience());
    assertEquals(token.issueTime().getEpochSecond(), verifiedPayload.getIssuedAtTimeSeconds());
    assertEquals(token.expiryTime().getEpochSecond(), verifiedPayload.getExpirationTimeSeconds());
  }

  // -------------------------------------------------------------------------
  // verify.
  // -------------------------------------------------------------------------

  @Test
  public void whenJwtMissesAudienceClaim_ThenVerifyThrowsException() throws Exception {
    var credentialsAdapter = new IamCredentialsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);
    var serviceAccount = ITestEnvironment.NO_ACCESS_USER;

    var tokenSigner = new TokenSigner(
      credentialsAdapter,
      new TokenSigner.Options(serviceAccount, Duration.ofMinutes(5)));

    var payload = new JsonWebToken.Payload()
      .setIssuer(serviceAccount.email);
    var jwt = credentialsAdapter.signJwt(serviceAccount, payload);

    assertThrows(
      TokenVerifier.VerificationException.class,
      () -> tokenSigner.verify(
        new PseudoJsonConverter(),
        jwt));
  }

  @Test
  public void whenJwtMissesIssuerClaim_ThenVerifyThrowsException() throws Exception {
    var credentialsAdapter = new IamCredentialsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);
    var serviceAccount = ITestEnvironment.NO_ACCESS_USER;

    var tokenSigner = new TokenSigner(
      credentialsAdapter,
      new TokenSigner.Options(serviceAccount, Duration.ofMinutes(5)));

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.email);

    var jwt = credentialsAdapter.signJwt(serviceAccount, payload);

    assertThrows(
      TokenVerifier.VerificationException.class,
      () -> tokenSigner.verify(
        new PseudoJsonConverter(),
        jwt));
  }

  @Test
  public void whenJwtSignedByWrongServiceAccount_ThenVerifyThrowsException() throws Exception {
    var credentialsAdapter = new IamCredentialsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);
    var serviceAccount = ITestEnvironment.TEMPORARY_ACCESS_USER;

    var tokenSigner = new TokenSigner(
      credentialsAdapter,
      new TokenSigner.Options(serviceAccount, Duration.ofMinutes(5)));

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.email)
      .setIssuer(serviceAccount.email);

    var jwt = credentialsAdapter.signJwt(ITestEnvironment.NO_ACCESS_USER, payload);

    assertThrows(
      TokenVerifier.VerificationException.class,
      () -> tokenSigner.verify(
        new PseudoJsonConverter(),
        jwt));
  }

  @Test
  public void whenJwtValid_ThenVerifySucceeds() throws Exception {
    var credentialsAdapter = new IamCredentialsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);
    var serviceAccount = ITestEnvironment.NO_ACCESS_USER;

    var tokenSigner = new TokenSigner(
      credentialsAdapter,
      new TokenSigner.Options(serviceAccount, Duration.ofMinutes(5)));

    var inputPayload = new JsonWebToken.Payload()
      .setJwtId("sample-1");

    var token = tokenSigner.sign(
      new PseudoJsonConverter(),
      inputPayload);
    var outputPayload = tokenSigner.verify(
      new PseudoJsonConverter(),
      token.token());

    assertEquals(inputPayload.getJwtId(), outputPayload.getJwtId());
  }
}
