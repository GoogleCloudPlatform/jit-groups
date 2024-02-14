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

package com.google.solutions.jitaccess.cel;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * IAM condition that checks for date range.
 */
public class TemporaryIamCondition extends IamCondition {
  private static final String CONDITION_TEMPLATE =
    "(request.time >= timestamp(\"%s\") && " + "request.time < timestamp(\"%s\"))";

  private static final String CONDITION_PATTERN =
    "^\\s*\\(request.time >= timestamp\\(\\\"(.*)\\\"\\) && "
      + "request.time < timestamp\\(\\\"(.*)\\\"\\)\\)\\s*$";

  private static final Pattern CONDITION = Pattern.compile(CONDITION_PATTERN);

  public TemporaryIamCondition(Instant startTime, Instant endTime) {
    super(String.format(
      CONDITION_TEMPLATE,
      startTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME),
      endTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)));
  }

  public TemporaryIamCondition(Instant startTime, Duration duration) {
   this(startTime, startTime.plus(duration));
  }

  /**
   * Check if the expression is a temporary access IAM condition.
   */
  public static boolean isTemporaryAccessCondition(String expression) {
    return expression != null && CONDITION.matcher(expression).matches();
  }
}
