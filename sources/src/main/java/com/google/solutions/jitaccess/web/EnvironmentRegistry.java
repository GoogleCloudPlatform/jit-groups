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

package com.google.solutions.jitaccess.web;

import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.apis.clients.HttpTransport;
import com.google.solutions.jitaccess.apis.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.auth.GroupMapping;
import com.google.solutions.jitaccess.catalog.policy.PolicyDocumentSource;
import com.google.solutions.jitaccess.catalog.provisioning.Environment;
import com.google.solutions.jitaccess.catalog.provisioning.Provisioner;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Executor;

/**
 * Registry for environments and their associated provisioners.
 */
public class EnvironmentRegistry {
  private final @NotNull Collection<Environment> environments;

  public EnvironmentRegistry(
    @NotNull Collection<EnvironmentConfiguration> environments,
    @NotNull GroupMapping groupMapping,
    @NotNull CloudIdentityGroupsClient groupsClient,
    @NotNull Executor executor,
    @NotNull Options options,
    @NotNull Logger logger
  ) {
    //
    // Initialize provisioners for each environment.
    //
    this.environments = environments.stream()
      .map(cfg -> {
        //
        // Create a CRM client that uses this environment's credential
        // (as opposed to the application credential).
        //
        var crmClient = new ResourceManagerClient(
          cfg.resourceCredentials(),
          options.httpTransportOptions());

        var provisioner = new Provisioner(
          cfg.name(),
          groupMapping,
          groupsClient,
          crmClient,
          executor,
          logger);

        return (Environment) new Environment(
          cfg.name(),
          cfg.description(),
          provisioner,
          options.cacheDuration()
        ) {
          @Override
          public PolicyDocumentSource loadPolicy() {
            return cfg.loadPolicy();
          }
        };
      })
      .toList();
  }

  public @NotNull Collection<Environment> environments() {
    return this.environments;
  }

  public record Options(
    @NotNull Duration cacheDuration,
    @NotNull HttpTransport.Options httpTransportOptions
  ) {}
}
