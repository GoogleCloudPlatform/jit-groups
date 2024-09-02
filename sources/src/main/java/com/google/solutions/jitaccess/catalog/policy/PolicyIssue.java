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

package com.google.solutions.jitaccess.catalog.policy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An issue related to a policy.
 */
public interface PolicyIssue {
  /**
   * Check whether this is an error or merely a warning.
   */
  boolean severe();

  /**
   * Return scope of the issue, typically referring to a part of the policy.
   */
  @Nullable String scope();

  /**
   * Return details about the issue.
   */
  @NotNull String details();

  /**
   * Return details about the issue.
   */
  @NotNull String toString();

  /**
   * Code classifying the issue.
   */
  @NotNull Code code();

  enum Code {
    FILE_INVALID,
    FILE_INVALID_SYNTAX,
    FILE_INVALID_VERSION,
    FILE_UNKNOWN_PROPERTY,
    ENVIRONMENT_MISSING,
    ENVIRONMENT_INVALID,
    SYSTEM_INVALID,
    GROUP_INVALID,
    ACL_INVALID_PRINCIPAL,
    ACL_INVALID_PERMISSION,
    CONSTRAINT_INVALID_VARIABLE_DECLARATION,
    CONSTRAINT_INVALID_TYPE,
    CONSTRAINT_INVALID_EXPIRY,
    CONSTRAINT_INVALID_EXPRESSION,
    PRIVILEGE_INVALID_RESOURCE_ID,
    PRIVILEGE_DUPLICATE_RESOURCE_ID,
    PRIVILEGE_INVALID_ROLE,
    OTHER
  }
}
