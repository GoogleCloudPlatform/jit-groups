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

import com.google.api.services.cloudasset.v1.model.Binding;
import com.google.api.services.cloudasset.v1.model.Expr;
import com.google.solutions.jitaccess.apis.ProjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestProjectRole {
  private static final String JIT_CONDITION = "has({}.jitAccessConstraint)";
  private static final String MPA_CONDITION = "has({}.multiPartyApprovalConstraint)";
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");

  // -------------------------------------------------------------------------
  // fromJitEligibleRoleBinding.
  // -------------------------------------------------------------------------

  @Test
  public void whenConditionNull_ThenFromJitEligibleRoleBindingReturnsEmpty() {
    var projectRole = ProjectRole.fromJitEligibleRoleBinding(
      SAMPLE_PROJECT,
      new Binding()
        .setRole("roles/test")
        .setCondition(null));
    assertFalse(projectRole.isPresent());
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", ""})
  public void whenConditionEmpty_ThenFromJitEligibleRoleBindingReturnsEmpty(String condition) {
    var projectRole = ProjectRole.fromJitEligibleRoleBinding(
      SAMPLE_PROJECT,
      new Binding()
        .setRole("roles/test")
        .setCondition(new Expr().setExpression(condition)));
    assertFalse(projectRole.isPresent());
  }

  @Test
  public void whenConditionIsJitConstraint_ThenFromJitEligibleRoleBindingReturnsRole() {
    var projectRole = ProjectRole.fromJitEligibleRoleBinding(
      SAMPLE_PROJECT,
      new Binding()
        .setRole("roles/test")
        .setCondition(new Expr().setExpression(JIT_CONDITION)));
    assertTrue(projectRole.isPresent());
    assertEquals(SAMPLE_PROJECT, projectRole.get().projectId());
    assertEquals("roles/test", projectRole.get().role());
  }

  @Test
  public void whenConditionContainsResourceCondition_ThenFromJitEligibleRoleBindingReturnsRole() {
    var projectRole = ProjectRole.fromJitEligibleRoleBinding(
      SAMPLE_PROJECT,
      new Binding()
        .setRole("roles/test")
        .setCondition(new Expr().setExpression(
          "// Require approval\n" +
            "has({}.jitAccessConstraint) &&\n" +
            "\n" +
            "// Disks only\n" +
            "resource.type == \"compute.googleapis.com/Disk\"\n")));
    assertTrue(projectRole.isPresent());
    assertEquals(SAMPLE_PROJECT, projectRole.get().projectId());
    assertEquals("roles/test", projectRole.get().role());
    assertEquals("resource.type == \"compute.googleapis.com/Disk\"", projectRole.get().resourceCondition());
  }

  // -------------------------------------------------------------------------
  // fromMpaEligibleRoleBinding.
  // -------------------------------------------------------------------------

  @Test
  public void whenConditionNull_ThenFromMpaEligibleRoleBindingReturnsEmpty() {
    var projectRole = ProjectRole.fromMpaEligibleRoleBinding(
      SAMPLE_PROJECT,
      new Binding()
        .setRole("roles/test")
        .setCondition(null));
    assertFalse(projectRole.isPresent());
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", ""})
  public void whenConditionEmpty_ThenFromMpaEligibleRoleBindingReturnsEmpty(String condition) {
    var projectRole = ProjectRole.fromMpaEligibleRoleBinding(
      SAMPLE_PROJECT,
      new Binding()
        .setRole("roles/test")
        .setCondition(new Expr().setExpression(condition)));
    assertFalse(projectRole.isPresent());
  }

  @Test
  public void whenConditionIsMpaConstraint_ThenFromMpaEligibleRoleBindingReturnsRole() {
    var projectRole = ProjectRole.fromMpaEligibleRoleBinding(
      SAMPLE_PROJECT,
      new Binding()
        .setRole("roles/test")
        .setCondition(new Expr().setExpression(MPA_CONDITION)));
    assertTrue(projectRole.isPresent());
    assertEquals(SAMPLE_PROJECT, projectRole.get().projectId());
    assertEquals("roles/test", projectRole.get().role());
  }

  @Test
  public void whenConditionContainsResourceCondition_ThenFromMpaEligibleRoleBindingReturnsRole() {
    var projectRole = ProjectRole.fromMpaEligibleRoleBinding(
      SAMPLE_PROJECT,
      new Binding()
        .setRole("roles/test")
        .setCondition(new Expr().setExpression(
          "// Require approval\n" +
            "has({}.multiPartyApprovalConstraint) &&\n" +
            "\n" +
            "// Disks only\n" +
            "resource.type == \"compute.googleapis.com/Disk\"\n")));
    assertTrue(projectRole.isPresent());
    assertEquals(SAMPLE_PROJECT, projectRole.get().projectId());
    assertEquals("roles/test", projectRole.get().role());
    assertEquals("resource.type == \"compute.googleapis.com/Disk\"", projectRole.get().resourceCondition());
  }

  // -------------------------------------------------------------------------
  // fromActivationRoleBinding.
  // -------------------------------------------------------------------------

  @Test
  public void whenConditionNull_ThenFromActivationRoleBindingReturnsEmpty() {
    var projectRole = ProjectRole.fromMpaEligibleRoleBinding(
      SAMPLE_PROJECT,
      new Binding()
        .setRole("roles/test")
        .setCondition(null));
    assertFalse(projectRole.isPresent());
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", ""})
  public void whenConditionEmpty_ThenFromActivationRoleBindingReturnsEmpty(String value) {
    var projectRole = ProjectRole.fromMpaEligibleRoleBinding(
      SAMPLE_PROJECT,
      new Binding()
        .setRole("roles/test")
        .setCondition(new Expr()
          .setExpression(value)
          .setTitle(value)));
    assertFalse(projectRole.isPresent());
  }
}