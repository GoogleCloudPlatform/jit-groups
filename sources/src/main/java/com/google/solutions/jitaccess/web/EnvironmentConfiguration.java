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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.solutions.jitaccess.apis.clients.HttpTransport;
import com.google.solutions.jitaccess.apis.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.apis.clients.SecretManagerClient;
import com.google.solutions.jitaccess.catalog.auth.ServiceAccountId;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.catalog.policy.Policy;
import com.google.solutions.jitaccess.catalog.policy.PolicyDocument;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Configuration for an environment.
 *
 * @param name                name of the environment
 * @param resourceCredentials environment-specific credentials to use for provisioning
 * @param loadPolicy          callback for (lazily) loading the policy
 */
class EnvironmentConfiguration {

  private final @NotNull String name;
  private final @NotNull GoogleCredentials resourceCredentials;
  private final @NotNull Supplier<EnvironmentPolicy> loadPolicy;

  EnvironmentConfiguration(
    @NotNull String name,
    @NotNull GoogleCredentials resourceCredentials,
    @NotNull Supplier<EnvironmentPolicy> loadPolicy
  ) {
    this.name = name;
    this.resourceCredentials = resourceCredentials;
    this.loadPolicy = loadPolicy;
  }

  public String name() {
    return name;
  }

  public GoogleCredentials resourceCredentials() {
    return resourceCredentials;
  }

  public EnvironmentPolicy loadPolicy() {
    return this.loadPolicy.get();
  }

  static @NotNull EnvironmentConfiguration forFile(
    @NotNull String filePath,
    @NotNull GoogleCredentials applicationCredentials
  ) {
    final File file;
    String environmentName;
    try {
      file = new File(new URL(filePath).toURI());
      environmentName = file.getName();

      if (environmentName.indexOf('.') > 0) {
        //
        // Remove suffix (like .yaml)
        //
        environmentName = environmentName.substring(0, environmentName.lastIndexOf('.'));
      }
    }
    catch (URISyntaxException | MalformedURLException e) {
      throw new IllegalArgumentException(
        String.format("The file path '%s' is malformed", filePath),
        e);
    }

    return new EnvironmentConfiguration(
      environmentName,
      applicationCredentials,
      () -> {
        try {
          return PolicyDocument.fromFile(file).policy();
        }
        catch (Exception e) {
          throw new UncheckedExecutionException(e);
        }
      }
    );
  }

  static @NotNull EnvironmentConfiguration forServiceAccount(
    @NotNull ServiceAccountId serviceAccountId,
    @NotNull UserId applicationPrincipal,
    @NotNull GoogleCredentials applicationCredentials,
    @NotNull HttpTransport.Options httpOptions
  ) {
    //
    // Derive the name of the environment from the service
    // account (jit-ENVIRONMENT@...). This approach has the following
    // advantages
    //
    // - It makes the association between an environment and
    //   a service account fairly static. This is good because
    //   environment shouldn't be renamed, and shouldn't be
    //   repurposed.
    // - It enforces a naming convention among the service
    //   accounts.
    // - It ensures that the environment name is alphanumeric
    //   and satisfies the criteria for valid environment names.
    //
    if (!serviceAccountId.id.startsWith(ApplicationConfiguration.ENVIRONMENT_SERVICE_ACCOUNT_PREFIX)) {
      throw new IllegalArgumentException(
        "Service accounts must use the prefix " +
        ApplicationConfiguration.ENVIRONMENT_SERVICE_ACCOUNT_PREFIX);
    }

    var environmentName = serviceAccountId.id
      .substring(ApplicationConfiguration.ENVIRONMENT_SERVICE_ACCOUNT_PREFIX.length());

    //
    // Impersonate the service account and use it for:
    //
    //  - Loading the policy from Secret Manager
    //  - Provisioning access
    //
    var environmentCredentials = ImpersonatedCredentials.create(
      applicationCredentials,
      serviceAccountId.value(),
      null,
      List.of(ResourceManagerClient.OAUTH_SCOPE), // No other scopes needed.
      0);

    //
    // Load policy from secret manager using the environment-specific
    // credentials (not the application credentials!).
    //
    // The secret path is based on a convention and can't be customized.
    //
    var secretPath = String.format(
      "projects/%s/secrets/jit-%s/versions/latest",
      serviceAccountId.projectId,
      environmentName);

    return new EnvironmentConfiguration(
      environmentName,
      environmentCredentials,
      () -> {
        //
        // If we lack impersonation permissions, ImpersonatedCredentials
        // will keep retrying until the call timeout expires. The effect
        // is that the application seems hung.
        //
        // To prevent this from happening, force a refresh here.
        //
        try {
          environmentCredentials.refresh();
        }
        catch (Exception e) {
          throw new RuntimeException(
            String.format(
              "Impersonating service account '%s' of environment '%s' failed, possibly caused " +
                "by insufficient IAM permissions. Make sure that the service account '%s' has " +
                "the roles/iam.serviceAccountTokenCreator role on '%s'.",
              serviceAccountId.email(),
              environmentName,
              applicationPrincipal,
              serviceAccountId.email()));
        }

        try {
          var secretClient = new SecretManagerClient(
            environmentCredentials,
            httpOptions);

          return PolicyDocument
            .fromString(
              secretClient.accessSecret(secretPath),
              new Policy.Metadata(secretPath, Instant.now()))
            .policy();
        }
        catch (Exception e) {
          throw new UncheckedExecutionException(e);
        }
      });
  }
}