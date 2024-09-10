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

import com.google.api.client.util.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.apis.clients.HttpTransport;
import com.google.solutions.jitaccess.apis.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.catalog.Catalog;
import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.catalog.Provisioner;
import com.google.solutions.jitaccess.catalog.auth.GroupMapping;
import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.catalog.policy.PolicyHeader;
import com.google.solutions.jitaccess.util.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Catalog source that lazily loads policies on demand and caches them.
 */
public class LazyCatalogSource implements Catalog.Source {
  private final @NotNull LoadingCache<String, Entry> environmentCache;
  private final @NotNull Map<String, EnvironmentConfiguration> environments;
  private final @NotNull Logger logger;

  LazyCatalogSource(
    @NotNull Collection<EnvironmentConfiguration> environments,
    @NotNull GroupMapping groupMapping,
    @NotNull CloudIdentityGroupsClient groupsClient,
    @NotNull Executor executor,
    @NotNull Options options,
    @NotNull Logger logger
  ) {
    this.environments = environments
      .stream()
      .collect(Collectors.toMap(e -> e.name(), e -> e));
    this.logger = logger;

    //
    // Prepare policy cache.
    //
    this.environmentCache = CacheBuilder.newBuilder()
      .expireAfterWrite(options.cacheDuration())
      .build(new CacheLoader<>() {
        @Override
        public @NotNull Entry load(
          @NotNull String environmentName
        ) {
          var configuration = LazyCatalogSource.this.environments.get(environmentName);
          assert configuration != null; // In lookup(), we check that the key exists.

          var policy = configuration.loadPolicy();

          Preconditions.checkState(
            policy.name().equals(environmentName),
            String.format(
              "The name in the policy ('%s') must match the name used in the configuration ('%s')",
              policy.name(),
              environmentName));

          //
          // Create a CRM client that uses this environment's credential
          // (as opposed to the application credential).
          //
          var crmClient = new ResourceManagerClient(
            configuration.resourceCredentials(),
            options.httpTransportOptions());

          return new Entry(
            policy,
            new Provisioner(
              environmentName,
              groupMapping,
              groupsClient,
              crmClient,
              executor,
              logger));
        }
      });
  }

  private @NotNull Optional<Entry> lookup(
    @NotNull String name
  ) {
    if (!this.environments.containsKey(name)) {
      return Optional.empty();
    }

    //
    // Lookup from cache (or load it lazily). Throws an exception if not found.
    //
    try {
      return Optional.of(this.environmentCache.get(name));
    }
    catch (Exception e) {
      this.logger.error(
        EventIds.LOAD_ENVIRONMENT,
        String.format("Loading policy for environment '%s' failed", name),
        Exceptions.unwrap(e));
      return Optional.empty();
    }
  }

  // -------------------------------------------------------------------------
  // Catalog.Source.
  // -------------------------------------------------------------------------

  @Override
  public @NotNull Collection<PolicyHeader> environmentPolicies() {
    //
    // Avoid eagerly loading all policies just to retrieve their
    // name and descriptions.
    //
    return this.environments
      .values()
      .stream()
      .map(e -> (PolicyHeader)e)
      .toList();
  }

  @Override
  public @NotNull Optional<EnvironmentPolicy> environmentPolicy(@NotNull String name) {
    return lookup(name).map(e -> e.policy);
  }

  @Override
  public @NotNull Optional<Provisioner> provisioner(
    @NotNull Catalog catalog,
    @NotNull String name
  ) {
    return lookup(name).map(e -> e.provisioner);
  }

  private record Entry(
    @NotNull EnvironmentPolicy policy,
    @NotNull Provisioner provisioner
  ) {}

  // -------------------------------------------------------------------------
  // Options.
  // -------------------------------------------------------------------------

  public record Options(
    @NotNull Duration cacheDuration,
    @NotNull HttpTransport.Options httpTransportOptions
  ) {}
}
