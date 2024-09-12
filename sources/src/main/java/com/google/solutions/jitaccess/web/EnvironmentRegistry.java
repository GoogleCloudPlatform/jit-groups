//package com.google.solutions.jitaccess.web;
//
//import com.google.solutions.jitaccess.apis.Logger;
//import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
//import com.google.solutions.jitaccess.apis.clients.HttpTransport;
//import com.google.solutions.jitaccess.apis.clients.ResourceManagerClient;
//import com.google.solutions.jitaccess.auth.GroupMapping;
//import com.google.solutions.jitaccess.catalog.Provisioner;
//import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
//import com.google.solutions.jitaccess.common.Lazy;
//import com.google.solutions.jitaccess.provisioning.Environment;
//import org.jetbrains.annotations.NotNull;
//
//import java.time.Duration;
//import java.util.Collection;
//import java.util.concurrent.Executor;
//import java.util.function.Supplier;
//
///**
// * Environment provider that lazily loads environments.
// */
//public class EnvironmentRegistry {// TODO: test
//  private final @NotNull Collection<Environment> environments;
//
//  public EnvironmentRegistry(
//    @NotNull Collection<EnvironmentConfiguration> environments,
//    @NotNull GroupMapping groupMapping,
//    @NotNull CloudIdentityGroupsClient groupsClient,
//    @NotNull Executor executor,
//    @NotNull LazyCatalogSource.Options options,
//    @NotNull Logger logger
//  ) {
//    //
//    // Initialize environments, but don't load the policies yet because
//    // that's slow.
//    //
//    this.environments = environments.stream()
//      .map(cfg -> {
//        //
//        // Create a CRM client that uses this environment's credential
//        // (as opposed to the application credential).
//        //
//        var crmClient = new ResourceManagerClient(
//          cfg.resourceCredentials(),
//          options.httpTransportOptions());
//
//        var provisioner = new Provisioner(
//          cfg.name(),
//          groupMapping,
//          groupsClient,
//          crmClient,
//          executor,
//          logger);
//
//        return (Environment)new DelayLoadedEnvironment(
//          cfg,
//          provisioner,
//          options.cacheDuration());
//      })
//      .toList();
//  }
//
//  public @NotNull Collection<Environment> environments() {
//    return this.environments;
//  }
//
//  // -------------------------------------------------------------------------
//  // Inner classes.
//  // -------------------------------------------------------------------------
//
//  private static class DelayLoadedEnvironment implements Environment {
//    private final @NotNull EnvironmentConfiguration configuration;
//    private final @NotNull Provisioner provisioner;
//    private Lazy<EnvironmentPolicy> policy;
//
//    public DelayLoadedEnvironment(
//      @NotNull EnvironmentConfiguration configuration,
//      @NotNull Provisioner provisioner,
//      @NotNull Duration policyCacheDuration
//    ) {
//      this.configuration = configuration;
//      this.provisioner = provisioner;
//      this.policy = Lazy
//        .initializeOpportunistically(configuration::loadPolicy)
//        .reinitializeAfter(policyCacheDuration);
//    }
//
//    @Override
//    public @NotNull String name() {
//      return this.configuration.name();
//    }
//
//    @Override
//    public @NotNull String description() {
//      return this.configuration.description();
//    }
//
//    @Override
//    public @NotNull Supplier<EnvironmentPolicy> policy() {
//      return this.policy;
//    }
//
//    @Override
//    public @NotNull Provisioner provisioner() {
//      return this.provisioner;
//    }
//  }
//
//  public record Options(
//    @NotNull Duration cacheDuration,
//    @NotNull HttpTransport.Options httpTransportOptions
//  ) {}
//}
