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

package com.google.solutions.jitaccess.catalog.auth;

import com.google.api.client.json.GenericJson;
import com.google.solutions.jitaccess.cel.Cel;
import dev.cel.common.CelException;
import dev.cel.common.types.CelTypes;
import dev.cel.compiler.CelCompiler;
import dev.cel.runtime.CelRuntime;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Maps User IDs to email addresses using a CEL expression.
 */
public class EmailMapping {

  private static final String PRINCIPAL_VARIABLE_NAME = "principal";
  private static final String USER_VARIABLE_NAME = "user";

  private static final CelCompiler CEL_COMPILER = Cel.createCompilerBuilder()
      .addVar(USER_VARIABLE_NAME, CelTypes.createMap(CelTypes.STRING, CelTypes.STRING))
      .addVar(PRINCIPAL_VARIABLE_NAME, CelTypes.createMap(CelTypes.STRING, CelTypes.STRING))
      .build();

  private static final CelRuntime CEL_RUNTIME = Cel.createRuntime();

  private final @Nullable String celExpression;

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
   * Map a principal ID to an email address.
   *
   * @throws MappingException when mapping fails.
   */
  public @NotNull EmailAddress emailFromPrincipalId(IamPrincipalId id) throws MappingException {
    if (this.celExpression == null || this.celExpression.isBlank()) {
      //
      // Use the user's ID as email address.
      //
      return new EmailAddress(id.value());
    }
    else
    {
      //
      // Apply a CEL mapping.
      //

      //
      // Expose principal email as `user.email`, this is for
      // backwards compatibility only.
      //
      var userVariable = new GenericJson()
        .set("email", id.value());

      var principalVariable = new GenericJson()
        .set("type", id.type())
        .set("id", id.value());

      try {
        var ast = CEL_COMPILER.compile(this.celExpression).getAst();
        var resultObject = CEL_RUNTIME
          .createProgram(ast)
          .eval(Map.of(
            USER_VARIABLE_NAME, userVariable,
            PRINCIPAL_VARIABLE_NAME, principalVariable));

        if (resultObject == null) {
          throw new MappingException(
            id,
            "Result is null");
        }
        else if (resultObject instanceof String result) {
          return new EmailAddress(result);
        }
        else {
          throw new MappingException(
            id,
            String.format("Result is of type '%s' instead of a string", resultObject.getClass()));
        }
      }
      catch (CelException e) {
        throw new MappingException(id, e);
      }
    }
  }

  public static class MappingException extends RuntimeException {
    MappingException(IamPrincipalId input, Exception cause) {
      super(
        String.format(
          "The email mapping expression failed to transform the principal ID '%s' into a valid email address",
          input),
        cause);
    }
    MappingException(IamPrincipalId input, String issue) {
      super(String.format(
        "The email mapping expression failed to transform the principal ID '%s' into a valid email address: %s",
        input,
        issue));
    }
  }
}
