//
// Copyright 2022 Google LLC
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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.api.services.cloudasset.v1.model.Expr;
import com.google.solutions.jitaccess.cel.TemporaryIamCondition;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

import java.util.regex.Matcher;

/**
 * Helper class for creating and parsing JIT constraints.
 *
 * JIT constraints are IAM conditions that "mark" an IAM binding as (JIT- or MPA-) eligible.
 */
class JitConstraints {
  /** Condition title for activated role bindings */
  public static final String ACTIVATION_CONDITION_TITLE = "JIT access activation";

  /** Condition that marks a role binding as eligible for JIT access */
  private static final Pattern JIT_CONDITION_PATTERN = Pattern
    .compile("^\\s*([^|]|\\(.*\\|*.*\\))*\\s*has\\(\\s*\\{\\s*\\}.jitaccessconstraint\\s*\\)\\s*([^|]|\\(.*\\|*.*\\))*\\s*$");

  /** Condition that marks a role binding as eligible for MPA */
  private static final Pattern MPA_CONDITION_PATTERN = Pattern
    .compile("^\\s*([^|]|\\(.*\\|*.*\\))*\\s*has\\(\\s*\\{\\s*\\}.multipartyapprovalconstraint\\s*\\)\\s*([^|]|\\(.*\\|*.*\\))*\\s*$");

  /** Additional conditions besides activation. They should appear after AND (&&) */
  private static final Pattern ADDITIONAL_CONDITIONS_PATTERN = Pattern
    .compile("\\s*&&[^&]*$");

  /** Temporary condition added after activation */
  private static final String TEMPORARY_CONDITION_PATTERN = 
    "^(\\s*\\(request.time >= timestamp\\(.*\\) && request.time < timestamp\\(.*\\)\\){0,1}\\s*)(.*)$";

  private static final Pattern TEMPORARY_CONDITION = Pattern.compile(TEMPORARY_CONDITION_PATTERN);

  private JitConstraints() {
  }

  private static boolean isConstraint(
    @Nullable Expr iamCondition,
    @NotNull Pattern pattern
  ) {
    if (iamCondition == null) {
      return false;
    }

    if (iamCondition.getExpression() == null) {
      return false;
    }

    // Strip all whitespace to simplify expression matching.
    var expression = iamCondition
      .getExpression()
      .toLowerCase()
      .replace(" ", "");

    return pattern.matcher(expression).matches();
  }

  /** Check if the IAM condition is a JIT Access constraint */
  public static boolean isJitAccessConstraint(Expr iamCondition) {
    return isConstraint(iamCondition, JIT_CONDITION_PATTERN);
  }

  /** Check if the IAM condition is an MPA constraint */
  public static boolean isMultiPartyApprovalConstraint(Expr iamCondition) {
    return isConstraint(iamCondition, MPA_CONDITION_PATTERN);
  }

  /** Check if the IAM condition is a JIT- or MPA constraint */
  public static boolean isApprovalConstraint(
    Expr iamCondition,
    @NotNull ActivationType activationType) {
    switch (activationType) {
      case JIT: return isJitAccessConstraint(iamCondition);
      case MPA: return isMultiPartyApprovalConstraint(iamCondition);
      default: throw new IllegalArgumentException();
    }
  }

  /** Check if the IAM condition indicates an activated role binding */
  public static boolean isActivated(@Nullable Expr iamCondition) {
    return iamCondition != null &&
      ACTIVATION_CONDITION_TITLE.equals(iamCondition.getTitle()) &&
      TemporaryIamCondition.isTemporaryAccessCondition(iamCondition.getExpression());
  }

  // Get the extra conditions that should be added in the final
  // constraints (when the role is activated using JIT)
  public static String getAdditionalConditions(
    String conditionalExpression
  ) {

    if (conditionalExpression == null)
      return "";

    Matcher additionalConditionsMatcher = TEMPORARY_CONDITION.matcher(conditionalExpression);

    if(additionalConditionsMatcher.find()) {
      return additionalConditionsMatcher.group(2);
    } else {
      additionalConditionsMatcher = ADDITIONAL_CONDITIONS_PATTERN.matcher(conditionalExpression);
      return additionalConditionsMatcher.find()? additionalConditionsMatcher.group() : "";
    }
  }

}
