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

package com.google.solutions.jitaccess.core;

import java.io.IOException;
import java.util.Properties;

public class ApplicationVersion {
  public static final String VERSION_STRING;

  public static final String USER_AGENT;

  private static String loadVersion() {
    try {
      //
      // Read properties file from JAR. This file should
      // contain the version number, produced by the Maven
      // resources plugin.
      //
      try (var propertiesFile = ApplicationVersion.class
        .getClassLoader()
        .getResourceAsStream("version.properties"))
      {
        if (propertiesFile != null) {
          var versionProperties = new Properties();
          versionProperties.load(propertiesFile);

          var version = versionProperties.getProperty("application.version");
          if (version != null && version.length() > 0) {
            return version;
          }
        }
      }
    }
    catch (IOException ignored) {
    }

    return "unknown";
  }

  static {
    VERSION_STRING = loadVersion();
    USER_AGENT = "JIT-Access/" + VERSION_STRING;
  }
}
