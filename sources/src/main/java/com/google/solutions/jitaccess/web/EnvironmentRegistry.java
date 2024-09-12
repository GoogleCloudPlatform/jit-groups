package com.google.solutions.jitaccess.web;

import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.apis.clients.HttpTransport;
import com.google.solutions.jitaccess.apis.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.auth.GroupMapping;
import com.google.solutions.jitaccess.catalog.Provisioner;
import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.provisioning.Environment;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Executor;

/**
 * Environment provider that lazily loads environments.
 */
public class EnvironmentRegistry {// TODO: test
  private final @NotNull Collection<Environment> environments;

  public EnvironmentRegistry(
    @NotNull Collection<EnvironmentConfiguration> environments,
    @NotNull GroupMapping groupMapping,
    @NotNull CloudIdentityGroupsClient groupsClient,
    @NotNull Executor executor,
    @NotNull LazyCatalogSource.Options options,
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
          protected EnvironmentPolicy loadPolicy() {
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
