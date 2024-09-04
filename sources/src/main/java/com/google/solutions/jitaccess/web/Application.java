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
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.solutions.jitaccess.ApplicationVersion;
import com.google.solutions.jitaccess.apis.clients.*;
import com.google.solutions.jitaccess.catalog.Catalog;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.catalog.Proposal;
import com.google.solutions.jitaccess.catalog.auth.*;
import com.google.solutions.jitaccess.catalog.legacy.LegacyPolicy;
import com.google.solutions.jitaccess.catalog.legacy.LegacyPolicyLoader;
import com.google.solutions.jitaccess.web.proposal.*;
import com.google.solutions.jitaccess.web.rest.UserResource;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Initializes the application and produces and produces CDI beans.
 */
@Singleton
public class Application {
  private static final String CONFIG_IMPERSONATE_SA = "jitaccess.impersonateServiceAccount";
  private static final String CONFIG_DEBUG_MODE = "jitaccess.debug";
  private static final String CONFIG_PROJECT = "jitaccess.project";

  private static final String projectId;
  private static final String projectNumber;
  private static final @NotNull GoogleCredentials applicationCredentials;
  private static final @NotNull ServiceAccountId applicationPrincipal;

  private static final @NotNull Logger logger;

  /**
   * Configuration, based on app.yaml environment variables.
   */
  private static final @NotNull ApplicationConfiguration configuration;

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

  // -------------------------------------------------------------------------
  // Application startup.
  // -------------------------------------------------------------------------

  /**
   * Force initialization by triggering the static constructor.
   */
  public static void initialize() {
  }

  /**
   * Perform all one-time initialization work.
   */
  static {
    //
    // Create a logger. We can't rely on injection as we're not in the
    // scope of a specific request here.
    //
    logger = new StructuredLogger.ApplicationContextLogger(System.out);
    configuration = new ApplicationConfiguration(System.getenv());

    if (!configuration.isSmtpConfigured()) {
      logger.warn(
        EventIds.STARTUP,
        "The SMTP configuration is incomplete");
    }

    if (isRunningOnAppEngine() || isRunningOnCloudRun()) {
      //
      // Initialize using service account attached to AppEngine or Cloud Run.
      //
      try {
        GenericData projectMetadata =
          getMetadata().parseAs(GenericData.class);

        projectId = (String) projectMetadata.get("projectId");
        projectNumber = projectMetadata.get("numericProjectId").toString();

        var defaultCredentials = (ComputeEngineCredentials)GoogleCredentials.getApplicationDefault();
        applicationPrincipal = ServiceAccountId
          .parse(ServiceAccountId.TYPE + ":" + defaultCredentials.getAccount())
          .orElseThrow(() -> new IllegalArgumentException(
            String.format("'%s' is not a valid service account email address",
              defaultCredentials.getAccount())));

        if (defaultCredentials.getScopes().containsAll(configuration.requiredOauthScopes())) {
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
            configuration.requiredOauthScopes().stream().toList(),
            0);
        }

        logger.info(
          EventIds.STARTUP,
          String.format("Running in project %s (%s) as %s, version %s",
            projectId,
            projectNumber,
            applicationPrincipal,
            ApplicationVersion.VERSION_STRING));
      }
      catch (IOException e) {
        logger.error(
          EventIds.STARTUP,
          "Failed to lookup instance metadata", e);
        throw new RuntimeException("Failed to initialize runtime environment", e);
      }
    }
    else if (isDebugModeEnabled()) {
      //
      // Initialize using development settings and credential.
      //
      projectId = System.getProperty(CONFIG_PROJECT, "dev");
      projectNumber = "0";

      try {
        var defaultCredentials = GoogleCredentials.getApplicationDefault();

        var impersonateServiceAccount = ServiceAccountId.parse(
          ServiceAccountId.TYPE + ":" + System.getProperty(CONFIG_IMPERSONATE_SA));
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
            configuration.requiredOauthScopes().stream().toList(),
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
            .get();
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

      logger.warn(
        EventIds.STARTUP,
        String.format("Running in development mode as %s", applicationPrincipal));
    }
    else {
      throw new RuntimeException(
        "Application is not running on AppEngine or Cloud Run, and debug mode is disabled. Aborting startup");
    }
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

  //---------------------------------------------------------------------------
  // Producers.
  //---------------------------------------------------------------------------

  @Produces
  @Singleton
  public RequireIapPrincipalFilter.Options produceIapRequestFilterOptions() {
    if (isDebugModeEnabled() || !configuration.verifyIapAudience){
      //
      // Disable expected audience-check.
      //
      return new RequireIapPrincipalFilter.Options(
        isDebugModeEnabled(),
        null);
    }
    else if (isRunningOnAppEngine()) {
      //
      // For AppEngine, we can derive the expected audience
      // from the project number and name.
      //
      return new RequireIapPrincipalFilter.Options(
        isDebugModeEnabled(),
        String.format("/projects/%s/apps/%s", projectNumber, projectId));
    }
    else  if (configuration.backendServiceId.isPresent()) {
      //
      // For Cloud Run, we need the backend service id.
      //
      return new RequireIapPrincipalFilter.Options(
        isDebugModeEnabled(),
        String.format(
          "/projects/%s/global/backendServices/%s",
          projectNumber,
          configuration.backendServiceId.get()));
    }
    else {
      throw new RuntimeException(
        "Initializing application failed because the backend service ID is empty");
    }
  }

  @Produces
  @Singleton
  public @NotNull Diagnosable produceDevModeDiagnosable() {
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

  @Produces
  public @NotNull CloudIdentityGroupsClient.Options produceCloudIdentityGroupsClientOptions() {
    return new CloudIdentityGroupsClient.Options(configuration.customerId);
  }

  @Produces
  public @NotNull CachedSubjectResolver.Options produceCachedSubjectResolverOptions() {
    //
    // Use a cache duration that's long enough so that a subsequent page
    // load can benefit from it, but short enough so that new group
    // memberships are applied without substantial extra delay.
    //
    return new CachedSubjectResolver.Options(Duration.ofSeconds(30));
  }

  @Produces
  @Singleton
  public @NotNull HttpTransport.Options produceHttpTransportOptions() {
    return new HttpTransport.Options(
      configuration.backendConnectTimeout,
      configuration.backendReadTimeout,
      configuration.backendWriteTimeout);
  }

  @Produces
  @Singleton
  public @NotNull ServiceAccountSigner.Options produceServiceAccountSignerOptions() {
    return new ServiceAccountSigner.Options(this.applicationPrincipal);
  }

  @Produces
  @Singleton
  public IamClient.Options produceIamClientOptions() {
    return new IamClient.Options(isDebugModeEnabled() ? 500 : Integer.MAX_VALUE);
  }

  @Produces
  @Singleton
  public UserResource.Options produceUserResourceOptions() {
    return new UserResource.Options(isDebugModeEnabled());
  }

  @Produces
  public GoogleCredentials produceApplicationCredentials() {
    return this.applicationCredentials;
  }

  @Produces
  public @NotNull StructuredLogger.RequestContextLogger produceLogger(@NotNull RequestContext context) {
    return new StructuredLogger.RequestContextLogger(context);
  }

  @Produces
  public @NotNull Subject produceSubject(@NotNull RequestContext context) {
    return context.subject();
  }

  @Produces
  public @NotNull LinkBuilder produceLinkBuilder() {
    return uriInfo -> uriInfo
      .getBaseUriBuilder()
      .scheme(isRunningOnAppEngine() || isRunningOnCloudRun() ? "https" : "http");
  }

  @Produces
  @RequestScoped
  public @NotNull Catalog produceCatalog(
    @NotNull Subject subject,
    @NotNull LazyCatalogSource catalogSource
  ) {
    return new Catalog(
      subject,
      catalogSource);
  }

  @Produces
  @Singleton
  public @NotNull GroupMapping produceGroupMapping() {
    return new GroupMapping(configuration.groupsDomain);
  }

  @Produces
  public @NotNull ProposalHandler produceProposalHandler(
    @NotNull TokenSigner tokenSigner,
    @NotNull SecretManagerClient secretManagerClient
  ) {
    if (isDebugModeEnabled()) {
      return new DebugProposalHandler(tokenSigner);
    }
    else if (configuration.isSmtpConfigured()) {
      var smtpOptions = new SmtpClient.Options(
        configuration.smtpHost,
        configuration.smtpPort,
        configuration.smtpSenderName,
        new EmailAddress(configuration.smtpSenderAddress.get()),
        configuration.smtpEnableStartTls,
        configuration.smtpExtraOptionsMap());

      //
      // Lookup credentials from config and/or secret. Use the secret
      // if both are configured.
      //
      if (configuration.isSmtpAuthenticationConfigured() && configuration.smtpSecret.isPresent()) {
        smtpOptions.setSmtpSecretCredentials(
          configuration.smtpUsername.get(),
          configuration.smtpSecret.get());
      }
      else if (configuration.isSmtpAuthenticationConfigured() && configuration.smtpPassword.isPresent()) {
        smtpOptions.setSmtpCleartextCredentials(
          configuration.smtpUsername.get(),
          configuration.smtpPassword.get());
      }

      return new MailProposalHandler(
        tokenSigner,
        new EmailMapping(configuration.smtpAddressMapping.orElse(null)),
        new SmtpClient(
          secretManagerClient,
          smtpOptions),
        new MailProposalHandler.Options(
          configuration.notificationTimeZone,
          configuration.proposalTimeout));
    }
    else {
      return new ProposalHandler() {
        @Override
        public @NotNull ProposalHandler.ProposalToken propose(
          @NotNull JitGroupContext.JoinOperation joinOperation,
          @NotNull Function<String, URI> buildActionUri
          ) {
          throw new UnsupportedOperationException(
            "Approvals are not supported because the SMTP configuration is incomplete");
        }

        @Override
        public @NotNull Proposal accept(
          @NotNull String proposalToken
        ) {
          throw new UnsupportedOperationException(
            "Approvals are not supported because the SMTP configuration is incomplete");
        }
      };
    }
  }

  @Produces
  @Singleton
  public @NotNull LazyCatalogSource produceEnvironments(
    @NotNull GroupMapping groupMapping,
    @NotNull CloudIdentityGroupsClient groupsClient,
    @NotNull Executor executor
  ) {
    //
    // Prepare configuration for all environments, but don't load their
    // policy yet (because that's expensive).
    //
    final var configurations = new HashMap<String, EnvironmentConfiguration>();
    for (var environment : configuration.environments) {
      if (environment.startsWith("file:")) {
        //
        // Value contains a file path, which is only allowed for development.
        //
        if (!isDebugModeEnabled()) {
          this.logger.warn(
            EventIds.LOAD_ENVIRONMENT,
            "File-based policies are only allowed in debug mode, ignoring environment '%s'",
            environment);
          break;
        }

        try {
          var configuration = EnvironmentConfiguration.forFile(
            environment,
            this.applicationCredentials);
          configurations.put(configuration.name(), configuration);
        }
        catch (Exception e) {
          this.logger.warn(
            EventIds.LOAD_ENVIRONMENT,
            "Encountered an invalid environment configuration, ignoring",
            e);
          break;
        }
      }
      else if (ServiceAccountId.parse(environment).isPresent()) {
        try {
          var configuration = EnvironmentConfiguration.forServiceAccount(
            ServiceAccountId.parse(environment).get(),
            this.applicationPrincipal,
            this.applicationCredentials,
            produceHttpTransportOptions());
          configurations.put(configuration.name(), configuration);
        }
        catch (Exception e) {
          this.logger.error(
            EventIds.LOAD_ENVIRONMENT,
            "Encountered an invalid environment configuration",
            e);
          throw new RuntimeException(
            "Loading catalog failed because of configuration issues");
        }
      }
      else {
        this.logger.error(
          EventIds.LOAD_ENVIRONMENT,
          "Encountered an unrecognized entry in environment configuration, " +
            "this might be because of a missing or invalid prefix: %s",
          environment);
      }
    }

    if (configuration.legacyCatalog.equalsIgnoreCase("ASSETINVENTORY") &&
      configuration.legacyScope.isPresent()) {

      //
      // Load an extra environment that surfaces JIT Access 1.x roles.
      //
      var legacyLoader = new LegacyPolicyLoader(
        () -> new ResourceManagerClient(this.applicationCredentials, produceHttpTransportOptions()),
        () -> new AssetInventoryClient(this.applicationCredentials, produceHttpTransportOptions()));

      configurations.put(
        LegacyPolicy.NAME,
        new EnvironmentConfiguration(
          LegacyPolicy.NAME,
          LegacyPolicy.DESCRIPTION,
          this.applicationCredentials, // Use app service account, as in 1.x
          () -> {
            try {
              return legacyLoader.load(
                configuration.legacyProjectsQuery,
                configuration.legacyScope.get(),
                configuration.legacyActivationTimeout,
                configuration.legacyJustificationPattern,
                configuration.legacyJustificationHint,
                this.logger);
            }
            catch (Exception e) {
              throw new UncheckedExecutionException(e);
            }
          }));
    }

    if (configurations.isEmpty()) {
      //
      // No policy configured yet, use the "OOBE" example policy.
      //
      try {
        var example = EnvironmentConfiguration.inertExample();
        configurations.put(
          example.name(),
          example);
      }
      catch (IOException e) {
        this.logger.warn(EventIds.LOAD_ENVIRONMENT, e);
      }
    }

    return new LazyCatalogSource(
      configurations.entrySet()
        .stream()
        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())),
      envName -> configurations.get(envName).loadPolicy(),
      policy -> new ResourceManagerClient(
        configurations.get(policy.name()).resourceCredentials(),
        produceHttpTransportOptions()),
      groupMapping,
      groupsClient,
      isDebugModeEnabled()
        ? Duration.ofSeconds(20)
        : this.configuration.environmentCacheTimeout,
      executor,
      this.logger);
  }
}
