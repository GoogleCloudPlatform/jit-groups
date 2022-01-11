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

package com.google.solutions.iamelevate.core.adapters;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.resourcemanager.v3.ProjectName;
import com.google.cloud.resourcemanager.v3.ProjectsClient;
import com.google.cloud.resourcemanager.v3.ProjectsSettings;
import com.google.iam.v1.Binding;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.GetPolicyOptions;
import com.google.solutions.iamelevate.core.AccessDeniedException;
import com.google.solutions.iamelevate.core.NotAuthenticatedException;
import com.google.type.Expr;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

public class TestResourceManagerAdapter {
  private static final String REQUEST_REASON = "testing";

  //---------------------------------------------------------------------
  // addIamBinding.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenAddIamBindingAsyncThrowsException() throws Exception {
    var adapter = new ResourceManagerAdapter(IntegrationTestEnvironment.INVALID_CREDENTIAL);

    assertThrows(
        NotAuthenticatedException.class,
        () ->
            adapter.addIamBinding(
                ProjectName.of(IntegrationTestEnvironment.PROJECT_ID),
                Binding.newBuilder()
                    .addMembers("user:bob@example.com")
                    .setRole("roles/resourcemanager.projectIamAdmin")
                    .build(),
                EnumSet.of(ResourceManagerAdapter.IamBindingOptions.NONE),
                REQUEST_REASON));
  }

  @Test
  public void whenCallerLacksPermission_ThenAddIamBindingAsyncThrowsException() throws Exception {
    var adapter = new ResourceManagerAdapter(IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS);

    assertThrows(
        AccessDeniedException.class,
        () ->
            adapter.addIamBinding(
                ProjectName.of(IntegrationTestEnvironment.PROJECT_ID),
                Binding.newBuilder()
                    .addMembers("user:bob@example.com")
                    .setRole("roles/resourcemanager.projectIamAdmin")
                    .build(),
                EnumSet.of(ResourceManagerAdapter.IamBindingOptions.NONE),
                REQUEST_REASON));
  }

  @Test
  public void whenResourceIsProject_ThenAddIamBindingAsyncSucceeds() throws Exception {
    var adapter = new ResourceManagerAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    String condition =
        IamConditions.createTemporaryConditionClause(OffsetDateTime.now(), Duration.ofMinutes(5));

    adapter.addIamBinding(
        ProjectName.of(IntegrationTestEnvironment.PROJECT_ID),
        Binding.newBuilder()
            .addMembers(
                "serviceAccount:" + IntegrationTestEnvironment.TEMPORARY_ACCESS_USER.getEmail())
            .setRole("roles/browser")
            .setCondition(Expr.newBuilder().setExpression(condition).build())
            .build(),
        EnumSet.of(
            ResourceManagerAdapter.IamBindingOptions.REPLACE_BINDINGS_FOR_SAME_PRINCIPAL_AND_ROLE),
        REQUEST_REASON);
  }

  @Test
  public void
      whenReplaceBindingsForSamePrincipalAndRoleOptionOn_ThenExistingTemporaryBindingsAreRemoved()
          throws Exception {
    var adapter = new ResourceManagerAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    // Add an "old" temporary IAM binding.
    adapter.addIamBinding(
        ProjectName.of(IntegrationTestEnvironment.PROJECT_ID),
        Binding.newBuilder()
            .addMembers(
                "serviceAccount:" + IntegrationTestEnvironment.TEMPORARY_ACCESS_USER.getEmail())
            .setRole("roles/browser")
            .setCondition(
                Expr.newBuilder()
                    .setTitle("old binding")
                    .setExpression(
                        IamConditions.createTemporaryConditionClause(
                            OffsetDateTime.now().minusDays(1), Duration.ofMinutes(5)))
                    .build())
            .build(),
        EnumSet.of(ResourceManagerAdapter.IamBindingOptions.NONE),
        REQUEST_REASON);

    // Add a permanent binding (with some random condition) for the same role.
    adapter.addIamBinding(
        ProjectName.of(IntegrationTestEnvironment.PROJECT_ID),
        Binding.newBuilder()
            .addMembers(
                "serviceAccount:" + IntegrationTestEnvironment.TEMPORARY_ACCESS_USER.getEmail())
            .setRole("roles/browser")
            .setCondition(
                Expr.newBuilder()
                    .setTitle("permanent binding")
                    .setExpression("resource.service == \"storage.googleapis.com\"")
                    .build())
            .build(),
        EnumSet.of(ResourceManagerAdapter.IamBindingOptions.NONE),
        REQUEST_REASON);

    var client =
        ProjectsClient.create(
            ProjectsSettings.newBuilder()
                .setCredentialsProvider(
                    FixedCredentialsProvider.create(
                        IntegrationTestEnvironment.APPLICATION_CREDENTIALS))
                .build());

    var oldPolicy =
        client.getIamPolicy(
            GetIamPolicyRequest.newBuilder()
                .setResource(ProjectName.of(IntegrationTestEnvironment.PROJECT_ID).toString())
                .setOptions(GetPolicyOptions.newBuilder().setRequestedPolicyVersion(3))
                .build());

    assertTrue(
        oldPolicy.getBindingsList().stream()
            .anyMatch(b -> b.getCondition().getTitle().equals("old binding")),
        "old binding has been added");
    assertTrue(
        oldPolicy.getBindingsList().stream()
            .anyMatch(b -> b.getCondition().getTitle().equals("permanent binding")));

    // Add "new" temporary binding, overriding the old one.
    adapter.addIamBinding(
        ProjectName.of(IntegrationTestEnvironment.PROJECT_ID),
        Binding.newBuilder()
            .addMembers(
                "serviceAccount:" + IntegrationTestEnvironment.TEMPORARY_ACCESS_USER.getEmail())
            .setRole("roles/browser")
            .setCondition(
                Expr.newBuilder()
                    .setTitle("new binding")
                    .setExpression(
                        IamConditions.createTemporaryConditionClause(
                            OffsetDateTime.now(), Duration.ofMinutes(5)))
                    .build())
            .build(),
        EnumSet.of(
            ResourceManagerAdapter.IamBindingOptions.REPLACE_BINDINGS_FOR_SAME_PRINCIPAL_AND_ROLE),
        REQUEST_REASON);

    var newPolicy =
        client.getIamPolicy(
            GetIamPolicyRequest.newBuilder()
                .setResource(ProjectName.of(IntegrationTestEnvironment.PROJECT_ID).toString())
                .setOptions(GetPolicyOptions.newBuilder().setRequestedPolicyVersion(3))
                .build());

    assertFalse(
        newPolicy.getBindingsList().stream()
            .anyMatch(b -> b.getCondition().getTitle().equals("old binding")));
    assertTrue(
        newPolicy.getBindingsList().stream()
            .anyMatch(b -> b.getCondition().getTitle().equals("new binding")));
    assertTrue(
        newPolicy.getBindingsList().stream()
            .anyMatch(b -> b.getCondition().getTitle().equals("permanent binding")));
  }
}
