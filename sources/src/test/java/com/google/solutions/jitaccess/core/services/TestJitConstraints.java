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

package com.google.solutions.jitaccess.core.services;

import com.google.api.services.cloudasset.v1.model.Expr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJitConstraints {

  // ---------------------------------------------------------------------
  // isJitAccessConstraint.
  // ---------------------------------------------------------------------

  @Test
  public void whenConditionHasRedundantWhitespace_ThenIsJitAccessConstraintReturnsTrue() {
    var condition = new Expr()
      .setExpression(" \r\n\t has( {  }.jitAccessConstraint \t ) \t \r\n\r");
    assertTrue(JitConstraints.isJitAccessConstraint(condition));
  }

  @Test
  public void whenConditionUsesWrongCase_ThenIsJitAccessConstraintReturnsTrue() {
    var condition = new Expr()
      .setExpression("HAS({}.JitacceSSConstraint)");
    assertTrue(JitConstraints.isJitAccessConstraint(condition));
  }

  @Test
  public void whenConditionIsEmpty_ThenIsJitAccessConstraintReturnsFalse() {
    assertFalse(JitConstraints.isJitAccessConstraint(new Expr().setExpression("")));
    assertFalse(JitConstraints.isJitAccessConstraint(null));
  }

  @Test
  public void whenExpressionIsNull_ThenIsJitAccessConstraintReturnsFalse() {
    var condition = new Expr().setExpression(null);
    assertFalse(JitConstraints.isJitAccessConstraint(condition));
  }

  // ---------------------------------------------------------------------
  // isMultiPartyApprovalConstraint.
  // ---------------------------------------------------------------------

  @Test
  public void whenConditionHasRedundantWhitespace_ThenIsMultiPartyApprovalConstraintTrue() {
    var condition = new Expr()
      .setExpression(" \r\n\t has( { \t\n\r\n }.multiPartyApprovalConstraint \t ) \t \r\n\r");
    assertTrue(JitConstraints.isMultiPartyApprovalConstraint(condition));
  }

  @Test
  public void whenConditionUsesWrongCase_ThenIsMultiPartyApprovalConstraintReturnsTrue() {
    var condition = new Expr()
      .setExpression("HAS({}.MultipARTYapproVALConstraint)");
    assertTrue(JitConstraints.isMultiPartyApprovalConstraint(condition));
  }

  @Test
  public void whenConditionIsEmpty_ThenIsMultiPartyApprovalConstraintReturnsFalse() {
    assertFalse(JitConstraints.isMultiPartyApprovalConstraint(new Expr().setExpression("")));
    assertFalse(JitConstraints.isMultiPartyApprovalConstraint(null));
  }

  @Test
  public void whenExpressionIsNull_ThenIsMultiPartyApprovalConstraintReturnsFalse() {
    var condition = new Expr().setExpression(null);
    assertFalse(JitConstraints.isMultiPartyApprovalConstraint(condition));
  }
}
