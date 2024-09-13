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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.solutions.jitaccess.ApplicationRuntime;
import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.apis.clients.*;
import com.google.solutions.jitaccess.auth.*;
import com.google.solutions.jitaccess.catalog.Catalog;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.Proposal;
import com.google.solutions.jitaccess.catalog.legacy.LegacyPolicy;
import com.google.solutions.jitaccess.catalog.legacy.LegacyPolicyLoader;
import com.google.solutions.jitaccess.catalog.policy.PolicyDocumentSource;
import com.google.solutions.jitaccess.web.proposal.*;
import com.google.solutions.jitaccess.web.rest.UserResource;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Produces CDI beans to initialize application components.
 */
@Singleton
public class Application {
  /**
   * Application logger, not tied to a request context.
   */
  private static @NotNull Logger logger;

  /**
   * Configuration, based on app.yaml environment variables.
   */
  private static @NotNull ApplicationConfiguration configuration;

  /**
   * Information about the application's runtime.
   */
  private static @NotNull ApplicationRuntime runtime;

  // -------------------------------------------------------------------------
  // Application startup.
  // -------------------------------------------------------------------------

  public Application() {
    Preconditions.checkState(
      logger != null && configuration != null && runtime != null,
      "Class has not been initialized");
  }

  /**
   * Initialize static variables. This method is intended to be called
   * from outside the CDI context, in the application's main method.
   */
  public static void initialize(
    @NotNull ApplicationRuntime runtime,
    @NotNull ApplicationConfiguration configuration,
    @NotNull Logger logger
  ) {
    Application.logger = logger;
    Application.configuration = configuration;
    Application.runtime = runtime;
  }

  //---------------------------------------------------------------------------
  // Producers.
  //---------------------------------------------------------------------------

  @Produces
  @Singleton
  public RequireIapPrincipalFilter.Options produceIapRequestFilterOptions() {
    switch (runtime.type())
    {
      case APPENGINE:
        //
        // For AppEngine, we can derive the expected audience
        // from the project number and name.
        //
        return new RequireIapPrincipalFilter.Options(
          false,
          String.format("/projects/%s/apps/%s", runtime.projectNumber(), runtime.projectId()));

      case DEVELOPMENT:
        //
        // Disable expected audience-check.
        //
        return new RequireIapPrincipalFilter.Options(
          true, // Allow pseudo-authentication
          null);

      case CLOUDRUN:
        if (configuration.verifyIapAudience && configuration.backendServiceId.isPresent()) {
          //
          // Use backend service id to determine expected audience.
          //
          return new RequireIapPrincipalFilter.Options(
            false,
            String.format(
              "/projects/%s/global/backendServices/%s",
              runtime.projectNumber(),
              configuration.backendServiceId.get()));
        }
        else if (!configuration.verifyIapAudience) {
          //
          // Disable expected audience-check.
          //
          return new RequireIapPrincipalFilter.Options(
            false,
            null);
        }
        else {
          throw new RuntimeException(
            "Initializing application failed because the backend service ID is empty");
        }

      default:
        throw new IllegalStateException("Unexpected value: " + runtime.type());
    }
  }

  @Produces
  @Singleton
  public @NotNull Diagnosable produceDevModeDiagnosable() {
    final String name = "DevModeIsDisabled";
    return new Diagnosable() {
      @Override
      public Collection<DiagnosticsResult> diagnose() {
        if (runtime.type() == ApplicationRuntime.Type.DEVELOPMENT) {
          return List.of(
            new DiagnosticsResult(
              name,
              false,
              "Application is running in development mode"));
        }
        else {
          return List.of(new DiagnosticsResult(name));
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
    return new CachedSubjectResolver.Options(
      Duration.ofSeconds(30),
      new Directory(configuration.primaryDomain));
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
    return new ServiceAccountSigner.Options(runtime.applicationPrincipal());
  }

  @Produces
  @Singleton
  public IamClient.Options produceIamClientOptions() {
    return new IamClient.Options(
      runtime.type() == ApplicationRuntime.Type.DEVELOPMENT
        ? 500
        : Integer.MAX_VALUE);
  }

  @Produces
  @Singleton
  public UserResource.Options produceUserResourceOptions() {
    return new UserResource.Options(
      runtime.type() == ApplicationRuntime.Type.DEVELOPMENT);
  }

  @Produces
  public GoogleCredentials produceApplicationCredentials() {
    return runtime.applicationCredentials();
  }

  @Produces
  public @NotNull RequestContextLogger produceLogger(@NotNull RequestContext context) {
    return new RequestContextLogger(context);
  }

  @Produces
  public @NotNull Subject produceSubject(@NotNull RequestContext context) {
    return context.subject();
  }

  @Produces
  public @NotNull LinkBuilder produceLinkBuilder() {
    return uriInfo -> uriInfo
      .getBaseUriBuilder()
      .scheme(runtime.type() == ApplicationRuntime.Type.DEVELOPMENT
        ? "http"
        : "https");
  }

  @Produces
  @RequestScoped
  public @NotNull Catalog produceCatalog(
    @NotNull Subject subject,
    @NotNull EnvironmentRegistry environmentRegistry
  ) {
    return new Catalog(
      subject,
      environmentRegistry.environments());
  }

  @Produces
  @Singleton
  public @NotNull GroupMapping produceGroupMapping() {
    return new GroupMapping(configuration.groupsDomain);
  }

  @Produces
  @Singleton
  public @NotNull Consoles produceConsoles() {
    return new Consoles(configuration.organizationId);
  }

  @Produces
  public @NotNull ProposalHandler produceProposalHandler(
    @NotNull TokenSigner tokenSigner,
    @NotNull SecretManagerClient secretManagerClient
  ) {
    if (configuration.isSmtpConfigured()) {
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
    else if (runtime.type() == ApplicationRuntime.Type.DEVELOPMENT) {
      return new DebugProposalHandler(tokenSigner);
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
  public @NotNull EnvironmentRegistry produceEnvironmentRegistry(
    @NotNull GroupMapping groupMapping,
    @NotNull CloudIdentityGroupsClient groupsClient,
    @NotNull Executor executor
  ) {
    //
    // Prepare configuration for all environments, but don't load their
    // policy yet (because that's expensive).
    //
    final var configurations = new LinkedList<EnvironmentConfiguration>();
    for (var environment : configuration.environments) {
      if (environment.startsWith("file:")) {
        //
        // Value contains a file path, which is only allowed for development.
        //
        if (runtime.type() != ApplicationRuntime.Type.DEVELOPMENT) {
          logger.warn(
            EventIds.LOAD_ENVIRONMENT,
            "File-based policies are only allowed in development mode, ignoring environment '%s'",
            environment);
          break;
        }

        try {
          configurations.add(EnvironmentConfiguration.forFile(
            environment,
            runtime.applicationCredentials()));
        }
        catch (Exception e) {
          logger.warn(
            EventIds.LOAD_ENVIRONMENT,
            "Encountered an invalid environment configuration, ignoring",
            e);
          break;
        }
      }
      else if (ServiceAccountId.parse(environment).isPresent()) {
        try {
          configurations.add(EnvironmentConfiguration.forServiceAccount(
            ServiceAccountId.parse(environment).get(),
            runtime.applicationPrincipal(),
            runtime.applicationCredentials(),
            produceHttpTransportOptions()));
        }
        catch (Exception e) {
          logger.error(
            EventIds.LOAD_ENVIRONMENT,
            "Encountered an invalid environment configuration",
            e);
          throw new RuntimeException(
            "Loading catalog failed because of configuration issues");
        }
      }
      else {
        logger.error(
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
        () -> new ResourceManagerClient(runtime.applicationCredentials(), produceHttpTransportOptions()),
        () -> new AssetInventoryClient(runtime.applicationCredentials(), produceHttpTransportOptions()));

      configurations.add(
        new EnvironmentConfiguration(
          LegacyPolicy.NAME,
          LegacyPolicy.DESCRIPTION,
          runtime.applicationCredentials() // Use app service account, as in 1.x
        ) {
          @Override
          PolicyDocumentSource loadPolicy() {
            try {
              return PolicyDocumentSource.fromPolicy(legacyLoader.load(
                configuration.legacyProjectsQuery,
                configuration.legacyScope.get(),
                configuration.legacyActivationTimeout,
                configuration.legacyJustificationPattern,
                configuration.legacyJustificationHint,
                logger));
            }
            catch (Exception e) {
              throw new UncheckedExecutionException(e);
            }
          }});
    }

    if (configurations.isEmpty()) {
      //
      // No policy configured yet, use the "OOBE" example policy.
      //
      try {
        configurations.add(EnvironmentConfiguration.inertExample());
      }
      catch (IOException e) {
        logger.warn(EventIds.LOAD_ENVIRONMENT, e);
      }
    }

    var options = new EnvironmentRegistry.Options(
      runtime.type() == ApplicationRuntime.Type.DEVELOPMENT
        ? Duration.ofSeconds(20)
        : configuration.environmentCacheTimeout,
      produceHttpTransportOptions());

    return new EnvironmentRegistry(
      configurations,
      groupMapping,
      groupsClient,
      executor,
      options,
      logger);
  }
}
