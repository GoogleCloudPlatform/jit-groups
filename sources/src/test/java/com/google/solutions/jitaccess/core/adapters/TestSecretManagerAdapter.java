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

package com.google.solutions.jitaccess.core.adapters;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.secretmanager.v1.SecretManager;
import com.google.api.services.secretmanager.v1.model.Automatic;
import com.google.api.services.secretmanager.v1.model.Replication;
import com.google.api.services.secretmanager.v1.model.Secret;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.NotAuthenticatedException;
import com.google.solutions.jitaccess.core.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.*;

public class TestSecretManagerAdapter {
  private static final String SECRET_NAME = "testsecret";
  private static final String SECRET_PATH = String.format(
    "projects/%s/secrets/%s",
    IntegrationTestEnvironment.PROJECT_ID,
    SECRET_NAME);
  private static final String SECRET_LASTEST_VERSION_PATH = String.format(
    "%s/versions/latest",
    SECRET_PATH);
  private static final String SECRET_CONTENT = "(secret)";

  private static SecretManager createClient() throws GeneralSecurityException, IOException {
    return new SecretManager.Builder(
      HttpTransport.newTransport(),
      new GsonFactory(),
      new HttpCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS))
      .build();
  }

  @BeforeAll
  public static void recreateSecret() throws GeneralSecurityException, IOException {
    var client = createClient();
    //
    // Delete existing secret if it exists.
    //
    try {
      client
        .projects()
        .secrets()
        .delete(SECRET_PATH)
        .execute();
    }
    catch (GoogleJsonResponseException e)
    {
      if (e.getStatusCode() != 404) {
        throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }

    //
    // Create new secret.
    //
    client
      .projects()
      .secrets()
      .create(String.format("projects/%s", IntegrationTestEnvironment.PROJECT_ID),
        new Secret().setReplication(new Replication().setAutomatic(new Automatic()))
      )
      .setSecretId(SECRET_NAME)
      .execute();
  }

  //---------------------------------------------------------------------
  // accessSecret.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenAccessSecretThrowsException() {
    var adapter = new SecretManagerAdapter(IntegrationTestEnvironment.INVALID_CREDENTIAL);

    assertThrows(
      NotAuthenticatedException.class,
      () -> adapter.accessSecret(SECRET_LASTEST_VERSION_PATH));
  }

  @Test
  public void whenCallerLacksPermission_ThenAccessSecretThrowsException() {
    var adapter = new SecretManagerAdapter(IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS);

    assertThrows(
      AccessDeniedException.class,
      () ->adapter.accessSecret(SECRET_LASTEST_VERSION_PATH));
  }

  @Test
  public void whenSecretNotFondPermission_ThenAccessSecretThrowsException() {
    var adapter = new SecretManagerAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    assertThrows(
      ResourceNotFoundException.class,
      () ->adapter.accessSecret(String.format(
        "projects/%s/secrets/doesnotexist/versions/latest",
        IntegrationTestEnvironment.PROJECT_ID)));
  }

  @Test
  public void whenSecretVersionNotFondPermission_ThenAccessSecretThrowsException() {
    var adapter = new SecretManagerAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    assertThrows(
      ResourceNotFoundException.class,
      () ->adapter.accessSecret(String.format(
        "%s/versions/99",
        SECRET_PATH)));
  }
}
