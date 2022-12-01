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

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.cloudresourcemanager.v3.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.Expr;
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.GetPolicyOptions;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.NotAuthenticatedException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestResourceManagerAdapter {
  private static final String REQUEST_REASON = "testing";

  //---------------------------------------------------------------------
  // addProjectIamBinding.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenAddIamProjectBindingAsyncThrowsException() throws Exception {
    var adapter = new ResourceManagerAdapter(IntegrationTestEnvironment.INVALID_CREDENTIAL);

    assertThrows(
      NotAuthenticatedException.class,
      () ->
        adapter.addProjectIamBinding(
          IntegrationTestEnvironment.PROJECT_ID,
          new Binding()
            .setMembers(List.of("user:bob@example.com"))
            .setRole("roles/resourcemanager.projectIamAdmin"),
          EnumSet.of(ResourceManagerAdapter.IamBindingOptions.NONE),
          REQUEST_REASON));
  }

  @Test
  public void whenCallerLacksPermission_ThenAddProjectIamBindingAsyncThrowsException() throws Exception {
    var adapter = new ResourceManagerAdapter(IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS);

    assertThrows(
      AccessDeniedException.class,
      () ->
        adapter.addProjectIamBinding(
          IntegrationTestEnvironment.PROJECT_ID,
          new Binding()
            .setMembers(List.of("user:bob@example.com"))
            .setRole("roles/resourcemanager.projectIamAdmin"),
          EnumSet.of(ResourceManagerAdapter.IamBindingOptions.NONE),
          REQUEST_REASON));
  }

  @Test
  public void whenResourceIsProject_ThenAddIamProjectBindingAsyncSucceeds() throws Exception {
    var adapter = new ResourceManagerAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    String condition =
      IamConditions.createTemporaryConditionClause(OffsetDateTime.now(), Duration.ofMinutes(5));

    adapter.addProjectIamBinding(
      IntegrationTestEnvironment.PROJECT_ID,
      new Binding()
        .setMembers(List.of("serviceAccount:" + IntegrationTestEnvironment.TEMPORARY_ACCESS_USER.email))
        .setRole("roles/browser")
        .setCondition(new Expr().setExpression(condition)),
      EnumSet.of(ResourceManagerAdapter.IamBindingOptions.REPLACE_BINDINGS_FOR_SAME_PRINCIPAL_AND_ROLE),
      REQUEST_REASON);
  }

  @Test
  public void whenReplaceBindingsForSamePrincipalAndRoleOptionOn_ThenExistingTemporaryBindingsAreRemoved() throws Exception {
    var adapter = new ResourceManagerAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    // Add an "old" temporary IAM binding.
    adapter.addProjectIamBinding(
      IntegrationTestEnvironment.PROJECT_ID,
      new Binding()
        .setMembers(List.of("serviceAccount:" + IntegrationTestEnvironment.TEMPORARY_ACCESS_USER.email))
        .setRole("roles/browser")
        .setCondition(new Expr()
          .setTitle("old binding")
          .setExpression(IamConditions.createTemporaryConditionClause(
            OffsetDateTime.now().minusDays(1),
            Duration.ofMinutes(5)))),
      EnumSet.of(ResourceManagerAdapter.IamBindingOptions.NONE),
      REQUEST_REASON);

    // Add a permanent binding (with some random condition) for the same role.
    adapter.addProjectIamBinding(
      IntegrationTestEnvironment.PROJECT_ID,
      new Binding()
        .setMembers(List.of("serviceAccount:" + IntegrationTestEnvironment.TEMPORARY_ACCESS_USER.email))
        .setRole("roles/browser")
        .setCondition(new Expr()
          .setTitle("permanent binding")
          .setExpression("resource.service == \"storage.googleapis.com\"")),
      EnumSet.of(ResourceManagerAdapter.IamBindingOptions.NONE),
      REQUEST_REASON);

    var service = new CloudResourceManager
      .Builder(
      HttpTransport.newTransport(),
      new GsonFactory(),
      new HttpCredentialsAdapter(GoogleCredentials.getApplicationDefault()))
      .build();

    var oldPolicy = service
      .projects()
      .getIamPolicy(
        String.format("projects/%s", IntegrationTestEnvironment.PROJECT_ID),
        new GetIamPolicyRequest()
          .setOptions(new GetPolicyOptions().setRequestedPolicyVersion(3)))
      .execute();

    assertTrue(
      oldPolicy.getBindings().stream().anyMatch(
        b -> b.getCondition() != null && "old binding".equals(b.getCondition().getTitle())),
      "old binding has been added");
    assertTrue(
      oldPolicy.getBindings().stream().anyMatch(
        b -> b.getCondition() != null && "permanent binding".equals(b.getCondition().getTitle())));

    // Add "new" temporary binding, overriding the old one.
    adapter.addProjectIamBinding(
      IntegrationTestEnvironment.PROJECT_ID,
      new Binding()
        .setMembers(List.of("serviceAccount:" + IntegrationTestEnvironment.TEMPORARY_ACCESS_USER.email))
        .setRole("roles/browser")
        .setCondition(new Expr()
          .setTitle("new binding")
          .setExpression(
            IamConditions.createTemporaryConditionClause(
              OffsetDateTime.now(), Duration.ofMinutes(5)))),
      EnumSet.of(ResourceManagerAdapter.IamBindingOptions.REPLACE_BINDINGS_FOR_SAME_PRINCIPAL_AND_ROLE),
      REQUEST_REASON);

    var newPolicy = service
      .projects()
      .getIamPolicy(
        String.format("projects/%s", IntegrationTestEnvironment.PROJECT_ID),
        new GetIamPolicyRequest()
          .setOptions(new GetPolicyOptions().setRequestedPolicyVersion(3)))
      .execute();

    assertFalse(newPolicy.getBindings().stream().anyMatch(
      b -> b.getCondition() != null && b.getCondition().getTitle().equals("old binding")));
    assertTrue(newPolicy.getBindings().stream().anyMatch(
      b -> b.getCondition() != null && b.getCondition().getTitle().equals("new binding")));
    assertTrue(newPolicy.getBindings().stream().anyMatch(
      b -> b.getCondition() != null && b.getCondition().getTitle().equals("permanent binding")));
  }
}
