//
// Copyright 2022 Google LLC
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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestRuntimeConfiguration {
  @Test
  public void whenNotSet_ThenScopeSetToDefault() {
    var settings = Map.of("GOOGLE_CLOUD_PROJECT", "project-1");
    var configuration = new RuntimeConfiguration(settings);

    assertEquals("projects/project-1", configuration.scope.getValue());
  }

  // -------------------------------------------------------------------------
  // Scope settings.
  // -------------------------------------------------------------------------

  @Test
  public void whenNotSet_ThenActivationTimeoutSetToDefault() {
    var configuration = new RuntimeConfiguration(Map.of());

    assertEquals(Duration.ofHours(2), configuration.activationTimeout.getValue());
  }

  @Test
  public void whenSet_ThenScopeReturnsSetting() {
    var settings = Map.of("RESOURCE_SCOPE", "folders/123");
    var configuration = new RuntimeConfiguration(settings);

    assertEquals("folders/123", configuration.scope.getValue());
  }

  // -------------------------------------------------------------------------
  // Activation settings.
  // -------------------------------------------------------------------------

  @Test
  public void whenNotSet_ThenActivationRequestTimeoutSetToDefault() {
    var configuration = new RuntimeConfiguration(Map.of());

    assertEquals(Duration.ofHours(1), configuration.activationRequestTimeout.getValue());
  }

  @Test
  public void whenNotSet_ThenJustificationPatternSetToDefault() {
    var configuration = new RuntimeConfiguration(Map.of());

    assertNotNull(configuration.justificationPattern.getValue());
  }

  @Test
  public void whenNotSet_ThenJustificationHintSetToDefault() {
    var configuration = new RuntimeConfiguration(Map.of());

    assertNotNull(configuration.justificationHint.getValue());
  }

  @Test
  public void whenSet_TheActivationTimeoutReturnsSetting() {
    var settings = Map.of("ELEVATION_DURATION", "30");
    var configuration = new RuntimeConfiguration(settings);

    assertEquals(Duration.ofMinutes(30), configuration.activationTimeout.getValue());
  }

  // -------------------------------------------------------------------------
  // Mail settings.
  // -------------------------------------------------------------------------

}
