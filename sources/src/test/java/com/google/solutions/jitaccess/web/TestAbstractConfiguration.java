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

import org.jboss.resteasy.util.DateUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestAbstractConfiguration {

  private class SampleConfiguration extends AbstractConfiguration {
    public SampleConfiguration(@NotNull Map<String, String> settingsData) {
      super(settingsData);
    }
  }

  // -------------------------------------------------------------------------
  // readSetting.
  // -------------------------------------------------------------------------

  @Test
  public void readSetting_whenParseFails() {
    var configuration = new SampleConfiguration(Map.of("TEST", "value"));
    var setting = configuration.readSetting(
      s -> { throw new IllegalArgumentException(); },
      "TEST");

    assertFalse(setting.isPresent());
  }

  @Test
  public void readSetting_whenParseSucceeds() {
    var configuration = new SampleConfiguration(Map.of("TEST", "value"));
    var setting = configuration.readSetting(
      s -> s.toUpperCase(),
      "TEST");

    assertTrue(setting.isPresent());
    assertEquals("VALUE", setting.get());
  }

  // -------------------------------------------------------------------------
  // readStringSetting.
  // -------------------------------------------------------------------------

  @Test
  public void readStringSetting_whenNotSet() {
    var configuration = new SampleConfiguration(Map.of());
    assertFalse(configuration.readStringSetting("TEST").isPresent());
  }

  @Test
  public void readStringSetting_whenEmpty() {
    var configuration = new SampleConfiguration(Map.of("TEST", ""));
    assertFalse(configuration.readStringSetting("TEST").isPresent());
  }

  @Test
  public void readStringSetting_whenPresent() {
    var configuration = new SampleConfiguration(Map.of("TEST", "  1 "));
    assertTrue(configuration.readStringSetting("TEST").isPresent());
    assertEquals("1", configuration.readStringSetting("TEST").get());
  }

  @Test
  public void readStringSetting_whenAliasPresent() {
    var configuration = new SampleConfiguration(Map.of("ALIAS", "  1 "));
    assertTrue(configuration.readStringSetting("TEST", "ALIAS").isPresent());
    assertEquals("1", configuration.readStringSetting("TEST", "ALIAS").get());
  }

  // -------------------------------------------------------------------------
  // readDurationSetting.
  // -------------------------------------------------------------------------

  @Test
  public void readDurationSetting_whenNotSet() {
    var configuration = new SampleConfiguration(Map.of());
    assertFalse(configuration.readDurationSetting(ChronoUnit.SECONDS, "TEST").isPresent());
  }

  @Test
  public void readDurationSetting_whenPresent() {
    var configuration = new SampleConfiguration(Map.of("TEST", "  3 "));
    assertEquals(
      Duration.ofHours(3),
      configuration.readDurationSetting(ChronoUnit.HOURS, "TEST"));
  }
}
