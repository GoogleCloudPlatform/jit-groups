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
import com.google.solutions.jitaccess.core.auth.EmailMapping;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.RegexJustificationPolicy;
import com.google.solutions.jitaccess.core.catalog.TokenSigner;
import com.google.solutions.jitaccess.core.catalog.project.*;
import com.google.solutions.jitaccess.core.clients.*;
import com.google.solutions.jitaccess.core.notifications.MailNotificationService;
import com.google.solutions.jitaccess.core.notifications.NotificationService;
import com.google.solutions.jitaccess.core.notifications.PubSubNotificationService;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

/**
 * Provides access to runtime configuration (AppEngine, local). To be injected using CDI.
 */
@Singleton
public class RuntimeEnvironment {
  private static final String CONFIG_IMPERSONATE_SA = "jitaccess.impersonateServiceAccount";
  private static final String CONFIG_DEBUG_MODE = "jitaccess.debug";
  private static final String CONFIG_PROJECT = "jitaccess.project";

  private final String projectId;
  private final String projectNumber;
  private final @NotNull UserId applicationPrincipal;
  private final GoogleCredentials applicationCredentials;

  /**
   * Configuration, based on app.yaml environment variables.
   */
  private final RuntimeConfiguration configuration = new RuntimeConfiguration(System::getenv);

  // -------------------------------------------------------------------------
  // Private helpers.
  // -------------------------------------------------------------------------

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
          getMetadata().parseAs(GenericData.class);

        this.projectId = (String) projectMetadata.get("projectId");
        this.projectNumber = projectMetadata.get("numericProjectId").toString();

        var defaultCredentials = (ComputeEngineCredentials)GoogleCredentials.getApplicationDefault();
        this.applicationPrincipal = new UserId(defaultCredentials.getAccount());

        if (defaultCredentials.getScopes().containsAll(this.configuration.getRequiredOauthScopes())) {
          //
          // Default credential has all the right scopes, use it as-is.
          //
          this.applicationCredentials = defaultCredentials;
        }
        else {
          //
          // Extend the set of scopes to include required non-cloud APIs by
          // letting the service account impersonate itself.
          //
          this.applicationCredentials = ImpersonatedCredentials.create(
            defaultCredentials,
            this.applicationPrincipal.email,
            null,
            this.configuration.getRequiredOauthScopes().stream().toList(),
            0);
        }

        logAdapter
          .newInfoEntry(
            LogEvents.RUNTIME_STARTUP,
            String.format("Running in project %s (%s) as %s, version %s, using %s catalog",
              this.projectId,
              this.projectNumber,
              this.applicationPrincipal,
              ApplicationVersion.VERSION_STRING,
              this.configuration.catalog.getValue()))
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
      this.projectId = System.getProperty(CONFIG_PROJECT, "dev");
      this.projectNumber = "0";

      try {
        var defaultCredentials = GoogleCredentials.getApplicationDefault();

        var impersonateServiceAccount = System.getProperty(CONFIG_IMPERSONATE_SA);
        if (impersonateServiceAccount != null && !impersonateServiceAccount.isEmpty()) {
          //
          // Use the application default credentials (ADC) to impersonate a
          // service account. This step is necessary to ensure we have a
          // credential for the right set of scopes, and that we're not running
          // with end-user credentials.
          //
          this.applicationCredentials = ImpersonatedCredentials.create(
            defaultCredentials,
            impersonateServiceAccount,
            null,
            this.configuration.getRequiredOauthScopes().stream().toList(),
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

  public UriBuilder createAbsoluteUriBuilder(@NotNull UriInfo uriInfo) {
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

  public @NotNull UserId getApplicationPrincipal() {
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
  public @NotNull TokenSigner.Options getTokenServiceOptions() {
    //
    // NB. The clock for activations "starts ticking" when the activation was
    // requested. The time allotted for reviewers to approve the request
    // must therefore not exceed the lifetime of the activation itself.
    //
    var effectiveRequestTimeout = Duration.ofSeconds(Math.min(
      this.configuration.activationRequestTimeout.getValue().getSeconds(),
      this.configuration.activationTimeout.getValue().getSeconds()));

    return new TokenSigner.Options(
      applicationPrincipal,
      effectiveRequestTimeout);
  }

  @Produces
  @Singleton
  public @NotNull NotificationService getPubSubNotificationService(
    PubSubClient pubSubClient
  ) {
    if (this.configuration.topicName.isValid()) {
      return new PubSubNotificationService(
        pubSubClient,
        new PubSubNotificationService.Options(
          new PubSubTopic(this.projectId, this.configuration.topicName.getValue())));
    }
    else {
      return new NotificationService.SilentNotificationService(isDebugModeEnabled());
    }
  }

  @Produces
  @Singleton
  public @NotNull NotificationService getEmailNotificationService(
    @NotNull SecretManagerClient secretManagerClient,
    @NotNull EmailMapping emailMapping
  ) {
    //
    // Configure SMTP if possible, and fall back to a fail-safe
    // configuration if the configuration is incomplete.
    //
    if (this.configuration.isSmtpConfigured()) {
      var options = new SmtpClient.Options(
        this.configuration.smtpHost.getValue(),
        this.configuration.smtpPort.getValue(),
        this.configuration.smtpSenderName.getValue(),
        new EmailAddress(this.configuration.smtpSenderAddress.getValue()),
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

      return new MailNotificationService(
        new SmtpClient(secretManagerClient, options),
        emailMapping,
        new MailNotificationService.Options(this.configuration.timeZoneForNotifications.getValue()));
    }
    else {
      return new NotificationService.SilentNotificationService(isDebugModeEnabled());
    }
  }

  @Produces
  public @NotNull EmailMapping getEmailMapping() {
    return new EmailMapping(this.configuration.smtpAddressMapping.getValue());
  }

  @Produces
  public @NotNull ProjectRoleActivator.Options getProjectRoleActivatorOptions() {
    return new ProjectRoleActivator.Options(
      this.configuration.maxNumberOfEntitlementsPerSelfApproval.getValue());
  }

  @Produces
  public @NotNull HttpTransport.Options getHttpTransportOptions() {
    return new HttpTransport.Options(
      this.configuration.backendConnectTimeout.getValue(),
      this.configuration.backendReadTimeout.getValue(),
      this.configuration.backendWriteTimeout.getValue());
  }

  @Produces
  public @NotNull RegexJustificationPolicy.Options getRegexJustificationPolicyOptions() {
    return new RegexJustificationPolicy.Options(
      this.configuration.justificationHint.getValue(),
      Pattern.compile(this.configuration.justificationPattern.getValue()));
  }

  @Produces
  public @NotNull MpaProjectRoleCatalog.Options getIamPolicyCatalogOptions() {
    return new MpaProjectRoleCatalog.Options(
      this.configuration.availableProjectsQuery.isValid()
        ? this.configuration.availableProjectsQuery.getValue()
        : null,
      this.configuration.activationTimeout.getValue(),
      this.configuration.minNumberOfReviewersPerActivationRequest.getValue(),
      this.configuration.maxNumberOfReviewersPerActivationRequest.getValue());
  }

  @Produces
  public @NotNull DirectoryGroupsClient.Options getDirectoryGroupsClientOptions() {
    return new DirectoryGroupsClient.Options(
      this.configuration.customerId.getValue());
  }

  @Produces
  public @NotNull CloudIdentityGroupsClient.Options getCloudIdentityGroupsClientOptions() {
    return new CloudIdentityGroupsClient.Options(
      this.configuration.customerId.getValue());
  }

  @Produces
  @Singleton
  public @NotNull ProjectRoleRepository getProjectRoleRepository(
    @NotNull Executor executor,
    @NotNull Instance<DirectoryGroupsClient> groupsClient,
    @NotNull PolicyAnalyzerClient policyAnalyzerClient
  ) {
    switch (this.configuration.catalog.getValue()) {
      case ASSETINVENTORY:
        return new AssetInventoryRepository(
          executor,
          groupsClient.get(),
          (AssetInventoryClient)policyAnalyzerClient,
          new AssetInventoryRepository.Options(this.configuration.scope.getValue()));

      case POLICYANALYZER:
      default:
        return new PolicyAnalyzerRepository(
          policyAnalyzerClient,
          new PolicyAnalyzerRepository.Options(this.configuration.scope.getValue()));
    }
  }

  @Produces
  @Singleton
  public @NotNull Diagnosable verifyDevModeIsDisabled() {
    final String name = "DevModeIsDisabled";
    return new Diagnosable() {
      @Override
      public Collection<DiagnosticsResult> diagnose() {
        if (!isDebugModeEnabled()) {
          return List.of(new DiagnosticsResult(name));
        }
        else {
          return List.of(
            new DiagnosticsResult(
              name,
              false,
              "Application is running in development mode"));
        }
      }
    };
  }
}
