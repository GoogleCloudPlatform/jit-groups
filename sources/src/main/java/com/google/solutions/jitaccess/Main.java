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

package com.google.solutions.jitaccess;

import com.google.solutions.jitaccess.apis.StructuredLogger;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.apis.clients.IamCredentialsClient;
import com.google.solutions.jitaccess.apis.clients.SecretManagerClient;
import com.google.solutions.jitaccess.common.Exceptions;
import com.google.solutions.jitaccess.web.Application;
import com.google.solutions.jitaccess.web.ApplicationConfiguration;
import com.google.solutions.jitaccess.web.EventIds;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

import java.util.HashSet;
import java.util.List;

/**
 * Entry point for the application.
 */
@QuarkusMain
public class Main implements QuarkusApplication {
  public static void main(String... args) {
    try {
      //
      // Create a logger. We can't rely on CDI injection as we're not
      // in a CDI context here.
      //
      var logger = new StructuredLogger(System.out);
      var configuration = new ApplicationConfiguration(System.getenv());

      if (!configuration.isSmtpConfigured()) {
        logger.warn(
          EventIds.STARTUP,
          "The SMTP configuration is incomplete");
      }

      var runtime = ApplicationRuntime.detect(new HashSet<>(List.of(
        IamCredentialsClient.OAUTH_SCOPE,
        SecretManagerClient.OAUTH_SCOPE,
        CloudIdentityGroupsClient.OAUTH_GROUPS_SCOPE,
        CloudIdentityGroupsClient.OAUTH_SETTINGS_SCOPE)));

      //
      // Eagerly initialize the application so that we can fail
      // fast if the configuration is incomplete or invalid.
      //
      // If we relied on CDI to trigger initialization, any
      // configuration issue would cause a failed injection,
      // which in turn produces long, difficult to interpret
      // stack traces.
      //
      Application.initialize(runtime, configuration, logger);

      if (runtime.type() == ApplicationRuntime.Type.DEVELOPMENT) {
        logger.warn(
          EventIds.STARTUP,
          String.format("Running in development mode as %s", runtime.applicationPrincipal()));
      }
      else {
        logger.info(
          EventIds.STARTUP,
          String.format("Running in project %s (%s) as %s, version %s",
            runtime.projectId(),
            runtime.projectNumber(),
            runtime.applicationPrincipal(),
            ApplicationVersion.VERSION_STRING));
      }
    }
    catch (Throwable e) {
      System.err.printf(
        "The application encountered a fatal error during initialization, aborting startup: %s\n\n",
        Exceptions.fullMessage(e));
      e.printStackTrace(System.err);
      System.exit(1);
    }

    //
    // Initialize Quarkus. This will cause the JAX-RS
    // resources to be loaded, which in turn kicks off
    // a cascade of CDI injections.
    //
    Quarkus.run(Main.class, args);
  }

  @Override
  public int run(String... args) {
    Quarkus.waitForExit();
    return 0;
  }
}
