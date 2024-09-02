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

package com.google.solutions.jitaccess.catalog.validation;

import com.google.solutions.jitaccess.apis.IamRole;
import com.google.solutions.jitaccess.apis.clients.IamClient;
import com.google.solutions.jitaccess.util.Lazy;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Validates IAM roles.
 */
@Singleton
public class IamRoleValidator {
  private final @NotNull Lazy<Set<IamRole>> predefinedRoles;

  public IamRoleValidator(
    @NotNull IamClient iamClient
  ) {
    //
    // Load list of predefined roles from IAM API, but do so
    // on first access only.
    //
    this.predefinedRoles = Lazy.initializeOpportunistically(
      () -> new HashSet<>(iamClient.listPredefinedRoles()));
  }

  public boolean isValidRole(@NotNull IamRole role) {
    if (role.isPredefined()) {
      return this.predefinedRoles
        .get()
        .contains(role);
    }
    else {
      //
      // We currently can't validate those, assume it's valid.
      //
      return true;
    }
  }
}
