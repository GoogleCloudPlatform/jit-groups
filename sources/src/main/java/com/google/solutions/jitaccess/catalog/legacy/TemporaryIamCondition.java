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

package com.google.solutions.jitaccess.catalog.legacy;

import dev.cel.common.CelException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * IAM condition that checks for date range.
 */
class TemporaryIamCondition extends IamCondition {
  private static final String CONDITION_TEMPLATE =
    "(request.time >= timestamp(\"%s\") && " + "request.time < timestamp(\"%s\"))";

  private static final String CONDITION_PATTERN =
    "^\\s*[\\(]+request.time >= timestamp\\(\\\"(.*)\\\"\\) && "
      + "request.time < timestamp\\(\\\"(.*)\\\"\\)[\\)]+\\s*$";

  private static final Pattern CONDITION = Pattern.compile(CONDITION_PATTERN);

  //---------------------------------------------------------------------------
  // Constructors.
  //---------------------------------------------------------------------------

  public TemporaryIamCondition(@NotNull Instant startTime, @NotNull Instant endTime) {
    super(String.format(
      CONDITION_TEMPLATE,
      startTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME),
      endTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)));
  }

  public TemporaryIamCondition(@NotNull Instant startTime, @NotNull Duration duration) {
    this(startTime, startTime.plus(duration));
  }

  public TemporaryIamCondition(@NotNull String condition) {
    super(condition);
  }

  //---------------------------------------------------------------------------
  // Publics.
  //---------------------------------------------------------------------------

  public @NotNull TimeSpan getValidity() {
    var matcher = CONDITION.matcher(this.expression);
    if (matcher.find()) {
      try {
        return new TimeSpan(
          Instant.parse(matcher.group(1)),
          Instant.parse(matcher.group(2)));
      }
      catch (DateTimeParseException ignored) {}
    }

    throw new IllegalArgumentException("Condition is not a temporary IAM condition");
  }

  /**
   * Check if the expression is a temporary access IAM condition.
   */
  public static boolean isTemporaryAccessCondition(@Nullable String expression) {
    return expression != null && CONDITION.matcher(expression).matches();
  }

  //---------------------------------------------------------------------------
  // Overrides.
  //---------------------------------------------------------------------------

  @Override
  public Boolean evaluate() throws CelException {
    return isTemporaryAccessCondition(this.expression) && super.evaluate();
  }
}