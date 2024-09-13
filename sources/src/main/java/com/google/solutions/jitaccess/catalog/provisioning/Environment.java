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

package com.google.solutions.jitaccess.catalog.provisioning;

import com.google.solutions.jitaccess.catalog.policy.*;
import com.google.solutions.jitaccess.common.Lazy;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Environment that can be provisioned to.
 */
public abstract class Environment {
  private final @NotNull String name;
  private final @NotNull String description;
  private final @NotNull Lazy<EnvironmentPolicy> policy;
  private final @NotNull Provisioner provisioner;

  protected Environment(
    @NotNull String name,
    @NotNull String description,
    @NotNull Provisioner provisioner,
    @NotNull Duration policyCacheDuration
  ) {
    this.name = name;
    this.description = description;
    this.provisioner = provisioner;

    //
    // Load policy on first access only, because doing so
    // might be slow.
    //
    this.policy = Lazy
      .initializeOpportunistically(() -> loadPolicy()
        .parse()
        .policy())
      .reinitializeAfter(policyCacheDuration);
  }

  /**
   * Name of the policy.
   */
  public @NotNull String name() {
    return this.name;
  }

  /**
   * Description of the environment.
   */
  public @NotNull String description() {
    return this.description;
  }

  /**
   * Policy for this environment, can be delay-loaded.
   */
  public @NotNull EnvironmentPolicy policy() {
    return this.policy.get();
  }

  /**
   * Provisioner for managing access to this environment.
   */
  public @NotNull Provisioner provisioner() {
    return this.provisioner;
  }

  /**
   * Load the raw, unparsed policy from file or backing store.
   */
  public abstract PolicyDocumentSource loadPolicy();
}