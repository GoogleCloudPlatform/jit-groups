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

import com.google.solutions.jitaccess.util.Exceptions;
import com.google.solutions.jitaccess.web.Application;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Entry point for the application.
 */
@QuarkusMain
public class Main implements QuarkusApplication {
  public static void main(String... args) {
    //
    // Eagerly initialize the application so that we can fail
    // fast if the configuration is incomplete or invalid.
    //
    // If we relied on CDI to trigger initialization, any
    // configuration issue would cause a failed injection,
    // which in turn produces long, difficult to interpret
    // stack traces.
    //
    try {
      Application.initialize();
    }
    catch (Error e) {
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
