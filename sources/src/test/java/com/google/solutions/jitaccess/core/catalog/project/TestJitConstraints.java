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
  // isPeerApprovalConstraint.
  // ---------------------------------------------------------------------

  @Test
  public void whenConditionHasRedundantWhitespace_ThenIsPeerApprovalConstraintTrue() {
    var condition = new Expr()
      .setExpression(" \r\n\t has( { \t\n\r\n }.peerApprovalConstraint \t ) \t \r\n\r");
    assertTrue(JitConstraints.isPeerApprovalConstraint(condition));
  }

  @Test
  public void whenConditionUsesWrongCase_ThenIsPeerApprovalConstraintReturnsTrue() {
    var condition = new Expr()
      .setExpression("HAS({}.pEErapproVALConstraint)");
    assertTrue(JitConstraints.isPeerApprovalConstraint(condition));
  }

  @Test
  public void whenConditionIsEmpty_ThenIsPeerApprovalConstraintReturnsFalse() {
    assertFalse(JitConstraints.isPeerApprovalConstraint(new Expr().setExpression("")));
    assertFalse(JitConstraints.isPeerApprovalConstraint(null));
  }

  @Test
  public void whenExpressionIsNull_ThenIsPeerApprovalConstraintReturnsFalse() {
    var condition = new Expr().setExpression(null);
    assertFalse(JitConstraints.isPeerApprovalConstraint(condition));
  }

  // ---------------------------------------------------------------------
  // isExternalApprovalConstraint.
  // ---------------------------------------------------------------------

  @Test
  public void whenConditionHasRedundantWhitespace_ThenIsExternalApprovalConstraintTrue() {
    var condition = new Expr()
      .setExpression(" \r\n\t has( { \t\n\r\n }.externalApprovalConstraint \t ) \t \r\n\r");
    assertTrue(JitConstraints.isExternalApprovalConstraint(condition));
  }

  @Test
  public void whenConditionUsesWrongCase_ThenIsExternalApprovalConstraintReturnsTrue() {
    var condition = new Expr()
      .setExpression("HAS({}.exTErNaLapproVALConstraint)");
    assertTrue(JitConstraints.isExternalApprovalConstraint(condition));
  }

  @Test
  public void whenConditionIsEmpty_ThenIsExternalApprovalConstraintReturnsFalse() {
    assertFalse(JitConstraints.isExternalApprovalConstraint(new Expr().setExpression("")));
    assertFalse(JitConstraints.isExternalApprovalConstraint(null));
  }

  @Test
  public void whenExpressionIsNull_ThenIsExternalApprovalConstraintReturnsFalse() {
    var condition = new Expr().setExpression(null);
    assertFalse(JitConstraints.isExternalApprovalConstraint(condition));
  }


  // ---------------------------------------------------------------------
  // isReviewerConstraint.
  // ---------------------------------------------------------------------

  @Test
  public void whenConditionHasRedundantWhitespace_ThenIsReviewerConstraintTrue() {
    var condition = new Expr()
      .setExpression(" \r\n\t has( { \t\n\r\n }.reviewerPrivilege \t ) \t \r\n\r");
    assertTrue(JitConstraints.isReviewerConstraint(condition));
  }

  @Test
  public void whenConditionUsesWrongCase_ThenIsReviewerConstraintReturnsTrue() {
    var condition = new Expr()
      .setExpression("HAS({}.reviewERPrivIlege)");
    assertTrue(JitConstraints.isReviewerConstraint(condition));
  }

  @Test
  public void whenConditionIsEmpty_ThenIsReviewerConstraintReturnsFalse() {
    assertFalse(JitConstraints.isReviewerConstraint(new Expr().setExpression("")));
    assertFalse(JitConstraints.isReviewerConstraint(null));
  }

  @Test
  public void whenExpressionIsNull_ThenIsReviewerConstraintReturnsFalse() {
    var condition = new Expr().setExpression(null);
    assertFalse(JitConstraints.isReviewerConstraint(condition));
  }
}