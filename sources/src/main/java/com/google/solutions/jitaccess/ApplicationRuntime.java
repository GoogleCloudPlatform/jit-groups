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

package com.google.solutions.jitaccess;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.GenericData;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.solutions.jitaccess.apis.ProjectId;
import com.google.solutions.jitaccess.auth.ServiceAccountId;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Set;

/**
 * Runtime environment the application operates in.
 */
public class ApplicationRuntime {
  private static final String CONFIG_IMPERSONATE_SA = "jitaccess.impersonateServiceAccount";
  private static final String CONFIG_DEBUG_MODE = "jitaccess.debug";
  private static final String CONFIG_PROJECT = "jitaccess.project";

  private final @NotNull ProjectId projectId;
  private final @NotNull String projectNumber;
  private final @NotNull GoogleCredentials applicationCredentials;
  private final @NotNull ServiceAccountId applicationPrincipal;
  private final @NotNull ApplicationRuntime.Type type;

  private ApplicationRuntime(
    @NotNull ApplicationRuntime.Type type,
    @NotNull ProjectId projectId,
    @NotNull String projectNumber,
    @NotNull GoogleCredentials applicationCredentials,
    @NotNull ServiceAccountId applicationPrincipal
  ) {
    this.type = type;
    this.projectId = projectId;
    this.projectNumber = projectNumber;
    this.applicationCredentials = applicationCredentials;
    this.applicationPrincipal = applicationPrincipal;
  }

  private static boolean isRunningOnAppEngine() {
    return System.getenv().containsKey("GAE_SERVICE");
  }

  private static boolean isRunningOnCloudRun() {
    return System.getenv().containsKey("K_SERVICE");
  }

  private static boolean isDebugModeEnabled() {
    return Boolean.getBoolean(CONFIG_DEBUG_MODE);
  }

  private static HttpResponse getMetadata() throws IOException {
    var genericUrl = new GenericUrl(
      ComputeEngineCredentials.getMetadataServerUrl() +
        "/computeMetadata/v1/project/?recursive=true");

    var request = new NetHttpTransport()
      .createRequestFactory()
      .buildGetRequest(genericUrl);

    request.setParser(new JsonObjectParser(GsonFactory.getDefaultInstance()));
    request.getHeaders().set("Metadata-Flavor", "Google");
    request.setThrowExceptionOnExecuteError(true);

    try {
      return request.execute();
    }
    catch (Exception exception) {
      throw new IOException(
        "Cannot find the metadata server. This is likely because code is not running on Google Cloud.",
        exception);
    }
  }

  /**
   * Detect runtime based on environment variables and system properties
   * and initialize an ApplicationRuntime instance.
   */
  public static ApplicationRuntime detect(
    @NotNull Set<String> requiredOauthScopes
  ) throws IOException {
    if (isRunningOnAppEngine() || isRunningOnCloudRun()) {
      //
      // Initialize using service account attached to AppEngine or Cloud Run.
      //
      var projectMetadata = getMetadata().parseAs(GenericData.class);
      var projectId = (String) projectMetadata.get("projectId");
      var projectNumber = projectMetadata.get("numericProjectId").toString();

      var defaultCredentials = (ComputeEngineCredentials)GoogleCredentials.getApplicationDefault();
      var applicationPrincipal = ServiceAccountId
        .parse(ServiceAccountId.TYPE + ":" + defaultCredentials.getAccount())
        .orElseThrow(() -> new IllegalArgumentException(
          String.format("'%s' is not a valid service account email address",
            defaultCredentials.getAccount())));

      GoogleCredentials applicationCredentials;
      if (defaultCredentials.getScopes().containsAll(requiredOauthScopes)) {
        //
        // Default credential has all the right scopes, use it as-is.
        //
        applicationCredentials = defaultCredentials;
      }
      else {
        //
        // Extend the set of scopes to include required non-cloud APIs by
        // letting the service account impersonate itself.
        //
        applicationCredentials = ImpersonatedCredentials.create(
          defaultCredentials,
          applicationPrincipal.value(),
          null,
          requiredOauthScopes.stream().toList(),
          0);
      }

      return new ApplicationRuntime(
        isRunningOnAppEngine() ? Type.APPENGINE : Type.CLOUDRUN,
        new ProjectId(projectId),
        projectNumber,
        applicationCredentials,
        applicationPrincipal);
    }
    else if (isDebugModeEnabled()) {
      //
      // Initialize using development settings and credential.
      //
      var defaultCredentials = GoogleCredentials.getApplicationDefault();

      var impersonateServiceAccount = ServiceAccountId.parse(
        ServiceAccountId.TYPE + ":" + System.getProperty(CONFIG_IMPERSONATE_SA));

      GoogleCredentials applicationCredentials;
      ServiceAccountId applicationPrincipal;
      if (impersonateServiceAccount.isPresent()) {
        //
        // Use the application default credentials (ADC) to impersonate a
        // service account. This step is necessary to ensure we have a
        // credential for the right set of scopes, and that we're not running
        // with end-user credentials.
        //
        applicationCredentials = ImpersonatedCredentials.create(
          defaultCredentials,
          impersonateServiceAccount.get().value(),
          null,
          requiredOauthScopes.stream().toList(),
          0);

        //
        // If we lack impersonation permissions, ImpersonatedCredentials
        // will keep retrying until the call timeout expires. The effect
        // is that the application seems hung.
        //
        // To prevent this from happening, force a refresh here. If the
        // refresh fails, fail application startup.
        //
        applicationCredentials.refresh();
        applicationPrincipal = impersonateServiceAccount.get();
      }
      else if (defaultCredentials instanceof ServiceAccountCredentials saCredentials) {
        //
        // Use ADC as-is.
        //
        applicationCredentials = defaultCredentials;
        applicationPrincipal = ServiceAccountId
          .parse(saCredentials.getServiceAccountUser())
          .orElseThrow(() -> new RuntimeException(String.format(
            "The email '%s' is not a valid service account email address",
            saCredentials.getServiceAccountUser())));
      }
      else {
        throw new IllegalArgumentException(String.format(
          "You're using user credentials as application default "
            + "credentials (ADC). Use -D%s=<service-account-email> to impersonate "
            + "a service account during development",
          CONFIG_IMPERSONATE_SA));
      }

      return new ApplicationRuntime(
        Type.DEVELOPMENT,
        new ProjectId(System.getProperty(CONFIG_PROJECT, "dev")),
        "0",
        applicationCredentials,
        applicationPrincipal);
    }
    else {
      throw new RuntimeException(
        "Application is not running on AppEngine or Cloud Run, and debug mode is disabled. Aborting startup");
    }
  }

  /**
   * Project the application is deployed in.
   */
  public @NotNull ProjectId projectId() {
    return this.projectId;
  }

  /**
   * Project the application is deployed in.
   */
  public @NotNull String projectNumber() {
    return this.projectNumber;
  }

  /**
   * Application credentials.
   */
  public @NotNull GoogleCredentials applicationCredentials() {
    return this.applicationCredentials;
  }

  /**
   * Service account used by the application.
   */
  public @NotNull ServiceAccountId applicationPrincipal() {
    return this.applicationPrincipal;
  }

  /**
   * Type of runtime environment the application is running in.
   */
  public @NotNull ApplicationRuntime.Type type() {
    return this.type;
  }

  public enum Type {
    APPENGINE,
    CLOUDRUN,
    DEVELOPMENT
  }
}
