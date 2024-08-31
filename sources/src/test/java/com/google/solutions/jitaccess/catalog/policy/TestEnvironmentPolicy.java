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

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestEnvironmentPolicy {

  private static final Policy.Metadata METADATA = new Policy.Metadata("test", Instant.EPOCH);

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
      () -> new EnvironmentPolicy(name, "description", METADATA));
  }


  //---------------------------------------------------------------------------
  // accessControlList.
  //---------------------------------------------------------------------------

  @Test
  public void accessControlList() {
    var policy = new EnvironmentPolicy(
      "system-1",
      "description",
      AccessControlList.EMPTY,
      Map.of(),
      METADATA);

    assertTrue(policy.accessControlList().isPresent());
  }

  //---------------------------------------------------------------------------
  // metadata.
  //---------------------------------------------------------------------------

  @Test
  public void metadata() {
    var environment = new EnvironmentPolicy("env", "", METADATA);
    assertSame(METADATA, environment.metadata());
  }

  //---------------------------------------------------------------------------
  // add.
  //---------------------------------------------------------------------------

  @Test
  public void add_whenAlreadyAdded_throwsException() {
    var environment = new EnvironmentPolicy("env", "", METADATA);
    var system = new SystemPolicy("system-1", "");
    environment.add(system);
    assertThrows(IllegalArgumentException.class, () -> environment.add(system));
  }

  //---------------------------------------------------------------------------
  // toString.
  //---------------------------------------------------------------------------

  @Test
  public void toString_returnsName() {
    var environment = new EnvironmentPolicy("env", "", METADATA);
    assertEquals("env", environment.toString());
  }

  //---------------------------------------------------------------------------
  // system.
  //---------------------------------------------------------------------------

  @Test
  public void system() {
    var environment = new EnvironmentPolicy("env", "", METADATA);
    environment.add(new SystemPolicy("system-1", ""));

    assertTrue(environment.system("system-1").isPresent());
    assertFalse(environment.system("system-2").isPresent());
  }
}
