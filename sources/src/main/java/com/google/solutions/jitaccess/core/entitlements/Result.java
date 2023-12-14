//
// Copyright 2021 Google LLC
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

package com.google.solutions.jitaccess.core.services;

import com.google.common.base.Preconditions;

import java.util.List;
import java.util.Set;

/**
 * Result list of T with an optional set of warnings.
 */
public class Result<T> {
  /**
   * List of bindings. Might be incomplete if Warnings is non-empty.
   */
  private final List<T> items;

  /**
   * Non-fatal issues encountered. Use a set to avoid duplicates.
   */
  private final Set<String> warnings;

  public Result(List<T> roleBindings, Set<String> warnings) {
    Preconditions.checkNotNull(roleBindings);

    this.items = roleBindings;
    this.warnings = warnings;
  }

  public List<T> getItems() {
    return this.items;
  }

  public Set<String> getWarnings() {
    return warnings;
  }
}
