//
// Copyright 2023 Google LLC
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

package com.google.solutions.jitaccess.core.catalog;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.solutions.jitaccess.core.auth.UserId;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Policy that checks justifications against a Regex pattern.
 */
@Singleton
public class RegexJustificationPolicy implements JustificationPolicy {
  private final @NotNull Options options;

  public RegexJustificationPolicy(@NotNull Options options) {
    Preconditions.checkNotNull(options, "options");
    this.options = options;
  }

  @Override
  public void checkJustification(
    @NotNull UserId user,
    @Nullable String justification
  ) throws InvalidJustificationException {
    if (
      Strings.isNullOrEmpty(justification) ||
      !this.options.justificationPattern.matcher(justification).matches()) {
      throw new InvalidJustificationException(
        String.format("Justification does not meet criteria: %s", this.options.justificationHint));
    }
  }

  @Override
  public String hint() {
    return this.options.justificationHint();
  }

  public record Options(
    String justificationHint,
    Pattern justificationPattern
  ){
  }
}
