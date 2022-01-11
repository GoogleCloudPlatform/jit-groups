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
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.api.gax.rpc.UnauthenticatedException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.asset.v1.AnalyzeIamPolicyRequest;
import com.google.cloud.asset.v1.AnalyzeIamPolicyResponse;
import com.google.cloud.asset.v1.AssetServiceClient;
import com.google.cloud.asset.v1.AssetServiceSettings;
import com.google.cloud.asset.v1.IamPolicyAnalysisQuery;
import com.google.cloud.resourcemanager.v3.ProjectsClient;
import com.google.cloud.resourcemanager.v3.ProjectsSettings;
import com.google.common.base.Preconditions;
import com.google.protobuf.Timestamp;
import com.google.solutions.iamelevate.core.AccessDeniedException;
import com.google.solutions.iamelevate.core.AccessException;
import com.google.solutions.iamelevate.core.NotAuthenticatedException;

import javax.enterprise.context.RequestScoped;
import java.io.IOException;

/** Adapter for the Asset Inventory API. */
@RequestScoped
public class AssetInventoryAdapter {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  private final GoogleCredentials credentials;

  public AssetInventoryAdapter(GoogleCredentials credentials) throws IOException {
    Preconditions.checkNotNull(credentials, "credentials");

    this.credentials = credentials;
  }

  private AssetServiceClient createClient() throws IOException {
    var clientSettings = AssetServiceSettings.newBuilder()
        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
        .build();

    return AssetServiceClient.create(clientSettings);
  }

  /**
   * Find resources accessible by a user: - resources the user has been directly granted access to -
   * resources which the user has inherited access to - resources which the user can access because
   * of a group membership
   *
   * NB. For group membership resolution to work, the service account must have the right
   * privileges in Cloud Identity/Workspace.
   */
  public AnalyzeIamPolicyResponse.IamPolicyAnalysis analyzeResourcesAccessibleByUser(
      String scope,
      UserId user,
      boolean expandResources)
      throws AccessException, IOException {
    Preconditions.checkNotNull(scope, "scope");
    Preconditions.checkNotNull(user, "user");

    assert (scope.startsWith("organizations/")
        || scope.startsWith("folders/")
        || scope.startsWith("projects/"));

    try (var client = createClient()) {
      var request =
          AnalyzeIamPolicyRequest.newBuilder()
              .setAnalysisQuery(
                  IamPolicyAnalysisQuery.newBuilder()
                      .setScope(scope)
                      .setIdentitySelector(
                          IamPolicyAnalysisQuery.IdentitySelector.newBuilder()
                              .setIdentity("user:" + user.getEmail()))
                      .setOptions(
                          IamPolicyAnalysisQuery.Options.newBuilder()
                              .setExpandResources(expandResources))
                      .setConditionContext(
                          IamPolicyAnalysisQuery.ConditionContext.newBuilder()
                              .setAccessTime(
                                  Timestamp.newBuilder()
                                      .setSeconds(System.currentTimeMillis() / 1000))
                              .build()))
              .build();

      return client.analyzeIamPolicy(request).getMainAnalysis();
    } catch (UnauthenticatedException e) {
      throw new NotAuthenticatedException("Not authenticated", e);
    } catch (PermissionDeniedException e) {
      throw new AccessDeniedException(String.format("Denied access to scope '%s'", scope), e);
    }
  }
}
