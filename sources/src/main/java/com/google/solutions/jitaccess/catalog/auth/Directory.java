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

package com.google.solutions.jitaccess.catalog.auth;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Information about the provenance of a user account.
 *
 * @param type the type of directory
 * @param hostedDomain primary domain of Cloud Identity/Workspace account
 */
public record Directory(
  @NotNull Directory.Type type,
  @Nullable String hostedDomain
) {
  public static final @NotNull Directory CONSUMER = new Directory(
    Type.CONSUMER,
    null);

  public static final @NotNull Directory PROJECT = new Directory(
    Type.PROJECT,
    null);

  public Directory {
    Preconditions.checkArgument(hostedDomain == null || type == Type.CLOUD_IDENTITY);
  }

  public Directory(@NotNull String hostedDomain) {
    this(Type.CLOUD_IDENTITY, hostedDomain);
  }

  @Override
  public String toString() {
    return switch (this.type) {
      case CONSUMER, PROJECT -> this.type.toString();
      case CLOUD_IDENTITY -> this.hostedDomain;
    };
  }

  /**
   * Type of directory.
   */
  public enum Type {
    /**
     * Consumer account directory, includes Gmail and all other
     * consumer accounts.
     */
    CONSUMER,

    /**
     * A Cloud Identity or Workspace account.
     */
    CLOUD_IDENTITY,

    /**
     * A Google Cloud project.
     */
    PROJECT
  }
}
