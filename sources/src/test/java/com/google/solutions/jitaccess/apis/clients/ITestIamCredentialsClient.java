//
// Copyright 2024 Google LLC
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

package com.google.solutions.jitaccess.apis.clients;

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ITestIamCredentialsClient {

  // -------------------------------------------------------------------------
  // signJwt.
  // -------------------------------------------------------------------------

  @Test
  public void signJwt_whenUnauthenticated_thenThrowsException() {
    var adapter = new IamCredentialsClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    var payload = new JsonWebToken.Payload()
      .setAudience("test");

    assertThrows(
      AccessDeniedException.class,
      () -> adapter.signJwt(ITestEnvironment.NO_ACCESS_USER, payload));
  }

  @Test
  public void signJwt() throws Exception {
    var adapter = new IamCredentialsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);
    var serviceAccount = ITestEnvironment.NO_ACCESS_USER;

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.value())
      .setIssuer(serviceAccount.value());

    var jwt = adapter.signJwt(serviceAccount, payload);
    assertNotNull(jwt);

    TokenVerifier
      .newBuilder()
      .setCertificatesLocation(IamCredentialsClient.getJwksUrl(serviceAccount))
      .setIssuer(serviceAccount.value())
      .setAudience(serviceAccount.value())
      .build()
      .verify(jwt);
  }

  // -------------------------------------------------------------------------
  // getJwksUrl.
  // -------------------------------------------------------------------------

  @Test
  public void getJwksUrl() {
    assertEquals(
      String.format(
        "https://www.googleapis.com/service_accounts/v1/metadata/jwk/%s",
        ITestEnvironment.NO_ACCESS_USER.value()),
      IamCredentialsClient.getJwksUrl(ITestEnvironment.NO_ACCESS_USER));
  }
}
