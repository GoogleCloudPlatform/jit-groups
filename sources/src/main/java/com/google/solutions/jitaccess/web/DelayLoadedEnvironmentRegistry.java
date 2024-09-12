package com.google.solutions.jitaccess.web;

import com.google.api.client.util.Preconditions;
import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.apis.clients.HttpTransport;
import com.google.solutions.jitaccess.apis.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.auth.GroupMapping;
import com.google.solutions.jitaccess.catalog.Provisioner;
import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.common.Lazy;
import com.google.solutions.jitaccess.provisioning.Environment;
import com.google.solutions.jitaccess.provisioning.EnvironmentRegistry;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Executor;

/**
 * Environment provider that lazily loads environments.
 */
public class DelayLoadedEnvironmentRegistry implements EnvironmentRegistry {// TODO: test
  private final @NotNull Collection<Environment> environments;

  public DelayLoadedEnvironmentRegistry(
    @NotNull Collection<EnvironmentConfiguration> environments,
    @NotNull GroupMapping groupMapping,
    @NotNull CloudIdentityGroupsClient groupsClient,
    @NotNull Executor executor,
    @NotNull LazyCatalogSource.Options options,
    @NotNull Logger logger
  ) {
    //
    // Initialize environments, but don't load the policies yet because
    // that's slow.
    //
    this.environments = environments.stream()
      .map(c -> {
        //
        // Create a CRM client that uses this environment's credential
        // (as opposed to the application credential).
        //
        var crmClient = new ResourceManagerClient(
          c.resourceCredentials(),
          options.httpTransportOptions());

        var provisioner = new Provisioner(
          c.name(),
          groupMapping,
          groupsClient,
          crmClient,
          executor,
          logger);

        return (Environment)new DelayLoadedEnvironment(c, provisioner);
      })
      .toList();
  }

  @Override
  public @NotNull Collection<Environment> environments() {
    return this.environments;
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  private static class DelayLoadedEnvironment implements Environment {
    private final @NotNull EnvironmentConfiguration configuration;
    private final @NotNull Provisioner provisioner;
    private Lazy<EnvironmentPolicy> policy;

    public DelayLoadedEnvironment(
      @NotNull EnvironmentConfiguration configuration,
      @NotNull Provisioner provisioner
    ) {
      this.configuration = configuration;
      this.provisioner = provisioner;
      this.policy = Lazy.initializeOpportunistically(configuration::loadPolicy); // TODO: Use Lazy.cached?!
    }

    @Override
    public @NotNull String name() {
      return this.configuration.name();
    }

    @Override
    public @NotNull String description() {
      return this.configuration.description();
    }

    @Override
    public @NotNull EnvironmentPolicy policy() {
      var policy = this.policy.get();

      Preconditions.checkState(
        policy.name().equals(this.configuration.name()),
        String.format(
          "The name in the policy ('%s') must match the name used in the configuration ('%s')",
          policy.name(),
          this.configuration.name()));

      return policy;
    }

    @Override
    public @NotNull Provisioner provisioner() {
      return this.provisioner;
    }
  }

  public record Options(
    @NotNull Duration cacheDuration,
    @NotNull HttpTransport.Options httpTransportOptions
  ) {}
}
