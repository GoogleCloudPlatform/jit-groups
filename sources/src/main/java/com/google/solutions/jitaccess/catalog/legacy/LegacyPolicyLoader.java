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

package com.google.solutions.jitaccess.catalog.legacy;

import com.google.api.services.cloudasset.v1.model.Binding;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.apis.clients.AssetInventoryClient;
import com.google.solutions.jitaccess.apis.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.apis.clients.ResourceNotFoundException;
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.catalog.policy.Policy;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class LegacyPolicyLoader {
  private final @NotNull Supplier<ResourceManagerClient> produceResourceManagerClient;
  private final @NotNull Supplier<AssetInventoryClient> produceAssetInventoryClient;

  public LegacyPolicyLoader(
    @NotNull Supplier<ResourceManagerClient> produceResourceManagerClient,
    @NotNull Supplier<AssetInventoryClient> produceAssetInventoryClient
  ) {
    this.produceResourceManagerClient = produceResourceManagerClient;
    this.produceAssetInventoryClient = produceAssetInventoryClient;
  }

  private Collection<Binding> getEffectiveIamPolicies(
    @NotNull String scope,
    @NotNull String resourceId
  ) throws AccessException, IOException {
    return this.produceAssetInventoryClient.get()
      .getEffectiveIamPolicies(scope, resourceId)
      .stream()

      // All bindings, across all resources in the ancestry.
      .flatMap(p -> p.getPolicy().getBindings().stream())
      .toList();
  }

  public @NotNull LegacyPolicy load(
    @NotNull String projectQuery,
    @NotNull String scope,
    @NotNull Duration activationTimeout,
    @NotNull String justificationPattern,
    @NotNull String justificationHint,
    @NotNull Logger logger
  ) throws AccessException, IOException {

    var policy = new LegacyPolicy(
      activationTimeout,
      justificationPattern,
      justificationHint,
      getEffectiveIamPolicies(scope, scope),
      new Policy.Metadata(
        String.format("IAM policies in %s", scope),
        Instant.now()));

    for (var project : this
      .produceResourceManagerClient.get()
      .searchProjects(projectQuery)) {

      policy.add(
        project,
        () -> {
          try {
            return getEffectiveIamPolicies(scope, "projects/" + project.getProjectId());
          }
          catch (ResourceNotFoundException e) {
            //
            // Project not in scope.
            //
            return List.of();
          }
          catch (Exception e) {
            throw new UncheckedExecutionException(e);
          }
        },
        logger);
    }

    return policy;
  }
}
