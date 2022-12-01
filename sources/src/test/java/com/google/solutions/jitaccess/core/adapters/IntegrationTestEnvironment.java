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

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.solutions.jitaccess.core.data.ProjectId;
import com.google.solutions.jitaccess.core.data.UserId;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class IntegrationTestEnvironment {
  private IntegrationTestEnvironment() {
  }

  private static final String SETTINGS_FILE = "test.properties";
  public static final GoogleCredentials INVALID_CREDENTIAL =
    new GoogleCredentials(new AccessToken("ey00", new Date(Long.MAX_VALUE))) {
      @Override
      public void refresh() {
      }
    };

  public static final ProjectId PROJECT_ID;

  public static final GoogleCredentials APPLICATION_CREDENTIALS;
  public static final GoogleCredentials NO_ACCESS_CREDENTIALS;
  public static final GoogleCredentials TEMPORARY_ACCESS_CREDENTIALS;

  public static final UserId TEMPORARY_ACCESS_USER;
  public static final UserId NO_ACCESS_USER;

  static {
    //
    // Open test settings file.
    //
    if (!new File(SETTINGS_FILE).exists()) {
      throw new RuntimeException(
        String.format(
          "Cannot find %s. Create file to specify which test project to use.", SETTINGS_FILE));
    }

    try (FileInputStream in = new FileInputStream(SETTINGS_FILE)) {
      Properties settings = new Properties();
      settings.load(in);

      PROJECT_ID = new ProjectId( getMandatory(settings, "test.project"));

      //
      // Service account that doesn't have access to anything.
      //
      NO_ACCESS_USER = new UserId(
        "no-access",
        String.format("%s@%s.iam.gserviceaccount.com", "no-access", PROJECT_ID));

      //
      // Service account that can be granted temporary access.
      //
      TEMPORARY_ACCESS_USER = new UserId(
        "temporary-access",
        String.format("%s@%s.iam.gserviceaccount.com", "temporary-access", PROJECT_ID));

      APPLICATION_CREDENTIALS = GoogleCredentials.getApplicationDefault();
      NO_ACCESS_CREDENTIALS = impersonate(APPLICATION_CREDENTIALS, NO_ACCESS_USER.email);
      TEMPORARY_ACCESS_CREDENTIALS = impersonate(APPLICATION_CREDENTIALS, TEMPORARY_ACCESS_USER.email);
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to load test settings", e);
    }
  }

  private static String getMandatory(Properties properties, String property) {
    String value = properties.getProperty(property);
    if (value == null || value.isEmpty()) {
      throw new RuntimeException(
        String.format("Settings file %s lacks setting for %s", SETTINGS_FILE, property));
    }

    return value;
  }

  private static GoogleCredentials impersonate(GoogleCredentials source, String serviceAccount) {
    return ImpersonatedCredentials.create(
      source, serviceAccount, null, List.of("https://www.googleapis.com/auth/cloud-platform"), 0);
  }
}
