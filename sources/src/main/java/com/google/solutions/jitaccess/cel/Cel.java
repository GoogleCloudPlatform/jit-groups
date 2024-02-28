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

package com.google.solutions.jitaccess.cel;

import dev.cel.compiler.CelCompilerBuilder;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.extensions.CelExtensions;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeFactory;

/**
 * Factory for CEL compilers and runtimes.
 */
public class Cel {
  private Cel() {}

  /**
   * Create a CelRuntimeBuilder that uses a common set of default
   * extensions.
   */
  public static CelRuntimeBuilder createRuntimeBuilder() {
    return CelRuntimeFactory
      .standardCelRuntimeBuilder()
      .addFunctionBindings(ExtractFunction.BINDING)
      .addLibraries(CelExtensions.strings())
      .addLibraries(CelExtensions.encoders());
  }

  /**
   * Create a CelRuntime that uses a common set of default
   * extensions.
   */
  public static CelRuntime createRuntime() {
    return createRuntimeBuilder().build();
  }

  /**
   * Create a CelCompilerBuilder that uses a common set of default
   * extensions.
   */
  public static CelCompilerBuilder createCompilerBuilder() {
    return CelCompilerFactory.standardCelCompilerBuilder()
      .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
      .addFunctionDeclarations(ExtractFunction.DECLARATION)
      .addLibraries(CelExtensions.strings())
      .addLibraries(CelExtensions.encoders());
  }
}
