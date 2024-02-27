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

import com.google.api.client.json.GenericJson;
import com.google.common.base.Preconditions;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import dev.cel.common.CelException;
import dev.cel.common.types.CelTypes;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @param condition the CEL condition.
 */
public class IamCondition implements CelExpression<Boolean> {
  private static final CelCompiler COMPILER =
    CelCompilerFactory.standardCelCompilerBuilder()
      .setStandardMacros(CelStandardMacro.ALL)
      .addVar("request", CelTypes.createMap(CelTypes.STRING, CelTypes.ANY))
      .build();

  private static final CelRuntime CEL_RUNTIME =
    CelRuntimeFactory
      .standardCelRuntimeBuilder()
      .build();

  protected final @NotNull String condition;

  public IamCondition(@NotNull String condition) {
    Preconditions.checkNotNull(condition, "condition");
    this.condition = condition;
  }

  //---------------------------------------------------------------------------
  // Overrides.
  //---------------------------------------------------------------------------

  @Override
  public String toString() {
    return this.condition;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof IamCondition other &&
      other != null &&
      other.condition.equals(this.condition);
  }

  //---------------------------------------------------------------------------
  // CelExpression.
  //---------------------------------------------------------------------------

  @Override
  public Boolean evaluate() throws CelException {
    return evaluate(Timestamps.now());
  }

  Boolean evaluate(Timestamp time) throws CelException {
    //
    // Provide default variables.
    //
    var request = new GenericJson()
      .set("time", time);

    var ast = COMPILER.compile(this.condition).getAst();
    return (Boolean)CEL_RUNTIME
      .createProgram(ast)
      .eval(Map.of("request", request));
  }
}
