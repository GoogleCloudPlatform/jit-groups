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

package com.google.solutions.jitaccess.catalog.policy;

import com.google.api.client.json.GenericJson;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.solutions.jitaccess.cel.Cel;
import com.google.solutions.jitaccess.cel.EvaluationException;
import com.google.solutions.jitaccess.cel.Expression;
import com.google.solutions.jitaccess.cel.InvalidExpressionException;
import dev.cel.common.CelIssue;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.common.types.CelTypes;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Constraint that executes a CEL expression.
 */
public class CelConstraint implements Constraint {
  static final String NAME_PATTERN = "[a-zA-Z0-9\\-]+";

  private static final CelRuntime CEL_RUNTIME = Cel.createRuntime();

  private final @NotNull String name;
  private final @NotNull String displayName;
  private final @NotNull Collection<Variable> variableDeclarations;
  private final @NotNull String expression;

  public CelConstraint(
    @NotNull String name,
    @NotNull String displayName,
    @NotNull Collection<Variable> variables,
    @NotNull String expression
  ) {
    Preconditions.checkArgument(
      name.matches(NAME_PATTERN),
      "Constraint names must only contain letters, numbers, and hyphens");
    Preconditions.checkArgument(
      !Strings.isNullOrEmpty(expression),
      "The expression must not be empty");

    this.name = name;
    this.displayName = displayName;
    this.variableDeclarations = variables;
    this.expression = expression;
  }

  @Override
  public String toString() {
    return String.format("%s [%s]", this.name, this.expression);
  }

  //---------------------------------------------------------------------------
  // Constraint.
  //---------------------------------------------------------------------------

  /**
   * Unique name.
   */
  @Override
  public @NotNull String name() {
    return this.name;
  }

  /**
   * Display name.
   */
  @Override
  public @NotNull String displayName() {
    return this.displayName;
  }

  /**
   * Variables used by the CEL expression.
   */
  public @NotNull Collection<Variable> variables() {
    return this.variableDeclarations;
  }

  /**
   * The CEL expression.
   */
  public @NotNull String expression() {
    return this.expression;
  }

  /**
   * Create a check object that can be used to evaluate
   * the constraint.
   */
  @Override
  public Constraint.Check createCheck() {
    return new Check();
  }

  /**
   * Lint the expression without evaluating it.
   */
  Collection<CelIssue> lint() {
    return new Check().compile().getAllIssues();
  }

  private class Check implements Constraint.Check {
    private final @NotNull Map<String, GenericJson> variables = new HashMap<>();
    private final @NotNull List<Property> input;

    public Check() {
      var json = new GenericJson();
      this.variables.put("input", json);

      this.input = CelConstraint.this.variableDeclarations.stream()
        .map(v -> v.bind(json))
        .toList();
    }

    @Override
    public @NotNull Constraint constraint() {
      return CelConstraint.this;
    }

    @Override
    public @NotNull List<Property> input() {
      return this.input;
    }

    @Override
    public @NotNull Expression.Context addContext(@NotNull String name) {
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

    private CelValidationResult compile() {
      //
      // Allow all the standard macros like has().
      //
      var compiler = Cel.createCompilerBuilder();

      for (var variable : this.variables.keySet()) {
        compiler.addVar(variable, CelTypes.createMap(CelTypes.STRING, CelTypes.ANY));
      }

      return compiler.build().compile(CelConstraint.this.expression);
    }

    @Override
    public @NotNull Boolean evaluate() throws EvaluationException {
      for (var input : this.input) {
        if (input.isRequired() && input.get() == null) {
          throw new IllegalArgumentException(
            String.format("Input missing for '%s'", input.displayName()));
        }
      }

      try {
        var ast = compile().getAst();

        return (Boolean)CEL_RUNTIME
          .createProgram(ast)
          .eval(this.variables);

      } catch (CelValidationException | CelEvaluationException e) {
        throw new InvalidExpressionException(
          String.format("The CEL expression '%s' is invalid", CelConstraint.this.expression),
          e);
      }
    }
  }

  /**
   * Input variable expected by a CEL expression.
   */
  public static abstract class Variable {
    /**
     * Pattern for variable names in CEL.
     */
    private static final String NAME_PATTERN = "[A-Za-z\\_]+[A-Za-z0-9\\_]*";

    private final @NotNull String name;
    private final @NotNull String displayName;

    protected Variable(@NotNull String name, @NotNull String displayName) {
      Preconditions.checkArgument(
        name.matches(NAME_PATTERN),
        "Variable names must be alphanumeric");
      Preconditions.checkArgument(
        !name.startsWith("_"),
        "Variable names with leading underscores are reserved");

      this.name = name;
      this.displayName = displayName;
    }

    /**
     * Name of the variable as referred to in CEL.
     */
    public @NotNull String name() {
      return this.name;
    }

    /**
     * Display name of the variable. This name is not used in
     * the CEL expression itself.
     */
    public @NotNull String displayName() {
      return this.displayName;
    }

    /**
     * Wrap variable as a property.
     */
    protected abstract @NotNull Property bind(@NotNull GenericJson json);
  }

  /**
   * String-typed variable.
   */
  public static class StringVariable extends Variable {
    private final int minLength;
    private final int maxLength;

    public StringVariable(
      @NotNull String name,
      @NotNull String displayName,
      int minLength,
      int maxLength
    ) {
      super(name, displayName);

      Preconditions.checkArgument(
        minLength <= maxLength,
        "The minimum length must be smaller than the maximum length");

      this.minLength = minLength;
      this.maxLength = maxLength;
    }

    public int minLength() {
      return this.minLength;
    }

    public int maxLength() {
      return this.maxLength;
    }

    @Override
    public @NotNull Property bind(@NotNull GenericJson json) {
      return new AbstractStringProperty(this.name(), this.displayName(), true, this.minLength, this.maxLength) {
        @Override
        protected void setCore(@Nullable String value) {
          json.set(this.name(), value);
        }

        @Override
        protected @Nullable String getCore() {
          return (String)json.get(this.name());
        }
      };
    }
  }

  /**
   * Long-typed variable.
   *
   * We use Long instead of Integer because cel-java lacks built-in operators
   * (like <=) for Integer.
   */
  public static class LongVariable extends Variable {
    private final Long minInclusive;
    private final Long maxInclusive;

    public LongVariable(
      @NotNull String name,
      @NotNull String displayName,
      @Nullable Long minInclusive,
      @Nullable Long maxInclusive
    ) {
      super(name, displayName);

      Preconditions.checkArgument(
        minInclusive <= maxInclusive,
        "The minimum value must be smaller than the maximum value");

      this.minInclusive = minInclusive;
      this.maxInclusive = maxInclusive;
    }

    public Long minInclusive() {
      return this.minInclusive;
    }

    public Long maxInclusive() {
      return this.maxInclusive;
    }

    @Override
    public @NotNull Property bind(@NotNull GenericJson json) {
      return new AbstractLongProperty(this.name(), this.displayName(), true, this.minInclusive, this.maxInclusive) {
        @Override
        protected void setCore(@Nullable Long value) {
          json.set(this.name(), value);
        }

        @Override
        protected @Nullable Long getCore() {
          return (Long)json.get(this.name());
        }
      };
    }
  }

  /**
   * Boolean-typed variable.
   */
  public static class BooleanVariable extends Variable {
    public BooleanVariable(
      @NotNull String name,
      @NotNull String displayName
    ) {
      super(name, displayName);
    }

    @Override
    public @NotNull Property bind(@NotNull GenericJson json) {
      return new AbstractBooleanProperty(this.name(), this.displayName(), true) {
        @Override
        protected void setCore(@Nullable Boolean value) {
          json.set(this.name(), value);
        }

        @Override
        protected @Nullable Boolean getCore() {
          return (Boolean)json.get(this.name());
        }
      };
    }
  }
}
