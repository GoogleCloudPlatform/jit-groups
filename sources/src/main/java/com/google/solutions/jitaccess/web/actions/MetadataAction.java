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

package com.google.solutions.jitaccess.web.actions;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.ApplicationVersion;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.JustificationPolicy;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import org.jetbrains.annotations.NotNull;

/**
 * Get information about this instance of JIT Access.
 */
public class MetadataAction extends AbstractAction {
  private final @NotNull MpaProjectRoleCatalog catalog;
  private final @NotNull JustificationPolicy justificationPolicy;

  public MetadataAction(
    @NotNull LogAdapter logAdapter,
    @NotNull MpaProjectRoleCatalog catalog,
    @NotNull JustificationPolicy justificationPolicy
  ) {
    super(logAdapter);
    this.catalog = catalog;
    this.justificationPolicy = justificationPolicy;
  }

  public @NotNull MetadataAction.ResponseEntity execute(
    @NotNull IapPrincipal iapPrincipal
  ) {
    var options = this.catalog.options();
    return new ResponseEntity(
      justificationPolicy.hint(),
      iapPrincipal.email(),
      ApplicationVersion.VERSION_STRING,
      (int)options.maxActivationDuration().toMinutes(),
      Math.min(60, (int)options.maxActivationDuration().toMinutes()));
  }

  public static class ResponseEntity {
    public final @NotNull String justificationHint;
    public final @NotNull UserId signedInUser;
    public final @NotNull String applicationVersion;
    public final int defaultActivationTimeout; // in minutes.
    public final int maxActivationTimeout;     // in minutes.

    private ResponseEntity(
      @NotNull String justificationHint,
      @NotNull UserId signedInUser,
      @NotNull String applicationVersion,
      int maxActivationTimeoutInMinutes,
      int defaultActivationTimeoutInMinutes
    ) {
      Preconditions.checkNotNull(justificationHint, "justificationHint");
      Preconditions.checkNotNull(signedInUser, "signedInUser");
      Preconditions.checkArgument(defaultActivationTimeoutInMinutes > 0, "defaultActivationTimeoutInMinutes");
      Preconditions.checkArgument(maxActivationTimeoutInMinutes > 0, "maxActivationTimeoutInMinutes");
      Preconditions.checkArgument(maxActivationTimeoutInMinutes >= defaultActivationTimeoutInMinutes, "maxActivationTimeoutInMinutes");

      this.justificationHint = justificationHint;
      this.signedInUser = signedInUser;
      this.applicationVersion = applicationVersion;
      this.defaultActivationTimeout = defaultActivationTimeoutInMinutes;
      this.maxActivationTimeout = maxActivationTimeoutInMinutes;
    }
  }
}
