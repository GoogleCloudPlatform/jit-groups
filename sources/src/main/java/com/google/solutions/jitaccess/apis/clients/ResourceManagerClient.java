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
import com.google.api.services.cloudresourcemanager.v3.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.auth.Credentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.ResourceId;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Adapter for Resource Manager API.
 */
public class ResourceManagerClient extends AbstractIamClient {
  public static final String SERVICE = "cloudresourcemanager.googleapis.com";
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  private static final int SEARCH_PROJECTS_PAGE_SIZE = 1000;

  private final @NotNull Credentials credentials;
  private final @NotNull HttpTransport.Options httpOptions;

  @Override
  protected @NotNull CloudResourceManager createClient() throws IOException
  {
    return Builders
      .newBuilder(CloudResourceManager.Builder::new, this.credentials, this.httpOptions)
      .build();
  }

  public ResourceManagerClient(
    @NotNull Credentials credentials,
    @NotNull HttpTransport.Options httpOptions
  ) {
    Preconditions.checkNotNull(credentials, "credentials");
    Preconditions.checkNotNull(httpOptions, "httpOptions");

    this.credentials = credentials;
    this.httpOptions = httpOptions;
  }

  /**
   * Modify a project, folder, or organization's
   * IAM policy using optimistic concurrency control.
   */
  public void modifyIamPolicy(
    @NotNull ResourceId id,
    @NotNull Consumer<Policy> modify,
    @NotNull String requestReason
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(id, "projectId");
    Preconditions.checkArgument(SERVICE.equals(id.service()), "Resource must be a CRM resource");
    Preconditions.checkNotNull(modify, "modify");

    modifyIamPolicy(
      String.format("v3/%s", id.path()),
      modify,
      requestReason);
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