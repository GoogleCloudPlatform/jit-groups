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

package com.google.solutions.jitaccess.web;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Base class for reading configuration settings.
 */
public abstract class AbstractConfiguration {
  /**
   * Raw settings data, typically sourced from the environment block.
   */
  private final @NotNull Map<String, String> settingsData;

  public AbstractConfiguration(@NotNull Map<String, String> settingsData) {
    this.settingsData = settingsData;
  }

  /**
   * Read and parse a setting.
   *
   * @return Optional. Empty if the setting isn't present, empty, or invalid.
   */
  protected @NotNull <T> Optional<T> readSetting(
    @NotNull Function<String, T> parse,
    @NotNull String... keys
  ) {
    return Stream.of(keys)
      .map(this.settingsData::get)
      .filter(v -> v != null)
      .map(String::trim)
      .filter(v -> !v.isBlank())
      .flatMap(v -> {
        try {
          return Stream.of(parse.apply(v));
        }
        catch (Exception e) {
          return Stream.empty();
        }
      })
      .findFirst();
  }

  /**
   * Read and parse a string-typed setting.
   */
  protected @NotNull Optional<String> readStringSetting(
    @NotNull String... keys
  ) {
    return readSetting(s -> s, keys);
  }

  /**
   * Read and parse a Duration-typed setting.
   */
  protected @NotNull Optional<Duration> readDurationSetting(
    @NotNull ChronoUnit unit,
    @NotNull String... keys
  ) {
    return readSetting(
      s -> Duration.of(Integer.parseInt(s), unit),
      keys);
  }
}
