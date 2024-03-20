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

package com.google.solutions.jitaccess.core.clients;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudasset.v1.model.IamPolicyAnalysis;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.UserId;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Adapter for the Policy Analyzer API.
 *
 * NB. This subset of the Asset Inventory API is subject to quotas that
 * depend on the presence of an SCC subscription.
 */
@Singleton
public class PolicyAnalyzerClient extends AssetInventoryClient {
  public PolicyAnalyzerClient(
    @NotNull GoogleCredentials credentials,
    @NotNull HttpTransport.Options httpOptions
  ) {
    super(credentials, httpOptions);
  }

  /**
   * Find resources that a user can access, considering inherited IAM bindings and group memberships.
   *
   * NB. For group membership resolution to work, the service account must have the right
   * privileges in Cloud Identity/Workspace.
   */
  public IamPolicyAnalysis findAccessibleResourcesByUser(
    @NotNull String scope,
    @NotNull UserId user,
    @NotNull Optional<String> permission,
    @NotNull Optional<String> fullResourceName,
    boolean expandResources
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(scope, "scope");
    Preconditions.checkNotNull(user, "user");

    assert permission.isEmpty() || permission.get().contains("");
    assert fullResourceName.isEmpty() || fullResourceName.get().startsWith("//");

    assert (scope.startsWith("organizations/")
      || scope.startsWith("folders/")
      || scope.startsWith("projects/"));

    try {
      var request = createClient().v1()
        .analyzeIamPolicy(scope)
        .setAnalysisQueryIdentitySelectorIdentity("user:" + user.email)
        .setAnalysisQueryOptionsExpandResources(expandResources)
        .setAnalysisQueryConditionContextAccessTime(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
        .setExecutionTimeout(String.format("%ds", this.httpOptions.readTimeout().toSeconds()));

      if (fullResourceName.isPresent()) {
        request.setAnalysisQueryResourceSelectorFullResourceName(fullResourceName.get());
      }

      if (permission.isPresent()) {
        request.setAnalysisQueryAccessSelectorPermissions(List.of(permission.get()));
      }

      return request
        .execute()
        .getMainAnalysis();
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 400:
          //
          // NB. The API returns 400 if the resource doesn't exist. Convert to empty result.
          //
          return new IamPolicyAnalysis();
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(String.format("Denied access to scope '%s'", scope), e);
        case 429:
          throw new QuotaExceededException(
            "Exceeded quota for AnalyzeIamPolicy API requests. Consider increasing the request " +
              "quota in the application project or reconfigure the application to use the " +
              "AssetInventory catalog instead.",
            e);
        default:
          throw (GoogleJsonResponseException) e.fillInStackTrace();
      }
    }
  }

  /**
   * Find users or groups that have been (conditionally) granted a given role on a given resource.
   */
  public IamPolicyAnalysis findPermissionedPrincipalsByResource(
    @NotNull String scope,
    @NotNull String fullResourceName,
    @NotNull String role
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(scope, "scope");
    Preconditions.checkNotNull(fullResourceName, "fullResourceName");
    Preconditions.checkNotNull(role, "role");

    assert (scope.startsWith("organizations/")
      || scope.startsWith("folders/")
      || scope.startsWith("projects/"));

    try {
      return createClient().v1()
        .analyzeIamPolicy(scope)
        .setAnalysisQueryResourceSelectorFullResourceName(fullResourceName)
        .setAnalysisQueryAccessSelectorRoles(List.of(role))
        .setAnalysisQueryConditionContextAccessTime(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
        .setAnalysisQueryOptionsExpandGroups(true)
        .setExecutionTimeout(String.format("%ds", this.httpOptions.readTimeout().toSeconds()))
        .execute()
        .getMainAnalysis();
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(String.format("Denied access to scope '%s': %s", scope, e.getMessage()), e);
        case 429:
          throw new QuotaExceededException(
            "Exceeded quota for AnalyzeIamPolicy API requests. Consider increasing the request " +
              "quota in the application project or reconfigure the application to use the " +
              "AssetInventory catalog instead.",
            e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }
}
