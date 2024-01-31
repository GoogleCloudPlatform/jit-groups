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
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.EntitlementType;

import java.util.regex.Pattern;

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
    .compile("^\\s*has\\(\\s*\\{\\s*\\}.jitaccessconstraint\\s*\\)\\s*$");

  /** Condition that marks a role binding as eligible for peer approval */
  private static final Pattern PEER_CONDITION_PATTERN = Pattern
    .compile("^\\s*has\\(\\s*\\{\\s*\\}.peerapprovalconstraint\\s*\\)\\s*$");

  /** Condition that marks a role binding as eligible for requester */
    private static final Pattern REQUESTER_CONDITION_PATTERN = Pattern
    .compile("^\\s*has\\(\\s*\\{\\s*\\}.externalapprovalconstraint\\s*\\)\\s*$");
  
  /** Condition that marks a role binding as eligible for reviewer */
    private static final Pattern REVIEWER_CONDITION_PATTERN = Pattern
    .compile("^\\s*has\\(\\s*\\{\\s*\\}.reviewerprivilege\\s*\\)\\s*$");

  private JitConstraints() {
  }

  private static boolean isConstraint(Expr iamCondition, Pattern pattern) {
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

  /** Check if the IAM condition is a peer approval constraint */
  public static boolean isPeerApprovalConstraint(Expr iamCondition) {
    return isConstraint(iamCondition, PEER_CONDITION_PATTERN);
  }

  /** Check if the IAM condition is an external approval constraint */
  public static boolean isExternalApprovalConstraint(Expr iamCondition) {
    return isConstraint(iamCondition, REQUESTER_CONDITION_PATTERN);
  }

  /** Check if the IAM condition is a reviewer privilege */
  public static boolean isReviewerConstraint(Expr iamCondition) {
    return isConstraint(iamCondition, REVIEWER_CONDITION_PATTERN);
  }

  /** Check if the IAM condition is a valid constraint or privilege. */
  public static boolean isApprovalConstraint(
    Expr iamCondition,
    EntitlementType entitlementType) {
    switch (entitlementType) {
      case JIT: return isJitAccessConstraint(iamCondition);
      case PEER: return isPeerApprovalConstraint(iamCondition);
      case REQUESTER: return isExternalApprovalConstraint(iamCondition);
      case REVIEWER: return isReviewerConstraint(iamCondition);
      default: throw new IllegalArgumentException();
    }
  }

  /** Check if the IAM condition indicates an activated role binding */
  public static boolean isActivated(Expr iamCondition) {
    return iamCondition != null &&
      ACTIVATION_CONDITION_TITLE.equals(iamCondition.getTitle());
  }
}
