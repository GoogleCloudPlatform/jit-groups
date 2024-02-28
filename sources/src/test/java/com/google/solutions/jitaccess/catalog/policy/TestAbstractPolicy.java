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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class TestAbstractPolicy {

  //---------------------------------------------------------------------------
  // name.
  //---------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    " "
  })
  public void name_whenNameIsNullOrBlank(String name) {
    assertThrows(
      IllegalArgumentException.class,
      () -> new AbstractPolicy(
        null,
        "description",
        AccessControlList.EMPTY,
        Map.of()) {
      });
    assertThrows(
      IllegalArgumentException.class,
      () -> new AbstractPolicy(
        name,
        "description",
        AccessControlList.EMPTY,
        Map.of()) {
      });
  }

  @Test
  public void name_whenMixedCase() {
    var policy = new AbstractPolicy(
      "Policy-With-Mixed-Case",
      "description",
      AccessControlList.EMPTY,
      Map.of()) {
    };

    assertEquals("policy-with-mixed-case", policy.name());
  }

  //---------------------------------------------------------------------------
  // metadata.
  //---------------------------------------------------------------------------

  @Test
  public void metadata() {
    var policy = new AbstractPolicy(
      "policy-1",
      "description",
      AccessControlList.EMPTY,
      Map.of()) {
    };

    var metadata = new Policy.Metadata("test", Instant.EPOCH);
    var parentPolicy = Mockito.mock(Policy.class);
    when(parentPolicy.metadata())
      .thenReturn(metadata);

    policy.setParent(parentPolicy);

    assertSame(metadata, policy.metadata());
  }

  //---------------------------------------------------------------------------
  // constraints.
  //---------------------------------------------------------------------------

  @Test
  public void constraints_whenPolicyHasConstraints_returnsList() {
    var policy = new AbstractPolicy(
      "policy-1",
      "description",
      AccessControlList.EMPTY,
      Map.of(Policy.ConstraintClass.JOIN, List.of(Mockito.mock(Constraint.class)))) {

    };

    assertFalse(policy.constraints(Policy.ConstraintClass.JOIN).isEmpty());
  }

  @Test
  public void constraints_whenPolicyHasNoConstraints_returnsEmpty() {
    var policy = new AbstractPolicy(
      "policy-1",
      "description",
      AccessControlList.EMPTY,
      Map.of()) {
    };

    assertTrue(policy.constraints(Policy.ConstraintClass.JOIN).isEmpty());
  }

  //---------------------------------------------------------------------------
  // setParent.
  //---------------------------------------------------------------------------

  @Test
  public void setParent_whenParentIsSelf() {
    var policy = new AbstractPolicy(
      "child-1",
      "description",
      AccessControlList.EMPTY,
      Map.of()) {
    };

    assertThrows(IllegalArgumentException.class, () -> policy.setParent(policy));
  }

  @Test
  public void setParent_whenAlreadySet() {
    var policy = new AbstractPolicy(
      "child-1",
      "description",
      AccessControlList.EMPTY,
      Map.of()) {
    };

    var parent = new AbstractPolicy(
      "parent-1",
      "description",
      AccessControlList.EMPTY,
      Map.of()) {
    };

    policy.setParent(parent);
    assertThrows(IllegalArgumentException.class, () -> policy.setParent(parent));
  }

  //---------------------------------------------------------------------------
  // toString.
  //---------------------------------------------------------------------------

  @Test
  public void toString_returnsName() {
    var policy = new AbstractPolicy(
      "policy-1",
      "description",
      AccessControlList.EMPTY,
      Map.of()) {
    };

    assertEquals("policy-1", policy.toString());
  }
}
