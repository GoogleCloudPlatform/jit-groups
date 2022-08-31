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

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.AbortedException;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.api.gax.rpc.UnauthenticatedException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.resourcemanager.v3.ProjectName;
import com.google.cloud.resourcemanager.v3.ProjectsClient;
import com.google.cloud.resourcemanager.v3.ProjectsSettings;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.iam.v1.Binding;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.GetPolicyOptions;
import com.google.iam.v1.SetIamPolicyRequest;
import com.google.solutions.jitaccess.core.*;

import javax.enterprise.context.RequestScoped;
import java.io.IOException;
import java.util.EnumSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Adapter for Resource Manager API. */
@RequestScoped
public class ResourceManagerAdapter {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  private static final int MAX_SET_IAM_POLICY_ATTEMPTS = 4;

  private final GoogleCredentials credentials;

  public ResourceManagerAdapter(GoogleCredentials credentials) {
    Preconditions.checkNotNull(credentials, "credentials");

    this.credentials = credentials;
  }

  private ProjectsClient createClient(String requestReason) throws IOException {
    var clientSettings = ProjectsSettings.newBuilder()
        .setCredentialsProvider(FixedCredentialsProvider.create(this.credentials))
        .setHeaderProvider(FixedHeaderProvider.create(
              ImmutableMap.of(
                  "user-agent", ApplicationVersion.USER_AGENT,
                  "x-goog-request-reason", requestReason)))
        .build();

    return ProjectsClient.create(clientSettings);
  }

  /** Add an IAM binding using the optimistic concurrency control-mechanism. */
  public void addIamBinding(
      ProjectName projectId,
      Binding binding,
      EnumSet<ResourceManagerAdapter.IamBindingOptions> options,
      String requestReason)
      throws AccessException, AlreadyExistsException, IOException {
    Preconditions.checkNotNull(projectId, "projectId");
    Preconditions.checkNotNull(binding, "binding");

    try (var client = createClient(requestReason)) {
      for (int attempt = 0; attempt < MAX_SET_IAM_POLICY_ATTEMPTS; attempt++) {
        //
        // Read current version of policy.
        //
        // NB. The API might return a v1 policy even if we
        // request a v3 policy.
        //

        var request =
            GetIamPolicyRequest.newBuilder()
                .setResource(projectId.toString())
                .setOptions(GetPolicyOptions.newBuilder()
                        .setRequestedPolicyVersion(3)
                        .build())
                .build();
        var policy = client.getIamPolicy(request).toBuilder();
        policy.setVersion(3);

        if (options.contains(
            ResourceManagerAdapter.IamBindingOptions
                .REPLACE_BINDINGS_FOR_SAME_PRINCIPAL_AND_ROLE)) {
          //
          // Remove bindings for the same principal and role.
          //
          // NB. There's a hard limit on how many role bindings in a policy can
          // have the same principal and role. Removing existing bindings
          // helps avoid hitting this limit.
          //
          Predicate<Binding> isObsolete = b ->
              b.getRole().equals(binding.getRole())
                  && b.getMembersList().equals(binding.getMembersList())
                  && IamConditions.isTemporaryConditionClause(b.getCondition().getExpression());

          var nonObsoleteBindings =
              policy.getBindingsList().stream()
                  .filter(isObsolete.negate())
                  .collect(Collectors.toList());

          policy.clearBindings();
          policy.addAllBindings(nonObsoleteBindings);
        }

        //
        // Apply change and write new version.
        //
        policy.addBindings(binding);

        try {
          client.setIamPolicy(
              SetIamPolicyRequest.newBuilder()
                  .setResource(projectId.toString())
                  .setPolicy(policy.build())
                  .build());

          //
          // Successful update -> quit loop.
          //
          return;
        } catch (AbortedException e)
        {
          //
          // Concurrent modification - back off and retry.
          //
          try {
            Thread.sleep(200);
          } catch (InterruptedException ignored) {
          }
        }
      }

      throw new AlreadyExistsException(
          "Failed to update IAM bindings due to concurrent modifications");
    } catch (UnauthenticatedException e) {
      throw new NotAuthenticatedException("Not authenticated", e);
    } catch (PermissionDeniedException e) {
      throw new AccessDeniedException(String.format("Denied access to project '%s'", projectId), e);
    }
  }

  public enum IamBindingOptions {
    NONE,
    REPLACE_BINDINGS_FOR_SAME_PRINCIPAL_AND_ROLE
  }
}
