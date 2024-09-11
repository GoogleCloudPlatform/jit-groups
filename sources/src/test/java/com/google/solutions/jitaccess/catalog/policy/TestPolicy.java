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

import com.google.solutions.jitaccess.auth.JitGroupId;
import com.google.solutions.jitaccess.auth.EndUserId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPolicy {
  private static final EndUserId SAMPLE_USER = new EndUserId("user@example.com");

  private static final JitGroupId SAMPLE_GROUPID = new JitGroupId("env-1", "system-1", "group-1");

  private static class SamplePolicy extends AbstractPolicy {
    public SamplePolicy(
      @Nullable AccessControlList acl
    ) {
      super("Test", "Test", acl, Map.of());
    }

    public SamplePolicy(
      @Nullable AccessControlList acl,
      @NotNull Map<ConstraintClass, Collection<Constraint>> constraints
    ) {
      super("Test", "Test", acl, constraints);
    }
  }

  //---------------------------------------------------------------------------
  // effectiveConstraints.
  //---------------------------------------------------------------------------

  @Test
  public void effectiveConstraints_whenParentEmpty() {
    var constraint1 = new CelConstraint("constraint-1", "", List.of(), "false");
    var leafPolicy = new SamplePolicy(
      null,
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(constraint1)));

    assertEquals(1, leafPolicy.effectiveConstraints(Policy.ConstraintClass.JOIN).size());
    assertEquals(0, leafPolicy.effectiveConstraints(Policy.ConstraintClass.APPROVE).size());

    assertTrue(leafPolicy.effectiveConstraints(Policy.ConstraintClass.JOIN).contains(constraint1));
  }

  @Test
  public void effectiveConstraints_whenParentHasConstraints() {
    var parentConstraint1 = new CelConstraint("constraint-1", "", List.of(), "false");
    var parentConstraint2 = new CelConstraint("constraint-2", "", List.of(), "false");

    var parentPolicy = new SamplePolicy(
      null,
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(parentConstraint1, parentConstraint2)));

    var overriddenConstraint1 = new CelConstraint(parentConstraint1.name(), "", List.of(), "true");

    var childPolicy = new SamplePolicy(
      null,
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(overriddenConstraint1)));
    childPolicy.setParent(parentPolicy);

    assertEquals(2, childPolicy.effectiveConstraints(Policy.ConstraintClass.JOIN).size());
    assertEquals(0, childPolicy.effectiveConstraints(Policy.ConstraintClass.APPROVE).size());

    assertTrue(childPolicy.effectiveConstraints(Policy.ConstraintClass.JOIN).contains(overriddenConstraint1));
    assertTrue(childPolicy.effectiveConstraints(Policy.ConstraintClass.JOIN).contains(parentConstraint2));
  }
}
