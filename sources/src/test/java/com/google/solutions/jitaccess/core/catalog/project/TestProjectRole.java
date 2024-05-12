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
    assertEquals(
      "[iam] role/sample on project-1",
      new ProjectRole(SAMPLE_PROJECT, "role/sample").toString());
    assertEquals(
      "[iam] role/sample on project-1 (condition: resource.name=='foo')",
      new ProjectRole(SAMPLE_PROJECT, "role/sample", "resource.name=='foo'").toString());
  }

  // -------------------------------------------------------------------------
  // id.
  // -------------------------------------------------------------------------

  @Test
  public void idContainsResourceAndRole() {
    assertEquals(
      "project-1:role/sample",
      new ProjectRole(SAMPLE_PROJECT, "role/sample").id());
    assertEquals(
      "project-1:role/sample:cmVzb3VyY2UubmFtZT09J2Zvbyc=",
      new ProjectRole(SAMPLE_PROJECT, "role/sample", "resource.name=='foo'").id());
  }

  // -------------------------------------------------------------------------
  // equals.
  // -------------------------------------------------------------------------

  @Test
  public void whenValueIsEquivalent_ThenEqualsReturnsTrue() {
    var role1 = new ProjectRole(SAMPLE_PROJECT, "roles/test");
    var role2 = new ProjectRole(SAMPLE_PROJECT, "roles/test");

    assertTrue(role1.equals(role2));
    assertTrue(role1.equals((Object) role2));
    assertEquals(role1.hashCode(), role2.hashCode());
    assertEquals(role1.toString(), role2.toString());
    assertEquals(role1.hashCode(), role1.hashCode());
  }

  @Test
  public void whenObjectsAreSame_ThenEqualsReturnsTrue() {
    var role = new ProjectRole(SAMPLE_PROJECT, "roles/test");

    assertTrue(role.equals(role));
    assertTrue(role.equals((Object) role));
    assertEquals(role.hashCode(), role.hashCode());
  }

  @Test
  public void whenProjectsDiffer_ThenEqualsReturnsFalse() {
    var role1 = new ProjectRole(new ProjectId("project-1"), "roles/test");
    var role2 = new ProjectRole(new ProjectId("project-2"), "roles/test");

    assertFalse(role1.equals(role2));
    assertFalse(role1.equals((Object) role2));
    assertNotEquals(role1.hashCode(), role2.hashCode());
  }

  @Test
  public void whenRolesDiffer_ThenEqualsReturnsFalse() {
    var role1 = new ProjectRole(new ProjectId("project-1"), "roles/test");
    var role2 = new ProjectRole(new ProjectId("project-1"), "roles/other");

    assertFalse(role1.equals(role2));
    assertFalse(role1.equals((Object) role2));
    assertNotEquals(role1.hashCode(), role2.hashCode());
  }

  @Test
  public void whenResourceConditionsDiffer_ThenEqualsReturnsFalse() {
    var role1 = new ProjectRole(new ProjectId("project-1"), "roles/test");
    var role2 = new ProjectRole(new ProjectId("project-2"), "roles/test", "true || false");

    assertFalse(role1.equals(role2));
    assertFalse(role1.equals((Object) role2));
    assertNotEquals(role1.hashCode(), role2.hashCode());
  }

  @Test
  public void equalsNullIsFalse() {
    var ref1 = new ProjectRole(SAMPLE_PROJECT, "roles/test");

    assertFalse(ref1.equals(null));
  }

  // -------------------------------------------------------------------------
  // fromId.
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {" ", "", " :x", "x: ", "xx" })
  public void whenIdNullOrEmptyOrMalformed_ThenFromIdThrowsException(String id) {
    assertThrows(
      IllegalArgumentException.class,
      () -> ProjectRole.fromId(id));
  }

  @Test
  public void whenIdValid_ThenFromIdSucceeds() {
    var roleWithoutCondition = new ProjectRole(SAMPLE_PROJECT, "roles/test");
    var roleWithCondition = new ProjectRole(SAMPLE_PROJECT, "roles/test", "resource.name=='foo'");

    assertEquals(roleWithoutCondition, ProjectRole.fromId(roleWithoutCondition.id()));
    assertEquals(roleWithCondition, ProjectRole.fromId(roleWithCondition.id()));
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

  @Test
  public void whenConditionIsActivationConstraint_ThenFromActivationRoleBindingReturnsRole() {
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
