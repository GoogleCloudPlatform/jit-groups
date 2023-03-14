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
import com.google.solutions.jitaccess.core.AlreadyExistsException;
import com.google.solutions.jitaccess.core.NotAuthenticatedException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestResourceManagerAdapter {
  private static final String REQUEST_REASON = "testing";

  //---------------------------------------------------------------------
  // addProjectIamBinding.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenAddIamProjectBindingThrowsException() {
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
  public void whenCallerLacksPermission_ThenAddProjectIamBindingThrowsException() {
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
  public void whenResourceIsProject_ThenAddIamProjectBindingSucceeds() throws Exception {
    var adapter = new ResourceManagerAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    String condition =
      IamTemporaryAccessConditions.createExpression(Instant.now(), Duration.ofMinutes(5));

    adapter.addProjectIamBinding(
      IntegrationTestEnvironment.PROJECT_ID,
      new Binding()
        .setMembers(List.of("serviceAccount:" + IntegrationTestEnvironment.TEMPORARY_ACCESS_USER.email))
        .setRole("roles/browser")
        .setCondition(new Expr().setExpression(condition)),
      EnumSet.of(ResourceManagerAdapter.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS),
      REQUEST_REASON);
  }

  @Test
  public void whenPurgeExistingTemporaryBindingsFlagIsOn_ThenExistingTemporaryBindingsAreRemoved() throws Exception {
    var adapter = new ResourceManagerAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    // Add an "old" temporary IAM binding.
    adapter.addProjectIamBinding(
      IntegrationTestEnvironment.PROJECT_ID,
      new Binding()
        .setMembers(List.of("serviceAccount:" + IntegrationTestEnvironment.TEMPORARY_ACCESS_USER.email))
        .setRole("roles/browser")
        .setCondition(new Expr()
          .setTitle("old binding")
          .setExpression(IamTemporaryAccessConditions.createExpression(
            Instant.now().minus(Duration.ofDays(1)),
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

    var service = new CloudResourceManager.Builder(
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
      oldPolicy
        .getBindings()
        .stream()
        .anyMatch(b -> b.getCondition() != null && "old binding".equals(b.getCondition().getTitle())),
      "old binding has been added");
    assertTrue(
      oldPolicy
        .getBindings()
        .stream()
        .anyMatch(b -> b.getCondition() != null && "permanent binding".equals(b.getCondition().getTitle())),
      "permanent binding has been added");

    // Add "new" temporary binding, overriding the old one.
    adapter.addProjectIamBinding(
      IntegrationTestEnvironment.PROJECT_ID,
      new Binding()
        .setMembers(List.of("serviceAccount:" + IntegrationTestEnvironment.TEMPORARY_ACCESS_USER.email))
        .setRole("roles/browser")
        .setCondition(new Expr()
          .setTitle("new binding")
          .setExpression(
            IamTemporaryAccessConditions.createExpression(
              Instant.now(), Duration.ofMinutes(5)))),
      EnumSet.of(ResourceManagerAdapter.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS),
      REQUEST_REASON);

    var newPolicy = service
      .projects()
      .getIamPolicy(
        String.format("projects/%s", IntegrationTestEnvironment.PROJECT_ID),
        new GetIamPolicyRequest()
          .setOptions(new GetPolicyOptions().setRequestedPolicyVersion(3)))
      .execute();

    assertFalse(
      newPolicy
        .getBindings()
        .stream()
        .anyMatch(b -> b.getCondition() != null && b.getCondition().getTitle().equals("old binding")),
      "old binding has been removed");
    assertTrue(
      newPolicy
        .getBindings()
        .stream()
        .anyMatch(b -> b.getCondition() != null && b.getCondition().getTitle().equals("new binding")),
      "new binding has been added");
    assertTrue(
      newPolicy
        .getBindings()
        .stream()
        .anyMatch(b -> b.getCondition() != null && b.getCondition().getTitle().equals("permanent binding")),
      "permanent binding has been preserved");
  }


  @Test
  public void whenFailIfBindingExistsFlagIsOnAndBindingExists_ThenAddProjectBindingThrowsException() throws Exception {
    var adapter = new ResourceManagerAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    var newBinding = new Binding()
      .setMembers(List.of("serviceAccount:" + IntegrationTestEnvironment.TEMPORARY_ACCESS_USER.email))
      .setRole("roles/browser")
      .setCondition(new Expr()
        .setTitle("temporary binding")
        .setExpression(IamTemporaryAccessConditions.createExpression(
          Instant.now(),
          Instant.now().plus(Duration.ofMinutes(1)))));

    // Add binding -> succeeds as no equivalent binding exists.
    adapter.addProjectIamBinding(
      IntegrationTestEnvironment.PROJECT_ID,
      newBinding,
      EnumSet.of(
        ResourceManagerAdapter.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS,
        ResourceManagerAdapter.IamBindingOptions.FAIL_IF_BINDING_EXISTS),
      "Test");

    // Add same binding again -> fails.
    assertThrows(AlreadyExistsException.class,
      () -> adapter.addProjectIamBinding(
        IntegrationTestEnvironment.PROJECT_ID,
        newBinding,
        EnumSet.of(
          ResourceManagerAdapter.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS,
          ResourceManagerAdapter.IamBindingOptions.FAIL_IF_BINDING_EXISTS),
        "Test"));

    // Add binding again, but without flag -> succeeds
    adapter.addProjectIamBinding(
      IntegrationTestEnvironment.PROJECT_ID,
      newBinding,
      EnumSet.of(ResourceManagerAdapter.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS),
      "Test");
  }

  //---------------------------------------------------------------------
  // Bindings.
  //---------------------------------------------------------------------

  @Test
  public void whenBindingsEquivalentButOrderOfMembersIsDifferent_ThenEqualsIsTrue() {
    var lhs = new Binding()
      .setMembers(List.of("alice", "bob"))
      .setRole("role");

    var rhs = new Binding()
      .setMembers(List.of("bob", "alice"))
      .setRole("role");

    assertTrue(ResourceManagerAdapter.Bindings.equals(lhs, rhs, true));
  }

  @Test
  public void whenBindingsHaveDifferentRole_ThenEqualsIsFalse() {
    var lhs = new Binding()
      .setMembers(List.of("alice", "bob"))
      .setRole("role1");

    var rhs = new Binding()
      .setMembers(List.of("alice", "bob"))
      .setRole("role2");

    assertFalse(ResourceManagerAdapter.Bindings.equals(lhs, rhs, true));
  }

  @Test
  public void whenBindingsHaveDifferentMembers_ThenEqualsIsFalse() {
    var lhs = new Binding()
      .setMembers(List.of("alice", "bob"))
      .setRole("role");

    var rhs = new Binding()
      .setMembers(List.of("alice"))
      .setRole("role");

    assertFalse(ResourceManagerAdapter.Bindings.equals(lhs, rhs, true));
  }

  @Test
  public void whenBindingsHaveDifferentConditions_ThenEqualsIsFalse() {
    var lhs = new Binding()
      .setMembers(List.of("alice"))
      .setRole("role")
      .setCondition(new Expr().setTitle("title"));

    var rhs = new Binding()
      .setMembers(List.of("alice"))
      .setRole("role");

    assertFalse(ResourceManagerAdapter.Bindings.equals(lhs, rhs, true));
  }

  @Test
  public void whenBindingsHaveDifferentConditionsButConditionIsIgnored_ThenEqualsIsTrue() {
    var lhs = new Binding()
      .setMembers(List.of("alice"))
      .setRole("role")
      .setCondition(new Expr().setTitle("title"));

    var rhs = new Binding()
      .setMembers(List.of("alice"))
      .setRole("role");

    assertTrue(ResourceManagerAdapter.Bindings.equals(lhs, rhs, false));
  }

  @Test
  public void whenBindingsHaveDifferentConditionExpressions_ThenEqualsIsFalse() {
    var lhs = new Binding()
      .setMembers(List.of("alice"))
      .setRole("role")
      .setCondition(new Expr()
        .setTitle("title")
        .setExpression("expr1"));

    var rhs = new Binding()
      .setMembers(List.of("alice"))
      .setRole("role")
      .setCondition(new Expr()
        .setTitle("title")
        .setExpression("expr2"));

    assertFalse(ResourceManagerAdapter.Bindings.equals(lhs, rhs, true));
  }

  @Test
  public void whenBindingsHaveDifferentConditionTitles_ThenEqualsIsFalse() {
    var lhs = new Binding()
      .setMembers(List.of("alice"))
      .setRole("role")
      .setCondition(new Expr()
        .setTitle("title1")
        .setExpression("expr"));

    var rhs = new Binding()
      .setMembers(List.of("alice"))
      .setRole("role")
      .setCondition(new Expr()
        .setTitle("title2")
        .setExpression("expr"));

    assertFalse(ResourceManagerAdapter.Bindings.equals(lhs, rhs, true));
  }

  @Test
  public void whenBindingsHaveDifferentConditionDescriptions_ThenEqualsIsFalse() {
    var lhs = new Binding()
      .setMembers(List.of("alice"))
      .setRole("role")
      .setCondition(new Expr()
        .setTitle("title")
        .setExpression("expr"));

    var rhs = new Binding()
      .setMembers(List.of("alice"))
      .setRole("role")
      .setCondition(new Expr()
        .setTitle("title")
        .setExpression("expr")
        .setDescription("description"));

    assertFalse(ResourceManagerAdapter.Bindings.equals(lhs, rhs, true));
  }

  //---------------------------------------------------------------------
  // testIamPermissions.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenTestIamPermissionsThrowsException() {
    var adapter = new ResourceManagerAdapter(IntegrationTestEnvironment.INVALID_CREDENTIAL);

    assertThrows(
      NotAuthenticatedException.class,
      () -> adapter.testIamPermissions(
        IntegrationTestEnvironment.PROJECT_ID,
        List.of("resourcemanager.projects.get")));
  }

  @Test
  public void whenAuthorized_ThenTestIamPermissionsSucceeds() throws Exception {
    var adapter = new ResourceManagerAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    var heldPermissions = adapter.testIamPermissions(
      IntegrationTestEnvironment.PROJECT_ID,
      List.of("resourcemanager.projects.get"));

    assertNotNull(heldPermissions);
    assertEquals(1, heldPermissions.size());
  }
}
