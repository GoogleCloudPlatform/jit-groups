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
import com.google.solutions.jitaccess.core.adapters.*;
import com.google.solutions.jitaccess.core.data.UserId;
import com.google.solutions.jitaccess.core.services.ActivationTokenService;
import com.google.solutions.jitaccess.core.services.NotificationService;
import com.google.solutions.jitaccess.core.services.RoleActivationService;
import com.google.solutions.jitaccess.core.services.RoleDiscoveryService;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
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

  private final String projectId;
  private final String projectNumber;
  private final UserId applicationPrincipal;
  private final GoogleCredentials applicationCredentials;

  /**
   * Configuration, based on app.yaml environment variables.
   */
  private final RuntimeConfiguration configuration = new RuntimeConfiguration(System::getenv);

  // -------------------------------------------------------------------------
  // Private helpers.
  // -------------------------------------------------------------------------

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

  public boolean isRunningOnAppEngine() {
    return System.getenv().containsKey("GAE_SERVICE");
  }

  public boolean isRunningOnCloudRun() {
    return System.getenv().containsKey("K_SERVICE");
  }

  public String getBackendServiceId() {
    return configuration.backendServiceId.getValue();
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

    if (!this.configuration.isSmtpConfigured()) {
      logAdapter
        .newWarningEntry(
          LogEvents.RUNTIME_STARTUP,
          "The SMTP configuration is incomplete")
        .write();
    }

    if (isRunningOnAppEngine() || isRunningOnCloudRun()) {
      //
      // Initialize using service account attached to AppEngine or Cloud Run.
      //
      try {
        GenericData projectMetadata =
          getMetadata("/computeMetadata/v1/project/?recursive=true").parseAs(GenericData.class);

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
      // Initialize using development settings and credential.
      //
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
                IamCredentialsAdapter.OAUTH_SCOPE,
                SecretManagerAdapter.OAUTH_SCOPE)
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
        "Application is not running on AppEngine or Cloud Run, and debug mode is disabled. Aborting startup");
    }
  }

  public boolean isDebugModeEnabled() {
    return Boolean.getBoolean(CONFIG_DEBUG_MODE);
  }

  public UriBuilder createAbsoluteUriBuilder(UriInfo uriInfo) {
    return uriInfo
      .getBaseUriBuilder()
      .scheme(isRunningOnAppEngine() || isRunningOnCloudRun() ? "https" : "http");
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
    return new RoleDiscoveryService.Options(this.configuration.scope.getValue());
  }

  @Produces
  public RoleActivationService.Options getRoleActivationServiceOptions() {
    return new RoleActivationService.Options(
      this.configuration.justificationHint.getValue(),
      Pattern.compile(this.configuration.justificationPattern.getValue()),
      this.configuration.activationTimeout.getValue(),
      this.configuration.minNumberOfReviewersPerActivationRequest.getValue(),
      this.configuration.maxNumberOfReviewersPerActivationRequest.getValue());
  }

  @Produces
  public ActivationTokenService.Options getTokenServiceOptions() {
    //
    // NB. The clock for activations "starts ticking" when the activation was
    // requested. The time allotted for reviewers to approve the request
    // must therefore not exceed the lifetime of the activation itself.
    //
    var effectiveRequestTimeout = Duration.ofSeconds(Math.min(
      this.configuration.activationRequestTimeout.getValue().getSeconds(),
      this.configuration.activationTimeout.getValue().getSeconds()));

    return new ActivationTokenService.Options(
      applicationPrincipal,
      effectiveRequestTimeout);
  }

  @Produces
  @ApplicationScoped
  public NotificationService getNotificationService(
    SecretManagerAdapter secretManagerAdapter
  ) {
    //
    // Configure SMTP if possible, and fall back to a fail-safe
    // configuration if the configuration is incomplete.
    //
    if (this.configuration.isSmtpConfigured()) {
      var options = new SmtpAdapter.Options(
        this.configuration.smtpHost.getValue(),
        this.configuration.smtpPort.getValue(),
        this.configuration.smtpSenderName.getValue(),
        this.configuration.smtpSenderAddress.getValue(),
        this.configuration.smtpEnableStartTls.getValue(),
        this.configuration.getSmtpExtraOptionsMap());

      //
      // Lookup credentials from config and/or secret. Use the secret
      // if both are configured.
      //
      if (this.configuration.isSmtpAuthenticationConfigured() && this.configuration.smtpSecret.isValid()) {
        options.setSmtpSecretCredentials(
          this.configuration.smtpUsername.getValue(),
          this.configuration.smtpSecret.getValue());
      }
      else if (this.configuration.isSmtpAuthenticationConfigured() && this.configuration.smtpPassword.isValid()) {
        options.setSmtpCleartextCredentials(
          this.configuration.smtpUsername.getValue(),
          this.configuration.smtpPassword.getValue());
      }

      return new NotificationService.MailNotificationService(
        new SmtpAdapter(secretManagerAdapter, options),
        new NotificationService.Options(this.configuration.timeZoneForNotifications.getValue()));
    }
    else {
      return new NotificationService.SilentNotificationService();
    }
  }
}
