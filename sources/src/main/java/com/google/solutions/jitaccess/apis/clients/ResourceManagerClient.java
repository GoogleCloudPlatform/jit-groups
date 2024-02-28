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


import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.cloudresourcemanager.v3.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.v3.model.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.ApplicationVersion;
import com.google.solutions.jitaccess.apis.ProjectId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Adapter for Resource Manager API.
 */
public class ResourceManagerClient {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  private static final int MAX_SET_IAM_POLICY_ATTEMPTS = 4;

  private static final int SEARCH_PROJECTS_PAGE_SIZE = 1000;

  private final @NotNull GoogleCredentials credentials;
  private final @NotNull HttpTransport.Options httpOptions;

  private @NotNull CloudResourceManager createClient() throws IOException
  {
    try {
      return new CloudResourceManager
        .Builder(
        HttpTransport.newTransport(),
        new GsonFactory(),
        HttpTransport.newAuthenticatingRequestInitializer(this.credentials, this.httpOptions))
        .setApplicationName(ApplicationVersion.USER_AGENT)
        .build();
    }
    catch (GeneralSecurityException e) {
      throw new IOException("Creating a ResourceManager client failed", e);
    }
  }

  private static boolean isRoleNotGrantableErrorMessage(@Nullable String message)
  {
    return message != null &&
      (message.contains("not supported") || message.contains("does not exist"));
  }

  public ResourceManagerClient(
    @NotNull GoogleCredentials credentials,
    @NotNull HttpTransport.Options httpOptions
  ) {
    Preconditions.checkNotNull(credentials, "credentials");
    Preconditions.checkNotNull(httpOptions, "httpOptions");

    this.credentials = credentials;
    this.httpOptions = httpOptions;
  }

  /**
   * Modify a project IAM policy using the optimistic concurrency control-mechanism.
   */
  public void modifyIamPolicy(
    @NotNull ProjectId projectId,
    @NotNull Consumer<Policy> modify,
    @NotNull String requestReason
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(projectId, "projectId");
    Preconditions.checkNotNull(modify, "modify");

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
            String.format("projects/%s", projectId.id()),
            new GetIamPolicyRequest()
              .setOptions(new GetPolicyOptions().setRequestedPolicyVersion(3)))
          .execute();

        //
        // Make sure we're using v3; older versions don't support conditions.
        //
        policy.setVersion(3);

        //
        // Apply changes.
        //
        modify.accept(policy);

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
        case 400:
          //
          // One possible reason for an INVALID_ARGUMENT error is that we've tried
          // to grant a role on a project that cannot be granted on a project at all.
          // If that's the case, provide a more descriptive error message.
          //
          if (e.getDetails() != null &&
            e.getDetails().getErrors() != null &&
            e.getDetails().getErrors().size() > 0 &&
            isRoleNotGrantableErrorMessage(e.getDetails().getErrors().get(0).getMessage())) {
            throw new AccessDeniedException(
              "Modifying the IAM policy failed because one of the roles isn't grantable on projects");
          }
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(String.format("Denied access to project '%s'", projectId), e);
        default:
          throw (GoogleJsonResponseException) e.fillInStackTrace();
      }
    }
  }

  /**
   * Search for projects.
   */
  public @NotNull Collection<Project> searchProjects(
    @NotNull String query
  ) throws NotAuthenticatedException, IOException {
    try {
      var client = createClient();

      var response = client
        .projects()
        .search()
        .setQuery(query)
        .setPageSize(SEARCH_PROJECTS_PAGE_SIZE)
        .execute();

      ArrayList<Project> allProjects = new ArrayList<>();
      if(response.getProjects() != null) {
        allProjects.addAll(response.getProjects());
      }

      while(response.getNextPageToken() != null
        && !response.getNextPageToken().isEmpty()
        && response.getProjects() !=null
        && response.getProjects().size() >= SEARCH_PROJECTS_PAGE_SIZE) {
        response = client
          .projects()
          .search()
          .setQuery(query)
          .setPageToken(response.getNextPageToken())
          .setPageSize(SEARCH_PROJECTS_PAGE_SIZE)
          .execute();

        if(response.getProjects() != null) {
          allProjects.addAll(response.getProjects());
        }
      }

      return allProjects;
    }
    catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 401) {
        throw new NotAuthenticatedException("Not authenticated", e);
      }
      throw (GoogleJsonResponseException) e.fillInStackTrace();
    }
  }
}