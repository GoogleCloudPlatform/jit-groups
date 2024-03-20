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

package com.google.solutions.jitaccess.core.auth;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Allows access to a defined set of principals.
 * @param allowedPrincipals allowed principals
 */
public record AccessControlList(
  @NotNull Set<PrincipalId> allowedPrincipals
){
  /**
   * Check whether a subject is allowed access.
   */
  public boolean isAllowed(@NotNull Subject subject) {
    return this.allowedPrincipals
      .stream()
      .anyMatch(id -> subject.principals().contains(id));
  }
}
