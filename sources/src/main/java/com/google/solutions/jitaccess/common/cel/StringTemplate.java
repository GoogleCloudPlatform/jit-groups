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

package com.google.solutions.jitaccess.common.cel;

import com.google.api.client.json.GenericJson;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.CelTypes;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A template that embeds CEL expressions. Embedded expressions
 * are enclosed in {{ and }} tokens, for example, the embedded
 * expression
 * <p>
 * {{ 1+1 }}
 * <p>
 * would be evaluated to 2.
 */
public class StringTemplate implements Expression<String> {
  private static final Pattern TEMPLATE_PATTERN = Pattern.compile(
    "\\{\\{(.*?)\\}\\}",
    Pattern.DOTALL);

  private static final CelRuntime CEL_RUNTIME = Cel.createRuntime();

  private final @NotNull String template;
  private final @NotNull Map<String, GenericJson> variables = new HashMap<>();

  public StringTemplate(@NotNull String template) {
    this.template = template;
  }

  @Override
  public @NotNull Context addContext(@NotNull String name) {
    //
    // For a CEL constraint, a context is an additional Map<String, ?>-typed
    // variable that we inject into the runtime.
    //
    var json = new GenericJson();
    this.variables.put(name, json);
    return new Context() {
      @Override
      public Context set(@NotNull String name, @NotNull Object value) {
        json.set(name, value);
        return this;
      }
    };
  }

  @Override
  public @NotNull String evaluate() throws EvaluationException {
    //
    // Prepare a compiler, allowing all the standard macros like has().
    //
    var compilerFactory = Cel.createCompilerBuilder();

    for (var variable : this.variables.keySet()) {
      compilerFactory.addVar(variable, CelTypes.createMap(CelTypes.STRING, CelTypes.ANY));
    }

    var compiler = compilerFactory.build();

    //
    // Find {{ }} embedded expressions and evaluate them one by one.
    //
    var output = new StringBuilder();
    var matcher = TEMPLATE_PATTERN.matcher(this.template);

    int lastIndex = 0;
    while (matcher.find()) {
      output.append(this.template, lastIndex, matcher.start());

      var expression = matcher.group(1).trim();
      try {
        var ast = compiler
          .compile(expression)
          .getAst();

        var evaluationResult = CEL_RUNTIME
          .createProgram(ast)
          .eval(this.variables);

        output.append(evaluationResult);
      }
      catch (CelValidationException | CelEvaluationException e) {
        throw new InvalidExpressionException(
          String.format("The CEL expression '%s' is invalid", expression),
          e);
      }

      lastIndex = matcher.end();
    }

    if (lastIndex < this.template.length()) {
      output.append(this.template, lastIndex, this.template.length());
    }

    return output.toString();
  }

  @Override
  public String toString() {
    return this.template;
  }
}
