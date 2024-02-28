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

public class TestSystemPolicy {

  //---------------------------------------------------------------------------
  // Constructor.
  //---------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    " ",
    "123456789_1234567",
    "with spaces",
    "?"})
  public void constructor_whenNameInvalid(String name) {
    assertThrows(
      IllegalArgumentException.class,
      () -> new SystemPolicy(name, "description"));
  }

  //---------------------------------------------------------------------------
  // accessControlList.
  //---------------------------------------------------------------------------

  @Test
  public void accessControlList() {
    var policy = new SystemPolicy(
      "system-1",
      "description",
      AccessControlList.EMPTY,
      Map.of());

    assertTrue(policy.accessControlList().isPresent());
  }

  //---------------------------------------------------------------------------
  // metadata.
  //---------------------------------------------------------------------------

  @Test
  public void metadata() {
    var group = new SystemPolicy(
      "system-1",
      "description");

    var metadata = new Policy.Metadata("test", Instant.EPOCH);
    var parentPolicy = Mockito.mock(Policy.class);
    when(parentPolicy.metadata())
      .thenReturn(metadata);

    group.setParent(parentPolicy);

    assertSame(metadata, group.metadata());
  }

  //---------------------------------------------------------------------------
  // add.
  //---------------------------------------------------------------------------

  @Test
  public void add_whenAlreadyAdded_throwsException() {
    var system = new SystemPolicy("system-1", "");
    var group = new JitGroupPolicy(
      "group-1",
      "description",
      AccessControlList.EMPTY,
      Map.of(),
      List.of());
    system.add(group);
    assertThrows(IllegalArgumentException.class, () -> system.add(group));
  }

  //---------------------------------------------------------------------------
  // toString.
  //---------------------------------------------------------------------------

  @Test
  public void toString_returnsName() {
    var system = new SystemPolicy("system-1", "");

    assertEquals("system-1", system.toString());
  }

  //---------------------------------------------------------------------------
  // group.
  //---------------------------------------------------------------------------

  @Test
  public void group() {
    var system = new SystemPolicy("system-1", "");
    var group = new JitGroupPolicy(
      "group-1",
      "description",
      AccessControlList.EMPTY,
      Map.of(),
      List.of());
    system.add(group);

    assertTrue(system.group("group-1").isPresent());
    assertFalse(system.group("group-2").isPresent());
  }
}
