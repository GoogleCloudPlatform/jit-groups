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

package com.google.solutions.jitaccess.apis.clients;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.services.iam.v1.Iam;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.IamRole;
import com.google.solutions.jitaccess.apis.ResourceId;
import com.google.solutions.jitaccess.util.Coalesce;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Client for IAM API.
 */
@Singleton
public class IamClient {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  private final @NotNull GoogleCredentials credentials;
  private final @NotNull HttpTransport.Options httpOptions;
  private final @NotNull Options options;

  private @NotNull Iam createClient() throws IOException
  {
    return Builders
      .newBuilder(Iam.Builder::new, this.credentials, this.httpOptions)
      .build();
  }

  public IamClient(
    @NotNull Options options,
    @NotNull GoogleCredentials credentials,
    @NotNull HttpTransport.Options httpOptions
  )  {
    Preconditions.checkNotNull(options, "options");
    Preconditions.checkNotNull(credentials, "credentials");
    Preconditions.checkNotNull(httpOptions, "httpOptions");

    this.options = options;
    this.httpOptions = httpOptions;
    this.credentials = credentials;
  }

  /**
   * List all predefined roles.
   */
  public @NotNull Collection<IamRole> listPredefinedRoles(
  ) throws AccessException, IOException {
    try {
      var request = createClient()
        .roles()
        .list()
        .setPageSize(Math.min(1000, this.options.defaultPageSize()));

      var roles = new LinkedList<IamRole>();

      ListRolesResponse response;
      do {
        response = request.execute();

        Coalesce
          .emptyIfNull(response.getRoles())
          .stream()
          .forEach(r -> roles.add(new IamRole(r.getName())));

        request.setPageToken(response.getNextPageToken());
      } while (response.getNextPageToken() != null);

      return roles;
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(
            String.format("Access to IAM API is denied: %s", e.getMessage()), e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }

  /**
   * List all roles (predefined and custom) that can be granted on a given resource.
   */
  public @NotNull Collection<IamRole> listGrantableRoles(
    @NotNull ResourceId resourceId
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(resourceId, "resourceId");
    Preconditions.checkArgument(
      ResourceManagerClient.SERVICE.equals(resourceId.service()),
      "Resource must be a CRM resource");

    try {
      var requestBody = new QueryGrantableRolesRequest()
        .setFullResourceName("//cloudresourcemanager.googleapis.com/" + resourceId.path())
        .setView("BASIC")
        .setPageSize(Math.min(2000, this.options.defaultPageSize()));
      var request = createClient()
        .roles()
        .queryGrantableRoles(requestBody);

      var roles = new LinkedList<IamRole>();

      QueryGrantableRolesResponse response;
      do {
        response = request.execute();

        Coalesce
          .emptyIfNull(response.getRoles())
          .stream()
          .forEach(r -> roles.add(new IamRole(r.getName())));

        requestBody.setPageToken(response.getNextPageToken());
      } while (response.getNextPageToken() != null);

      return roles;
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(
            String.format("Access to resource '%s' is denied: %s", resourceId, e.getMessage()), e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }

  /**
   * Lint policies and return any errors or warnings encountered.
   *
   * @return List of issues, or empty if the expression is ok.
   */
  public Collection<LintResult> lintIamCondition(
    @NotNull ResourceId resourceId,
    @NotNull String expression
  ) throws IOException {
    //
    // NB. The call doesn't require authorization, and the resource
    // doesn't need to exist either.
    //
    var response = createClient()
      .iamPolicies()
      .lintPolicy(new LintPolicyRequest()
        .setFullResourceName("//cloudresourcemanager.googleapis.com/" + resourceId.path())
        .setCondition(new Expr()
          .setExpression(expression)))
      .execute();

    //
    // The result contains an item for each check, even for those that
    // succeeded, so we must filter out NOTICE-level items.
    //
    return Coalesce
      .emptyIfNull(response.getLintResults())
      .stream()
      .filter(r -> !"NOTICE".equals(r.getSeverity()))
      .toList();
  }

  public record Options(
    int defaultPageSize
  ) {
    public Options {
      Preconditions.checkArgument(defaultPageSize > 0, "Page size must be positive");
    }
  }
}
