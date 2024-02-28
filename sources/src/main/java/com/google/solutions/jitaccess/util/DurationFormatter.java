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

package com.google.solutions.jitaccess.util;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.LinkedList;

/**
 * Utility class for formatting durations.
 */
public class DurationFormatter {
  private DurationFormatter() {}

  private static @NotNull String pluralize(long num, String unit) {
    return String.format(
      "%d %s%s",
      num,
      unit,
      num == 1 ? "" : "s");
  }

  /**
   * Pretty-print a duration in minute precision.
   *
   * @return String in the format "6 days, 21 hours, 2 minutes".
   */
  public static @NotNull String format(@NotNull Duration duration) {
    var parts = new LinkedList<String>();

    long days = duration.toDays();
    if (days > 0) {
      parts.add(pluralize(days, "day"));
    }

    int hours = duration.toHoursPart();
    if (hours > 0) {
      parts.add(pluralize(hours, "hour"));
    }

    int minutes = duration.toMinutesPart();
    if (minutes > 0 || parts.isEmpty()) {
      parts.add(pluralize(minutes, "minute"));
    }

    return String.join(", ", parts);
  }
}
