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

package com.google.solutions.jitaccess.web;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.GenericData;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.solutions.jitaccess.core.ApplicationVersion;
import com.google.solutions.jitaccess.core.adapters.AssetInventoryAdapter;
import com.google.solutions.jitaccess.core.adapters.IamCredentialsAdapter;
import com.google.solutions.jitaccess.core.adapters.LogAdapter;
import com.google.solutions.jitaccess.core.adapters.ResourceManagerAdapter;
import com.google.solutions.jitaccess.core.data.UserId;
import com.google.solutions.jitaccess.core.services.NotificationService;
import com.google.solutions.jitaccess.core.services.RoleActivationService;
import com.google.solutions.jitaccess.core.services.RoleDiscoveryService;
import com.google.solutions.jitaccess.core.services.ActivationTokenService;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides access to runtime configuration (AppEngine, local). To be injected using CDI.
 */
@ApplicationScoped
public class RuntimeEnvironment {
  private static final String CONFIG_IMPERSONATE_SA = "jitaccess.impersonateServiceAccount";
  private static final String CONFIG_DEBUG_MODE = "jitaccess.debug";

  private final boolean developmentMode;
  private final String projectId;
  private final String projectNumber;
  private final UserId applicationPrincipal;
  private final GoogleCredentials applicationCredentials;

  private static HttpResponse getMetadata(String path) throws IOException {
    GenericUrl genericUrl = new GenericUrl(ComputeEngineCredentials.getMetadataServerUrl() + path);
    HttpRequest request = new NetHttpTransport().createRequestFactory().buildGetRequest(genericUrl);

    request.setParser(new JsonObjectParser(GsonFactory.getDefaultInstance()));
    request.getHeaders().set("Metadata-Flavor", "Google");
    request.setThrowExceptionOnExecuteError(true);

    try {
      return request.execute();
    }
    catch (UnknownHostException exception) {
      throw new IOException(
        "Cannot find the metadata server. This is likely because code is not running on Google Cloud.",
        exception);
    }
  }

  private static String getConfigurationOption(String key, String defaultValue) {
    var value = System.getenv(key);
    if (value != null && !value.isEmpty()) {
      return value.trim();
    }
    else if (defaultValue != null) {
      return defaultValue;
    }
    else {
      throw new RuntimeException(String.format("Missing configuration '%s'", key));
    }
  }

  private boolean isRunningOnAppEngine() {
    return System.getenv().containsKey("GAE_SERVICE");
  }

  // -------------------------------------------------------------------------
  // Public methods.
  // -------------------------------------------------------------------------

  public RuntimeEnvironment() {
    //
    // Create a log adapter. We can't rely on injection as the adapter
    // is request-scoped.
    //
    var logAdapter = new LogAdapter();

    if (isRunningOnAppEngine()) {
      //
      // Running on AppEngine.
      //
      try {
        GenericData projectMetadata =
          getMetadata("/computeMetadata/v1/project/?recursive=true").parseAs(GenericData.class);

        this.developmentMode = false;
        this.projectId = (String) projectMetadata.get("projectId");
        this.projectNumber = projectMetadata.get("numericProjectId").toString();

        this.applicationCredentials = GoogleCredentials.getApplicationDefault();
        this.applicationPrincipal = new UserId(((ComputeEngineCredentials) this.applicationCredentials).getAccount());

        logAdapter
          .newInfoEntry(
            LogEvents.RUNTIME_STARTUP,
            String.format("Running in project %s (%s) as %s, version %s",
              this.projectId,
              this.projectNumber,
              this.applicationPrincipal,
              ApplicationVersion.VERSION_STRING))
          .write();
      }
      catch (IOException e) {
        logAdapter
          .newErrorEntry(
            LogEvents.RUNTIME_STARTUP,
            "Failed to lookup instance metadata", e)
          .write();
        throw new RuntimeException("Failed to initialize runtime environment", e);
      }
    }
    else if (isDebugModeEnabled()) {
      //
      // Running in debug mode.
      //
      this.developmentMode = true;
      this.projectId = "dev";
      this.projectNumber = "0";

      try {
        var defaultCredentials = GoogleCredentials.getApplicationDefault();

        var impersonateServiceAccount = System.getProperty(CONFIG_IMPERSONATE_SA);
        if (impersonateServiceAccount != null && !impersonateServiceAccount.isEmpty()) {
          //
          // Use the application default credentials (ADC) to impersonate a
          // service account. This can be used when using user credentials as ADC.
          //
          this.applicationCredentials = ImpersonatedCredentials.create(
            defaultCredentials,
            impersonateServiceAccount,
            null,
            Stream.of(
              ResourceManagerAdapter.OAUTH_SCOPE,
              AssetInventoryAdapter.OAUTH_SCOPE,
              IamCredentialsAdapter.OAUTH_SCOPE)
              .distinct()
              .collect(Collectors.toList()),
            0);

          //
          // If we lack impersonation permissions, ImpersonatedCredentials
          // will keep retrying until the call timeout expires. The effect
          // is that the application seems hung.
          //
          // To prevent this from happening, force a refresh here. If the
          // refresh fails, fail application startup.
          //
          this.applicationCredentials.refresh();
          this.applicationPrincipal = new UserId(impersonateServiceAccount);
        }
        else if (defaultCredentials instanceof ServiceAccountCredentials) {
          //
          // Use ADC as-is.
          //
          this.applicationCredentials = defaultCredentials;
          this.applicationPrincipal = new UserId(
              ((ServiceAccountCredentials) this.applicationCredentials).getServiceAccountUser());
        }
        else {
          throw new RuntimeException(String.format(
            "You're using user credentials as application default "
              + "credentials (ADC). Use -D%s=<service-account-email> to impersonate "
              + "a service account during development",
            CONFIG_IMPERSONATE_SA));
        }
      }
      catch (IOException e) {
        throw new RuntimeException("Failed to lookup application credentials", e);
      }

      logAdapter
        .newWarningEntry(
          LogEvents.RUNTIME_STARTUP,
          String.format("Running in development mode as %s", this.applicationPrincipal))
        .write();
    }
    else {
      throw new RuntimeException(
        "Application is not running on AppEngine, and debug mode is disabled. Aborting startup");
    }
  }

  public boolean isDebugModeEnabled() {
    return Boolean.getBoolean(CONFIG_DEBUG_MODE);
  }

  public String getScheme() {
    return isRunningOnAppEngine() ? "https" : "http";
  }

  public String getProjectId() {
    return projectId;
  }

  public String getProjectNumber() {
    return projectNumber;
  }

  public UserId getApplicationPrincipal() {
    return applicationPrincipal;
  }

  // -------------------------------------------------------------------------
  // Producer methods.
  // -------------------------------------------------------------------------

  @Produces
  public GoogleCredentials getApplicationCredentials() {
    return applicationCredentials;
  }

  @Produces
  public RoleDiscoveryService.Options getRoleDiscoveryServiceOptions() {
    return new RoleDiscoveryService.Options(
      getConfigurationOption(
        "RESOURCE_SCOPE",
        "projects/" + getConfigurationOption("GOOGLE_CLOUD_PROJECT", null)));
  }

  @Produces
  public RoleActivationService.Options getRoleActivationServiceOptions() {
    return new RoleActivationService.Options(
      getConfigurationOption("JUSTIFICATION_HINT", "Bug or case number"),
      Pattern.compile(getConfigurationOption("JUSTIFICATION_PATTERN", ".*")),
      Duration.ofMinutes(Integer.parseInt(getConfigurationOption("ELEVATION_DURATION", "5"))));
  }

  @Produces
  public ActivationTokenService.Options getTokenServiceOptions() {
    return new ActivationTokenService.Options(
      applicationPrincipal,
      Duration.ofMinutes(Integer.parseInt(getConfigurationOption("ACTIVATION_REQUEST_TIMEOUT", "120"))));

    // TODO: Validate that token lifetime < elevation duration (or use miniumum)
  }

  @Produces
  public NotificationService.Options getNotificationServiceOptions() {
    return new NotificationService.Options(!developmentMode);
  }
}
