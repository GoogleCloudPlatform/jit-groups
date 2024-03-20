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

package com.google.solutions.jitaccess.core.auth;

import com.google.api.client.json.GenericJson;
import com.google.solutions.jitaccess.cel.ExtractFunction;
import com.google.solutions.jitaccess.core.clients.EmailAddress;
import dev.cel.common.CelException;
import dev.cel.common.types.CelTypes;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Maps User IDs to email addresses using a CEL expression.
 */
public class EmailMapping {

  private static final String USER_VARIABLE_NAME = "user";
  private static final CelCompiler CEL_COMPILER =
    CelCompilerFactory.standardCelCompilerBuilder()
      .setStandardMacros(CelStandardMacro.ALL)
      .addVar(USER_VARIABLE_NAME, CelTypes.createMap(CelTypes.STRING, CelTypes.STRING))
      .addFunctionDeclarations(ExtractFunction.DECLARATION)
      .build();

  private static final CelRuntime CEL_RUNTIME =
    CelRuntimeFactory
      .standardCelRuntimeBuilder()
      .addFunctionBindings(ExtractFunction.BINDING)
      .build();

  private @Nullable String celExpression;

  /**
   * Create mapping that uses the user's ID as email address.
   */
  public EmailMapping() {
    this(null);
  }

  /**
   * Create mapping that uses a CEL expression to derive an email address
   * from a user ID.
   */
  public EmailMapping(@Nullable String celExpression) {
    this.celExpression = celExpression;
  }

  /**
   * Map a user ID to an email address.
   */
  public EmailAddress emailFromUserId(UserId userId) throws MappingException {
    if (this.celExpression == null || this.celExpression.isBlank()) {
      //
      // Use the user's ID as email address.
      //
      return new EmailAddress(userId.email);
    }
    else
    {
      //
      // Apply a CEL mapping.
      //

      //
      // Expose user's email as `user.email`.
      //
      var userVariable = new GenericJson().set("email", userId.email);

      try {
        var ast = CEL_COMPILER.compile(this.celExpression).getAst();
        var resultObject = CEL_RUNTIME
          .createProgram(ast)
          .eval(Map.of(USER_VARIABLE_NAME, userVariable));

        if (resultObject == null) {
          throw new MappingException(
            userId,
            "Result is null");
        }
        else if (resultObject instanceof String result) {
          return new EmailAddress(result);
        }
        else {
          throw new MappingException(
            userId,
            String.format("Result is of type '%s' instead of a string", resultObject.getClass()));
        }
      }
      catch (CelException e) {
        throw new MappingException(userId, e);
      }
    }
  }

  public static class MappingException extends RuntimeException {
    public MappingException(UserId input, Exception cause) {
      super(
        String.format(
          "The email mapping expression failed to transform the user ID '%s' into a valid email address",
          input),
        cause);
    }
    public MappingException(UserId input, String issue) {
      super(String.format(
        "The email mapping expression failed to transform the user ID '%s' into a valid email address: %s",
        input,
        issue));
    }
  }
}
