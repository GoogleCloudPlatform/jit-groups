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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.cloudresourcemanager.v3.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.v3.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.data.ProjectId;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Adapter for Resource Manager API.
 */
@SuppressWarnings("SwitchStatementWithTooFewBranches")
@ApplicationScoped
public class ResourceManagerAdapter {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  private static final int MAX_SET_IAM_POLICY_ATTEMPTS = 4;

  private final GoogleCredentials credentials;

  private CloudResourceManager createClient() throws IOException
  {
    try {
      return new CloudResourceManager
        .Builder(
          HttpTransport.newTransport(),
          new GsonFactory(),
          new HttpCredentialsAdapter(this.credentials))
        .setApplicationName(ApplicationVersion.USER_AGENT)
        .build();
    }
    catch (GeneralSecurityException e) {
      throw new IOException("Creating a ResourceManager client failed", e);
    }
  }

  public ResourceManagerAdapter(GoogleCredentials credentials) {
    Preconditions.checkNotNull(credentials, "credentials");

    this.credentials = credentials;
  }

  /** Add an IAM binding using the optimistic concurrency control-mechanism. */
  public void addProjectIamBinding(
    ProjectId projectId,
    Binding binding,
    EnumSet<ResourceManagerAdapter.IamBindingOptions> options,
    String requestReason
  ) throws AccessException, AlreadyExistsException, IOException {
    Preconditions.checkNotNull(projectId, "projectId");
    Preconditions.checkNotNull(binding, "binding");

    try {
      var service = createClient();

      //
      // IAM policies use optimistic concurrency control, so we might need to perform
      // multiple attempts to update the policy.
      //
      for (int attempt = 0; attempt < MAX_SET_IAM_POLICY_ATTEMPTS; attempt++) {
        //
        // Read current version of policy.
        //
        // NB. The API might return a v1 policy even if we
        // request a v3 policy.
        //

        var policy = service
          .projects()
          .getIamPolicy(
            String.format("projects/%s", projectId.id),
            new GetIamPolicyRequest()
              .setOptions(new GetPolicyOptions().setRequestedPolicyVersion(3)))
          .execute();

        //
        // Make sure we're using v3; older versions don't support conditions.
        //
        policy.setVersion(3);

        if (options.contains(IamBindingOptions.FAIL_IF_BINDING_EXISTS)) {
          if (policy.getBindings()
            .stream()
            .anyMatch(b -> Bindings.equals(b, binding, true))) {
            //
            // The exact same binding (incl. condition) exists.
            //
            throw new AlreadyExistsException("The binding already exists");
          }
        }

        if (options.contains(ResourceManagerAdapter.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS)) {
          //
          // Remove existing temporary bindings for the same principal and role.
          //
          // NB. There's a hard limit on how many role bindings in a policy can
          // have the same principal and role. Removing existing bindings
          // helps avoid hitting this limit.
          //
          // NB. This check detects temporary bindings that we created, but it might not
          // detect other temporary bindings (which might use a slightly different
          // condition)
          //
          Predicate<Binding> isObsolete = b -> Bindings.equals(b, binding, false)
            && b.getCondition() != null
            && IamTemporaryAccessConditions.isTemporaryAccessCondition(b.getCondition().getExpression());

          var nonObsoleteBindings =
            policy.getBindings().stream()
              .filter(isObsolete.negate())
              .collect(Collectors.toList());

          policy.getBindings().clear();
          policy.getBindings().addAll(nonObsoleteBindings);
        }

        //
        // Apply change and write new version.
        //
        policy.getBindings().add(binding);

        try {
          var request = service
            .projects()
            .setIamPolicy(
              String.format("projects/%s", projectId),
              new SetIamPolicyRequest().setPolicy((policy)));

          request.getRequestHeaders().set("x-goog-request-reason", requestReason);
          request.execute();

          //
          // Successful update -> quit loop.
          //
          return;
        }
        catch (GoogleJsonResponseException e) {
          if (e.getStatusCode() == 412) {
            //
            // Concurrent modification - back off and retry.
            //
            try {
              Thread.sleep(200);
            }
            catch (InterruptedException ignored) {
            }
          }
          else {
            throw (GoogleJsonResponseException) e.fillInStackTrace();
          }
        }
      }

      throw new AlreadyExistsException(
        "Failed to update IAM bindings due to concurrent modifications");
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(String.format("Denied access to project '%s'", projectId), e);
        default:
          throw (GoogleJsonResponseException) e.fillInStackTrace();
      }
    }
  }

  public List<String> testIamPermissions(
    ProjectId projectId,
    List<String> permissions
  ) throws NotAuthenticatedException, IOException {
    try
    {
      var response = createClient()
        .projects()
        .testIamPermissions(
          String.format("projects/%s", projectId),
          new TestIamPermissionsRequest()
            .setPermissions(permissions))
        .execute();

      return response.getPermissions() != null
        ? response.getPermissions()
        : List.of();
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        default:
          throw (GoogleJsonResponseException) e.fillInStackTrace();
      }
    }
  }

  //---------------------------------------------------------------------
  // Inner classes.
  //---------------------------------------------------------------------

  /**
   * Helper class for using Binding objects.
   */
  public static class Bindings {
    public static boolean equals(Binding lhs, Binding rhs, boolean compareCondition) {
      if (!lhs.getRole().equals(rhs.getRole())) {
        return  false;
      }

      if (!new HashSet<>(lhs.getMembers()).equals(new HashSet<>(rhs.getMembers()))) {
        return false;
      }

      if (compareCondition) {
        if ((lhs.getCondition() == null) != (rhs.getCondition() == null)) {
          return false;
        }

        if (lhs.getCondition() != null && rhs.getCondition() != null) {
          if (!Objects.equals(lhs.getCondition().getExpression(), rhs.getCondition().getExpression())) {
            return false;
          }

          if (!Objects.equals(lhs.getCondition().getTitle(), rhs.getCondition().getTitle())) {
            return false;
          }

          if (!Objects.equals(lhs.getCondition().getDescription(), rhs.getCondition().getDescription())) {
            return false;
          }
        }
      }

      return true;
    }
  }

  public enum IamBindingOptions {
    NONE,

    /** Purge existing temporary bindings for the same principal and role */
    PURGE_EXISTING_TEMPORARY_BINDINGS,

    /** Throw an AlreadyExistsException if an equivalent binding for the same principal and role exists */
    FAIL_IF_BINDING_EXISTS
  }
}
