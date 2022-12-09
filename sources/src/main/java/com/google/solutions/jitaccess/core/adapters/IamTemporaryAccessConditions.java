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

package com.google.solutions.jitaccess.core.adapters;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAmount;
import java.util.regex.Pattern;

/**
 * Helper class for creating temporary access IAM conditions.
 */
public class IamTemporaryAccessConditions {
  private static final String CONDITION_TEMPLATE =
    "(request.time >= timestamp(\"%s\") && " + "request.time < timestamp(\"%s\"))";

  private static final String CONDITION_PATTERN =
    "^\\s*\\(request.time >= timestamp\\(\\\".*\\\"\\) && "
      + "request.time < timestamp\\(\\\".*\\\"\\)\\)\\s*$";

  private static final Pattern CONDITION = Pattern.compile(CONDITION_PATTERN);

  private IamTemporaryAccessConditions() {
  }

  public static boolean isTemporaryAccessCondition(String expression) {
    return expression != null && CONDITION.matcher(expression).matches();
  }

  public static String createExpression(Instant startTime, Instant endTime) {
    assert (startTime.isBefore(endTime));

    var clause = String.format(
      CONDITION_TEMPLATE,
      startTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME),
      endTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME));

    assert (isTemporaryAccessCondition(clause));

    return clause;
  }

  public static String createExpression(
    Instant startTime,
    TemporalAmount duration
  ) {
    return createExpression(startTime, startTime.plus(duration));
  }
}
