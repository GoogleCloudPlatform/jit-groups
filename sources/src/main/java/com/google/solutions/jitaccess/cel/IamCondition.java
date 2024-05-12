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
import dev.cel.parser.CelUnparserFactory;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A CEL condition.
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

  protected final @NotNull String expression;

  public IamCondition(@NotNull String expression) {
    Preconditions.checkNotNull(expression, "condition");
    this.expression = expression;
  }

  //---------------------------------------------------------------------------
  // Overrides.
  //---------------------------------------------------------------------------

  @Override
  public String toString() {
    return this.expression;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof IamCondition other &&
      other != null &&
      other.expression.equals(this.expression);
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

    var ast = COMPILER.compile(this.expression).getAst();
    return (Boolean)CEL_RUNTIME
      .createProgram(ast)
      .eval(Map.of("request", request));
  }

  /**
   * @return condition using canonical formatting.
   */
  public IamCondition reformat() {
    try {
      var ast = COMPILER.parse(this.expression).getAst();
      return new IamCondition(CelUnparserFactory.newUnparser().unparse(ast));
    }
    catch (CelException e) {
      throw new  IllegalArgumentException("The CEL expression is invalid", e);
    }
  }

  /**
   * Split a condition that consists of multiple AND clauses
   * into its parts.
   */
  public List<IamCondition> splitAnd() {
    //
    // The CEL library doesn't lend itself well for this, so
    // we have to parse the expression manually.
    //

    var clauses = new LinkedList<IamCondition>();
    var currentClause = new StringBuilder();

    //
    // Remove line comments.
    //
    var cleanedExpression = Arrays.stream(this.expression.split("\n"))
      .filter(line -> !line.trim().startsWith("//"))
      .collect(Collectors.joining("\n"));

    var bracesDepth = 0;
    var singleQuotes = 0;
    var doubleQuotes = 0;
    for (int i = 0; i < cleanedExpression.length(); i++)
    {
      var c = cleanedExpression.charAt(i);
      switch (c) {
        case '&':
          if (bracesDepth == 0 && // not inside a nested clause
            (singleQuotes % 2) == 0 && // quotes are balanced
            (doubleQuotes % 2) == 0 && // quotes are balanced
            i + 1 < cleanedExpression.length() &&
            cleanedExpression.charAt(i + 1) == '&') {

            //
            // We've encountered a top-level "&&".
            //
            clauses.addLast(new IamCondition(currentClause.toString()));
            currentClause.setLength(0);

            i++; // Skip the next "&".
            break;
          }
          else {
            currentClause.append(c);
            break;
          }

        case '(':
          bracesDepth++;
          currentClause.append(c);
          break;

        case ')':
          bracesDepth--;
          currentClause.append(c);
          break;

        case '\'':
          singleQuotes++;
          currentClause.append(c);
          break;

        case '"':
          doubleQuotes++;
          currentClause.append(c);
          break;

        default:
          currentClause.append(c);
          break;
      }
    }

    if (currentClause.length() > 0) {
      clauses.addLast(new IamCondition(currentClause.toString()));
    }

    return clauses;
  }

  /**
   * Combine multiple condition clauses into one by AND-combining them.
   */
  public static @NotNull IamCondition and(
    @NotNull Collection<IamCondition> conditions
  ) {
    return new IamCondition(
      String.join(
        " && ", conditions
          .stream()
          .map(c -> String.format("(%s)", c.expression))
          .collect(Collectors.toList())));
  }
}
