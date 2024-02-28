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

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDurationFormatter {
  @Test
  public void format_minutes() {
    assertEquals(
      "0 minutes",
      DurationFormatter.format(Duration.ofMinutes(0)));;
    assertEquals(
      "1 minute",
      DurationFormatter.format(Duration.ofMinutes(1)));;
    assertEquals(
      "2 minutes",
      DurationFormatter.format(Duration.ofMinutes(2)));;
  }

  @Test
  public void hours() {
    assertEquals(
      "1 hour",
      DurationFormatter.format(Duration.ofHours(1)));;
    assertEquals(
      "2 hours, 59 minutes",
      DurationFormatter.format(Duration.ofHours(2).plusMinutes(59)));;
  }

  @Test
  public void days() {
    assertEquals(
      "1 day",
      DurationFormatter.format(Duration.ofDays(1)));
    assertEquals(
      "720 days",
      DurationFormatter.format(Duration.ofDays(720)));
    assertEquals(
      "2 days, 59 minutes",
      DurationFormatter.format(Duration.ofDays(2).plusMinutes(59)));;
  }
}
