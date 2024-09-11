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

import com.google.solutions.jitaccess.ApplicationRuntime;
import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.apis.ProjectId;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class TestApplication {
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");

  private Map<String, String> createMandatorySettings() {
    return new HashMap<>(Map.of(
      "PRIMARY_DOMAIN", "  example.com ",
      "ORGANIZATION_ID", "10000000000000001 ",
      "CUSTOMER_ID", " C123 "));
  }

  private static @NotNull ApplicationRuntime createAppEngineRuntime() {
    var runtime = Mockito.mock(ApplicationRuntime.class);
    when(runtime.type()).thenReturn(ApplicationRuntime.Type.APPENGINE);
    when(runtime.projectNumber()).thenReturn("123");
    when(runtime.projectId()).thenReturn(SAMPLE_PROJECT);
    return runtime;
  }

  private static @NotNull ApplicationRuntime createCloudRunRuntime() {
    var runtime = Mockito.mock(ApplicationRuntime.class);
    when(runtime.type()).thenReturn(ApplicationRuntime.Type.CLOUDRUN);
    when(runtime.projectNumber()).thenReturn("123");
    when(runtime.projectId()).thenReturn(SAMPLE_PROJECT);
    return runtime;
  }

  //---------------------------------------------------------------------------
  // produceIapRequestFilterOptions.
  //---------------------------------------------------------------------------

  @Test
  public void produceIapRequestFilterOptions_whenOnAppEngine() {
    Application.initialize(
      createAppEngineRuntime(),
      new ApplicationConfiguration(createMandatorySettings()),
      Mockito.mock(Logger.class));

    var options = new Application().produceIapRequestFilterOptions();
    assertFalse(options.enableDebugAuthentication());
    assertEquals("/projects/123/apps/project-1", options.expectedAudience());
  }

  @Test
  public void produceIapRequestFilterOptions_whenOnCloudRunAndAudienceVerificationEnabled() {
    var settings = createMandatorySettings();
    settings.put("IAP_BACKEND_SERVICE_ID", "999");

    Application.initialize(
      createCloudRunRuntime(),
      new ApplicationConfiguration(settings),
      Mockito.mock(Logger.class));

    var options = new Application().produceIapRequestFilterOptions();
    assertFalse(options.enableDebugAuthentication());
    assertEquals("/projects/123/global/backendServices/999", options.expectedAudience());
  }

  @Test
  public void produceIapRequestFilterOptions_whenOnCloudRunAndAudienceVerificationDisabled() {
    var settings = createMandatorySettings();
    settings.put("IAP_VERIFY_AUDIENCE", "false");

    Application.initialize(
      createCloudRunRuntime(),
      new ApplicationConfiguration(settings),
      Mockito.mock(Logger.class));

    var options = new Application().produceIapRequestFilterOptions();
    assertFalse(options.enableDebugAuthentication());
    assertNull(options.expectedAudience());
  }
}
