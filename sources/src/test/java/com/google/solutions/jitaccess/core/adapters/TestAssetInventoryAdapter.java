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

package com.google.solutions.jitaccess.core.adapters;

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.NotAuthenticatedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestAssetInventoryAdapter {
  // -------------------------------------------------------------------------
  // findAccessibleResourcesByUser.
  // -------------------------------------------------------------------------

  @Test

  public void whenUnauthenticated_ThenFindAccessibleResourcesByUserThrowsException() throws Exception {
    var adapter = new AssetInventoryAdapter(IntegrationTestEnvironment.INVALID_CREDENTIAL);

    assertThrows(
      NotAuthenticatedException.class,
      () -> adapter.findAccessibleResourcesByUser(
        "projects/0",
        new UserId("", "bob@example.com"),
        true));
  }

  @Test
  public void whenCallerLacksPermission_ThenFindAccessibleResourcesByUserThrowsException() throws Exception {
    var adapter = new AssetInventoryAdapter(IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS);

    assertThrows(
      AccessDeniedException.class,
      () -> adapter.findAccessibleResourcesByUser(
        "projects/0",
        new UserId("", "bob@example.com"),
        true));
  }

  // -------------------------------------------------------------------------
  // findPermissionedPrincipalsByResource.
  // -------------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenPermissionedPrincipalsByResourceThrowsException()
    throws Exception {
    var adapter = new AssetInventoryAdapter(IntegrationTestEnvironment.INVALID_CREDENTIAL);

    assertThrows(
      NotAuthenticatedException.class,
      () -> adapter.findPermissionedPrincipalsByResource(
        "projects/0",
        "//cloudresourcemanager.googleapis.com/projects/132",
        "roles/browser"));
  }

  @Test
  public void whenCallerLacksPermission_ThenFindPermissionedPrincipalsByResourceThrowsException()
    throws Exception {
    var adapter = new AssetInventoryAdapter(IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS);

    assertThrows(
      AccessDeniedException.class,
      () -> adapter.findPermissionedPrincipalsByResource(
        "projects/0",
        "//cloudresourcemanager.googleapis.com/projects/132",
        "roles/browser"));
  }
}
