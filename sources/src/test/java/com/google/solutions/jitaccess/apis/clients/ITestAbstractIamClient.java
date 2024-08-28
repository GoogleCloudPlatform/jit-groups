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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.SetIamPolicyRequest;
import com.google.auth.Credentials;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ITestAbstractIamClient {
  private static @NotNull AbstractIamClient createIamClient(
    @NotNull Credentials credentials
  ) {
    return new ResourceManagerClient(credentials, HttpTransport.Options.DEFAULT);
  }

  //-------------------------------------------------------------------------
  // getIamPolicy.
  //-------------------------------------------------------------------------

  @Test
  public void getIamPolicy_whenUnauthenticated() throws Exception {
    var request = createIamClient(ITestEnvironment.INVALID_CREDENTIAL)
      .getIamPolicy(
        "/v1/projects/" + ITestEnvironment.PROJECT_ID,
        new GetIamPolicyRequest());

    var exception = assertThrows(
      GoogleJsonResponseException.class,
      () -> request.execute());
    assertEquals(401, exception.getStatusCode());
  }

  @Test
  public void getIamPolicy_whenCallerLacksPermission() throws Exception {
    var request = createIamClient(ITestEnvironment.NO_ACCESS_CREDENTIALS)
      .getIamPolicy(
        "/v1/projects/" + ITestEnvironment.PROJECT_ID,
        new GetIamPolicyRequest());

    var exception = assertThrows(
      GoogleJsonResponseException.class,
      () -> request.execute());
    assertEquals(403, exception.getStatusCode());
  }

  @Test
  public void getIamPolicy_whenResourceInvalid() throws Exception {
    var request = createIamClient(ITestEnvironment.APPLICATION_CREDENTIALS)
      .getIamPolicy(
        "/v1/something",
        new GetIamPolicyRequest());

    var exception = assertThrows(
      GoogleJsonResponseException.class,
      () -> request.execute());
    assertEquals(404, exception.getStatusCode());
  }

  @Test
  public void getIamPolicy() throws Exception {
    var request = createIamClient(ITestEnvironment.APPLICATION_CREDENTIALS)
      .getIamPolicy(
        "/v1/projects/" + ITestEnvironment.PROJECT_ID,
        new GetIamPolicyRequest());

    var policy = request.execute();
    assertNotNull(policy);
  }

  //-------------------------------------------------------------------------
  // setIamPolicy.
  //-------------------------------------------------------------------------

  @Test
  public void setIamPolicy_whenUnauthenticated() throws Exception {
    var request = createIamClient(ITestEnvironment.INVALID_CREDENTIAL)
      .setIamPolicy(
        "/v1/projects/" + ITestEnvironment.PROJECT_ID,
        new SetIamPolicyRequest());

    var exception = assertThrows(
      GoogleJsonResponseException.class,
      () -> request.execute());
    assertEquals(401, exception.getStatusCode());
  }

  @Test
  public void setIamPolicy_whenCallerLacksPermission() throws Exception {
    var request = createIamClient(ITestEnvironment.NO_ACCESS_CREDENTIALS)
      .setIamPolicy(
        "/v1/projects/" + ITestEnvironment.PROJECT_ID,
        new SetIamPolicyRequest());

    var exception = assertThrows(
      GoogleJsonResponseException.class,
      () -> request.execute());
    assertEquals(403, exception.getStatusCode());
  }

  @Test
  public void setIamPolicy_whenResourceInvalid() throws Exception {
    var request = createIamClient(ITestEnvironment.APPLICATION_CREDENTIALS)
      .setIamPolicy(
        "/v1/something",
        new SetIamPolicyRequest());

    var exception = assertThrows(
      GoogleJsonResponseException.class,
      () -> request.execute());
    assertEquals(404, exception.getStatusCode());
  }
}
