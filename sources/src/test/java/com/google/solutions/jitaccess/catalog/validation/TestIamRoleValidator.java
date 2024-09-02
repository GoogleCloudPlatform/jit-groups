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

package com.google.solutions.jitaccess.catalog.validation;

import com.google.solutions.jitaccess.apis.IamRole;
import com.google.solutions.jitaccess.apis.clients.IamClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static io.smallrye.common.constraint.Assert.assertFalse;
import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class TestIamRoleValidator {

  // -------------------------------------------------------------------------
  // isValidRole.
  // -------------------------------------------------------------------------

  @Test
  public void isValidRole_whenPredefinedRole() throws Exception {
    var iamClient = Mockito.mock(IamClient.class);
    when(iamClient.listPredefinedRoles())
      .thenReturn(List.of(
        new IamRole("roles/owner"),
        new IamRole("roles/editor"),
        new IamRole("roles/viewer")));
    var validator = new IamRoleValidator(iamClient);

    assertTrue(validator.isValidRole(new IamRole("roles/owner")));
  }

  @Test
  public void isValidRole_whenUnknown() throws Exception {
    var iamClient = Mockito.mock(IamClient.class);
    when(iamClient.listPredefinedRoles())
      .thenReturn(List.of(
        new IamRole("roles/owner"),
        new IamRole("roles/editor"),
        new IamRole("roles/viewer")));
    var validator = new IamRoleValidator(iamClient);

    assertFalse(validator.isValidRole(new IamRole("roles/unknown")));
  }

  @Test
  public void isValidRole_whenProjectCustomRole() {
    var validator = new IamRoleValidator(Mockito.mock(IamClient.class));

    assertTrue(validator.isValidRole(new IamRole("projects/project-1/roles/role")));
  }

  @Test
  public void isValidRole_whenOrganizationCustomRole() {
    var validator = new IamRoleValidator(Mockito.mock(IamClient.class));

    assertTrue(validator.isValidRole(new IamRole("organizations/123/roles/role")));
  }
}
