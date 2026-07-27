//
// Copyright 2026 Google LLC
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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.parametermanager.v1.ParameterManager;
import com.google.api.services.parametermanager.v1.model.Parameter;
import com.google.auth.http.HttpCredentialsAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ITestParameterManagerClient {
  private static final String PARAMETER_NAME = "testparameter";
  private static final String PARAMETER_PATH = String.format(
    "projects/%s/locations/global/parameters/%s",
    ITestEnvironment.PROJECT_ID,
    PARAMETER_NAME);
  private static final String PARAMETER_LATEST_VERSION_PATH = String.format(
    "%s/versions/latest",
    PARAMETER_PATH);

  private static ParameterManager createClient() throws GeneralSecurityException, IOException {
    return new ParameterManager.Builder(
      HttpTransport.newTransport(),
      new GsonFactory(),
      new HttpCredentialsAdapter(ITestEnvironment.APPLICATION_CREDENTIALS))
      .build();
  }

  @BeforeAll
  public static void recreateParameter() throws GeneralSecurityException, IOException {
    var client = createClient();
    //
    // Delete existing parameter if it exists.
    //
    try {
      client
        .projects()
        .locations()
        .parameters()
        .delete(PARAMETER_PATH)
        .execute();
    }
    catch (GoogleJsonResponseException e)
    {
      if (e.getStatusCode() != 404) {
        throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }

    //
    // Create new parameter.
    //
    client
      .projects()
      .locations()
      .parameters()
      .create(String.format("projects/%s/locations/global", ITestEnvironment.PROJECT_ID),
        new Parameter().setFormat("YAML")
      ).setParameterId(PARAMETER_NAME)
      .execute();
  }

  //---------------------------------------------------------------------
  // render.
  //---------------------------------------------------------------------

  @Test
  public void render_whenUnauthenticated_thenThrowsException() {
    var adapter = new ParameterManagerClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      ITestEnvironment.REGION_ID,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> adapter.render(PARAMETER_LATEST_VERSION_PATH));
  }

  @Test
  public void render_whenCallerLacksPermission_thenThrowsException() {
    var adapter = new ParameterManagerClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      ITestEnvironment.REGION_ID,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> adapter.render(PARAMETER_LATEST_VERSION_PATH));
  }

  @Test
  public void render_whenParameterNotFondPermission_thenThrowsException() {
    var adapter = new ParameterManagerClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      ITestEnvironment.REGION_ID,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      ResourceNotFoundException.class,
      () -> adapter.render(String.format(
        "projects/%s/locations/global/parameters/doesnotexist/versions/latest",
        ITestEnvironment.PROJECT_ID)));
  }

  @Test
  public void render_whenParameterVersionNotFondPermission_thenThrowsException() {
    var adapter = new ParameterManagerClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      ITestEnvironment.REGION_ID,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      ResourceNotFoundException.class,
      () -> adapter.render(String.format(
        "%s/versions/99",
        PARAMETER_PATH)));
  }
}
