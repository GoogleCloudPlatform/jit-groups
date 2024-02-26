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

import java.util.List;

public class PolicyException extends Exception {
  private final @NotNull List<PolicyIssue> issues;

  public @NotNull List<PolicyIssue> getIssues() {
    return issues;
  }

  PolicyException(
    @NotNull String message,
    @NotNull List<PolicyIssue> issues
  ) {
    super(message);
    this.issues = issues;
  }
}
