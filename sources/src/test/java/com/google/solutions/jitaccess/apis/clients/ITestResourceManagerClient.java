//
// Copyright 2021 Google LLC
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

import com.google.solutions.jitaccess.apis.CustomerId;
import com.google.solutions.jitaccess.apis.ResourceId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class ITestResourceManagerClient {
  private static final String REQUEST_REASON = "testing";

  //---------------------------------------------------------------------
  // modifyIamPolicy.
  //---------------------------------------------------------------------

  @Test
  public void modifyIamPolicy_whenResourceFromDifferentService() throws Exception {
    var id = Mockito.mock(ResourceId.class);
    when(id.service()).thenReturn("other.googleapis.com");
    
    var client = new ResourceManagerClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      IllegalArgumentException.class,
      () ->  client.modifyIamPolicy(
      id,
        policy -> {},
        REQUEST_REASON));
  }
  
  @Test
  public void modifyIamPolicy_project() throws Exception {
    var client = new ResourceManagerClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    client.modifyIamPolicy(
      ITestEnvironment.PROJECT_ID,
      policy -> {},
      REQUEST_REASON);
  }

  //---------------------------------------------------------------------
  // getOrganization.
  //---------------------------------------------------------------------

  @Test
  public void getOrganization_whenUnauthenticated_thenThrowsException() {
    var client = new ResourceManagerClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.getOrganization(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID));
  }

  @Test
  public void getOrganization_whenCallerLacksPermission_thenThrowsException() throws Exception {
    var client = new ResourceManagerClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    assertFalse(client
      .getOrganization(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID)
      .isPresent());
  }

  @Test
  public void getOrganization_whenOrganizationNotFound() throws Exception {
    var client = new ResourceManagerClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    assertFalse(client.getOrganization(new CustomerId("C0000000")).isPresent());
  }

  @Test
  public void getOrganization() throws Exception {
    var client = new ResourceManagerClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    var organization = client.getOrganization(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID);
    assertTrue(organization.isPresent());
    assertEquals(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID.id(), organization.get().getDirectoryCustomerId());
    assertNotNull(organization.get().getDisplayName());
  }
}