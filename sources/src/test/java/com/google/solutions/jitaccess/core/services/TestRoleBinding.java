//
// Copyright 2021 Google LLC
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestRoleBinding {
  @Test
  public void whenValueIsEquivalent_ThenEqualsReturnsTrue() {
    var ref1 = new RoleBinding(
      "name", "//full-name",
      "roles/test",
      RoleBinding.RoleBindingStatus.ACTIVATED);
    var ref2 = new RoleBinding(
      "name",
      "//full-name",
      "roles/test",
      RoleBinding.RoleBindingStatus.ACTIVATED);

    assertTrue(ref1.equals(ref2));
    assertTrue(ref1.equals((Object) ref2));
    assertEquals(ref1.hashCode(), ref2.hashCode());
    assertEquals(ref1.toString(), ref2.toString());
  }

  @Test
  public void whenObjectsAreSame_ThenEqualsReturnsTrue() {
    var ref1 = new RoleBinding(
      "name",
      "//full-name",
      "roles/test",
      RoleBinding.RoleBindingStatus.ACTIVATED);
    var ref2 = ref1;

    assertTrue(ref1.equals(ref2));
    assertTrue(ref1.equals((Object) ref2));
    assertEquals(ref1.hashCode(), ref2.hashCode());
  }

  @Test
  public void whenRolesDiffer_ThenEqualsReturnsFalse() {
    var ref1 = new RoleBinding(
      "name",
      "//full-name",
      "roles/test",
      RoleBinding.RoleBindingStatus.ACTIVATED);
    var ref2 = new RoleBinding(
      "name",
      "//full-name",
      "roles/admin",
      RoleBinding.RoleBindingStatus.ACTIVATED);

    assertFalse(ref1.equals(ref2));
    assertFalse(ref1.equals((Object) ref2));
  }

  @Test
  public void whenStatusesDiffer_ThenEqualsReturnsFalse() {
    var ref1 = new RoleBinding(
      "name",
      "//full-name",
      "roles/test",
      RoleBinding.RoleBindingStatus.ACTIVATED);
    var ref2 = new RoleBinding(
      "name",
      "//full-name",
      "roles/test",
      RoleBinding.RoleBindingStatus.ELIGIBLE);

    assertFalse(ref1.equals(ref2));
    assertFalse(ref1.equals((Object) ref2));
  }

  @Test
  public void equalsNull() {
    var ref1 = new RoleBinding(
      "name",
      "//full-name",
      "roles/test",
      RoleBinding.RoleBindingStatus.ACTIVATED);

    assertFalse(ref1.equals(null));
  }

  @Test
  public void toStringReturnsDetails() {
    var ref1 = new RoleBinding(
      "name",
      "//full-name",
      "roles/test",
      RoleBinding.RoleBindingStatus.ACTIVATED);

    assertEquals("roles/test on //full-name (ACTIVATED)", ref1.toString());
  }
}
