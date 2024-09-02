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

package com.google.solutions.jitaccess.apis;

import com.google.api.services.iam.v1.model.LintResult;
import com.google.solutions.jitaccess.apis.clients.IamClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static io.smallrye.common.constraint.Assert.assertFalse;
import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class TestIamRoleResolver {
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");

  // -------------------------------------------------------------------------
  // exists.
  // -------------------------------------------------------------------------

  @Test
  public void exists_whenPredefinedRole() throws Exception {
    var iamClient = Mockito.mock(IamClient.class);
    when(iamClient.listPredefinedRoles())
      .thenReturn(List.of(
        new IamRole("roles/owner"),
        new IamRole("roles/editor"),
        new IamRole("roles/viewer")));
    var resolver = new IamRoleResolver(iamClient);

    assertTrue(resolver.exists(new IamRole("roles/owner")));
  }

  @Test
  public void exists_whenUnknown() throws Exception {
    var iamClient = Mockito.mock(IamClient.class);
    when(iamClient.listPredefinedRoles())
      .thenReturn(List.of());
    var resolver = new IamRoleResolver(iamClient);

    assertFalse(resolver.exists(new IamRole("roles/unknown")));
  }

  @Test
  public void exists_whenProjectCustomRole() {
    var resolver = new IamRoleResolver(Mockito.mock(IamClient.class));

    assertTrue(resolver.exists(new IamRole("projects/project-1/roles/role")));
  }

  @Test
  public void exists_whenOrganizationCustomRole() {
    var resolver = new IamRoleResolver(Mockito.mock(IamClient.class));

    assertTrue(resolver.exists(new IamRole("organizations/123/roles/role")));
  }

  // -------------------------------------------------------------------------
  // lintRoleBinding.
  // -------------------------------------------------------------------------

  @Test
  public void lintRoleBinding_whenRoleUnknownAndConditionInvalid() throws Exception {
    var iamClient = Mockito.mock(IamClient.class);
    when(iamClient.listPredefinedRoles())
      .thenReturn(List.of());
    when(iamClient.lintIamCondition(
      eq(SAMPLE_PROJECT),
      eq("condition")))
      .thenReturn(List.of(new LintResult().setDebugMessage("not good")));

    var resolver = new IamRoleResolver(iamClient);
    var issues = resolver.lintRoleBinding(
      SAMPLE_PROJECT,
      new IamRole("roles/owner"),
      "condition");

    assertEquals(2, issues.size());
  }

  @Test
  public void lintRoleBinding() throws Exception {
    var iamClient = Mockito.mock(IamClient.class);
    when(iamClient.listPredefinedRoles())
      .thenReturn(List.of(new IamRole("roles/owner")));
    when(iamClient.lintIamCondition(
      eq(SAMPLE_PROJECT),
      eq("condition")))
      .thenReturn(List.of());

    var resolver = new IamRoleResolver(iamClient);
    var issues = resolver.lintRoleBinding(
      SAMPLE_PROJECT,
      new IamRole("roles/owner"),
      "condition");

    assertTrue(issues.isEmpty());
  }
}
