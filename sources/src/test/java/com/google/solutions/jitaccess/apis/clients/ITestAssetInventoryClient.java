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

import com.google.solutions.jitaccess.apis.ProjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ITestAssetInventoryClient {
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");

  // -------------------------------------------------------------------------
  // getEffectiveIamPolicies.
  // -------------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenGetEffectiveIamPoliciesThrowsException() {
    var adapter = new AssetInventoryClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> adapter.getEffectiveIamPolicies(
        "folders/0",
        "projects/" + SAMPLE_PROJECT));
  }

  @Test
  public void whenCallerLacksPermission_ThenGetEffectiveIamPoliciesThrowsException() {
    var adapter = new AssetInventoryClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> adapter.getEffectiveIamPolicies(
        "folders/0",
        "projects/" + SAMPLE_PROJECT));
  }

  @Test
  public void whenProjectDoesNotExist_ThenGetEffectiveIamPoliciesThrowsException() {
    var adapter = new AssetInventoryClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      ResourceNotFoundException.class,
      () -> adapter.getEffectiveIamPolicies(
        "projects/" + ITestEnvironment.PROJECT_ID,
        "projects/0"));
  }
}