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

package com.google.solutions.jitaccess.apis.clients;

import com.google.solutions.jitaccess.catalog.auth.EmailAddress;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class TestSmtpClient {

  //---------------------------------------------------------------------
  // Options.createAuthenticator.
  //---------------------------------------------------------------------

  @Test
  public void createAuthenticator_whenOptionsContainPassword() throws Exception {
    var options = new SmtpClient.Options(
      "host",
      2525,
      "sender",
      new EmailAddress("sender@example.com"),
      true,
      Map.of())
      .setSmtpCleartextCredentials("user", "password");

    var secretManager = Mockito.mock(SecretManagerClient.class);

    var authentication = options.createPasswordAuthentication(secretManager);
    assertEquals("password", authentication.getPassword());
  }

  @Test
  public void createAuthenticator_whenOptionsContainSecretPath() throws Exception {
    var options = new SmtpClient.Options(
      "host",
      2525,
      "sender",
      new EmailAddress("sender@example.com"),
      true,
      Map.of())
      .setSmtpSecretCredentials("user", "path/to/secret");

    var secretManager = Mockito.mock(SecretManagerClient.class);
    when(secretManager.accessSecret("path/to/secret")).thenReturn("password-from-secret");

    var authentication = options.createPasswordAuthentication(secretManager);
    assertEquals("password-from-secret", authentication.getPassword());
  }
}
