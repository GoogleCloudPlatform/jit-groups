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

package com.google.solutions.jitaccess.core.clients;

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.NotAuthenticatedException;
import com.google.solutions.jitaccess.core.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ITestDirectoryGroupsClient {
  private final String INVALID_CUSTOMER_ID = "Cinvalid";
  //---------------------------------------------------------------------
  // listDirectGroupMemberships.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenListDirectGroupMembershipsThrowsException() {
    var adapter = new DirectoryGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new DirectoryGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> adapter.listDirectGroupMemberships(ITestEnvironment.NO_ACCESS_USER));
  }

  @Test
  public void whenUserFromUnknownDomain_ThenListDirectGroupMembershipsThrowsException() {
    var adapter = new DirectoryGroupsClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      new DirectoryGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      ResourceNotFoundException.class,
      () -> adapter.listDirectGroupMemberships(ITestEnvironment.NO_ACCESS_USER));
  }

  @Test
  public void whenCallerLacksPermission_ThenListDirectGroupMembershipsThrowsException() {
    var adapter = new DirectoryGroupsClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      new DirectoryGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      ResourceNotFoundException.class,
      () -> adapter.listDirectGroupMemberships(ITestEnvironment.NO_ACCESS_USER));
  }

  //---------------------------------------------------------------------
  // listDirectGroupMembers.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenListDirectGroupMembersThrowsException() {
    var adapter = new DirectoryGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new DirectoryGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> adapter.listDirectGroupMembers("group@example.com"));
  }

  @Test
  public void whenCallerLacksPermission_ThenListDirectGroupMembersThrowsException() {
    var adapter = new DirectoryGroupsClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      new DirectoryGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> adapter.listDirectGroupMembers("group@example.com"));
  }

  @Test
  public void whenGroupDoesNotExist_ThenListDirectGroupMembersThrowsException() {
    var adapter = new DirectoryGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new DirectoryGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      ResourceNotFoundException.class,
      () -> adapter.listDirectGroupMembers("unknown-groupkey"));
  }
}
