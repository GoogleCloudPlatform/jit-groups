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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.api.services.cloudasset.v1.model.Binding;
import com.google.api.services.cloudasset.v1.model.Expr;
import com.google.solutions.jitaccess.cel.TemporaryIamCondition;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class TestProjectRole {
  private static final String JIT_CONDITION = "has({}.jitAccessConstraint)";
  private static final String MPA_CONDITION = "has({}.multiPartyApprovalConstraint)";
  private static final ProjectId SAMPLE_PROJECT = new ProjectId("project-1");

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toStringReturnsResourceAndRole() {
    var role = new ProjectRole(SAMPLE_PROJECT, "role/sample");
    assertEquals("iam:project-1:role/sample", role.toString());
  }

  // -------------------------------------------------------------------------
  // equals.
  // -------------------------------------------------------------------------

  @Test
  public void whenValueIsEquivalent_ThenEqualsReturnsTrue() {
    var ref1 = new ProjectRole(SAMPLE_PROJECT, "roles/test");
    var ref2 = new ProjectRole(SAMPLE_PROJECT, "roles/test");

    assertTrue(ref1.equals(ref2));
    assertTrue(ref1.equals((Object) ref2));
    assertEquals(ref1.hashCode(), ref2.hashCode());
    assertEquals(ref1.toString(), ref2.toString());
  }

  @Test
  public void whenObjectsAreSame_ThenEqualsReturnsTrue() {
    var role = new ProjectRole(SAMPLE_PROJECT, "roles/test");

    assertTrue(role.equals(role));
    assertTrue(role.equals((Object) role));
    assertEquals(role.hashCode(), role.hashCode());
  }

  @Test
  public void whenRolesDiffer_ThenEqualsReturnsFalse() {
    var role1 = new ProjectRole(new ProjectId("project-1"), "roles/test");
    var role2 = new ProjectRole(new ProjectId("project-1"), "roles/other");

    assertFalse(role1.equals(role2));
    assertFalse(role1.equals((Object) role2));
  }

  @Test
  public void whenResourcesDiffer_ThenEqualsReturnsFalse() {
    var ref1 = new ProjectRole(new ProjectId("project-1"), "roles/test");
    var ref2 = new ProjectRole(new ProjectId("project-2"), "roles/test");

    assertFalse(ref1.equals(ref2));
    assertFalse(ref1.equals((Object) ref2));
  }

  @Test
  public void equalsNullIsFalse() {
    var ref1 = new ProjectRole(SAMPLE_PROJECT, "roles/test");

    assertFalse(ref1.equals(null));
  }

  // -------------------------------------------------------------------------
  // parse.
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {" ", "", " :x", "x: ", "xx" })
  public void whenIdNullOrEmptyOrMalformed_ThenParseThrowsException(String id) {
    assertThrows(
      IllegalArgumentException.class,
      () -> ProjectRole.parse(id));
  }

  @Test
  public void whenIdValid_ThenParseReturnsRole() {
    var role = new ProjectRole(SAMPLE_PROJECT, "roles/test");
    assertEquals(role, ProjectRole.parse(role.id()));
  }

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

  @Test
  public void whenConditionIsActivationConstraint_henFromActivationRoleBindingReturnsRole() {
    var tempCondition = new TemporaryIamCondition(Instant.now(), Duration.ofMinutes(5));
    var activatedRole = ProjectRole.fromActivationRoleBinding(
      SAMPLE_PROJECT,
      new Binding()
        .setRole("roles/test")
        .setCondition(new Expr()
          .setExpression(tempCondition.toString())
          .setTitle(ProjectRole.ActivationCondition.TITLE)));
    assertTrue(activatedRole.isPresent());
    assertEquals(SAMPLE_PROJECT, activatedRole.get().projectRole().projectId());
    assertEquals("roles/test", activatedRole.get().projectRole().role());
    assertEquals(tempCondition.getValidity(), activatedRole.get().activation().validity());
  }
}
