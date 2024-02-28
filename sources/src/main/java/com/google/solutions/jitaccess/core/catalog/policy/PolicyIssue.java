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

package com.google.solutions.jitaccess.core.catalog.policy;

import org.jetbrains.annotations.NotNull;

/**
 * Warning or error affecting a policy.
 * @param error indicates if this is a fatal error
 * @param code unique code for the issue
 * @param description textual description
 */
public record PolicyIssue(
  boolean error,
  @NotNull Code code,
  @NotNull String details) {

  public enum Code {
    FILE_INVALID_SYNTAX,
    POLICY_INVALID_ID,
    POLICY_DUPLICATE_ID,
    POLICY_MISSING_NAME,
    POLICY_MISSING_ROLES,

    ROLE_INVALID_ID,
    ROLE_MISSING_NAME,
    ROLE_MISSING_ACCESS,

    ACCESS_INVALID_PRINCIPAL,
    ACCESS_INVALID_EFFECT,
    ACCESS_INVALID_ACTION,

    CONSTRAINT_DURATION_CONSTRAINTS_MISSING,
    CONSTRAINT_DURATION_CONSTRAINT_EMPTY,
    CONSTRAINT_DURATION_CONSTRAINT_INVALID,
    CONSTRAINT_APPROVAL_CONSTRAINTS_MISSING,
    CONSTRAINT_APPROVAL_LIMITS_MISSING,
    CONSTRAINT_APPROVAL_LIMITS_INVALID
  }
}
