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
import com.google.solutions.jitaccess.apis.ProjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ITestIamClient {

  // -------------------------------------------------------------------------
  // listGrantableRoles.
  // -------------------------------------------------------------------------

  @Test
  public void listGrantableRoles_whenUnauthenticated() {
    var client = new IamClient(
      new IamClient.Options(10),
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> client.listGrantableRoles(ITestEnvironment.PROJECT_ID));
  }

  @Test
  public void listGrantableRoles_whenCallerLacksPermission() {
    var client = new IamClient(
      new IamClient.Options(10),
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> client.listGrantableRoles(ITestEnvironment.PROJECT_ID));
  }

  @Test
  public void listGrantableRoles_whenProjectDoesNotExist() {
    var client = new IamClient(
      new IamClient.Options(10),
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> client.listGrantableRoles(new ProjectId("0")));
  }

  @Test
  public void listGrantableRoles() throws Exception {
    var client = new IamClient(
      new IamClient.Options(100),
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    var roles = client.listGrantableRoles(ITestEnvironment.PROJECT_ID);

    assertTrue(roles.size() > 100);
  }
}
