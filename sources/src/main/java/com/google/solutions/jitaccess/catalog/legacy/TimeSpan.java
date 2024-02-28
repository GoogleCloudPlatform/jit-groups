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

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

record TimeSpan(
  @NotNull Instant start,
  @NotNull Instant end
) implements Comparable<TimeSpan> {
  public TimeSpan {
    assert !start.isAfter(end);
  }

  public TimeSpan(@NotNull Instant start, @NotNull Duration duration) {
    this(start, start.plus(duration));
  }

  @Override
  public int compareTo(@NotNull TimeSpan o) {
    return (int)(this.end.getEpochSecond() - o.end.getEpochSecond());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TimeSpan timeSpan = (TimeSpan) o;
    return start.equals(timeSpan.start) && end.equals(timeSpan.end);
  }

  @Override
  public int hashCode() {
    return Objects.hash(start, end);
  }
}