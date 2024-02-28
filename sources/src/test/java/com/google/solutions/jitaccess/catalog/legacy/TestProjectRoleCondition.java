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

package com.google.solutions.jitaccess.catalog.legacy;

import com.google.api.services.cloudasset.v1.model.Expr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestProjectRoleCondition {

  // ---------------------------------------------------------------------
  // EligibilityCondition.
  // ---------------------------------------------------------------------

  @Test
  public void whenExpressionIsNullOrEmpty_ThenParseEligibilityConditionReturnsEmpty() {
    assertFalse(ProjectRole.EligibilityCondition
      .parse((Expr)null)
      .isPresent());
    assertFalse(ProjectRole.EligibilityCondition
      .parse(new Expr().setExpression(null))
      .isPresent());
    assertFalse(ProjectRole.EligibilityCondition
      .parse(new Expr().setExpression(""))
      .isPresent());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    " 1<2",
    "HAS({}.JitacceSSConstraint) || 1<2"
  })
  public void whenExpressionIsUnknown_ThenParseEligibilityConditionReturnsEmpty(String expression) {
    var condition = new Expr().setExpression(expression);
    assertFalse(ProjectRole.EligibilityCondition.parse(condition).isPresent());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    " \r\n\t has( {  }.jitAccessConstraint \t ) \t \r\n\r",
    "HAS({}.JitacceSSConstraint)"
  })
  public void whenExpressionIsJitConstraint_ThenParseEligibilityConditionReturns(String expression) {
    var condition = ProjectRole.EligibilityCondition
      .parse(new Expr().setExpression(expression))
      .get();

    assertTrue(condition.isJitEligible());
    assertFalse(condition.isMpaEligible());
    assertNull(condition.resourceCondition());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    " \r\n\t has( {  }.multiPartyApprovalConstraint \t ) \t \r\n\r",
    "HAS({}.MultipARTYapproVALConstraint)"
  })
  public void whenExpressionIsMpaConstraint_ThenParseEligibilityConditionReturns(String expression) {
    var condition = ProjectRole.EligibilityCondition
      .parse(new Expr().setExpression(expression))
      .get();

    assertFalse(condition.isJitEligible());
    assertTrue(condition.isMpaEligible());
    assertNull(condition.resourceCondition());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "HAS({}.JitacceSSConstraint) && a>b     && b <    c",
    "a>b && HAS({}.JitacceSSConstraint)&& b <    c",
    "a>b&&b<c&&HAS({}.JitacceSSConstraint)  ",
    "      \ra>\n b && HAS({}.JitacceSSConstraint)&& b \r\n<\t \t\n    c\t\n",
  })
  public void whenExpressionHasResourceCondition_ThenParseEligibilityConditionExtractsResourceCondition(String expression) {
    var condition = ProjectRole.EligibilityCondition
      .parse(new Expr().setExpression(expression))
      .get();

    assertTrue(condition.isJitEligible());
    assertFalse(condition.isMpaEligible());
    assertEquals("a > b && b < c", condition.resourceCondition());
  }
}