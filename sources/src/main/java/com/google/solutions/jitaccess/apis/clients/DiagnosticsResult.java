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

package com.google.solutions.jitaccess.apis.clients;

import org.jetbrains.annotations.NotNull;

/**
 * @param name name of the check that was performed
 * @param successful result of the check
 * @param details error message in case the check failed
 */
public record DiagnosticsResult(
  @NotNull String name,
  boolean successful,
  String details
) {
  public DiagnosticsResult(@NotNull String name) {
    this(name, true, null);
  }

  @Override
  public String toString() {
    if (this.successful) {
      return String.format("%s: OK", this.name);
    }
    else {
      return String.format("%s: %s", this.name, this.details);
    }
  }
}
