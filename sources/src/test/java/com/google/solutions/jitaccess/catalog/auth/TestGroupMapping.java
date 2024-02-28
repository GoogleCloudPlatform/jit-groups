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

package com.google.solutions.jitaccess.catalog.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestGroupMapping {
  //---------------------------------------------------------------------------
  // isJitGroup
  //---------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    "@",
    "a@b",
    "jit@example.com",
    "jit...@example.com",
    "jit.a.b@example.com",
    "jit.a.b.@example.com",
    "jit.a.b.c@d.example.com",
  })
  public void isJitGroup_whenInvalid_thenReturnsFalse(String email) {
    var mapping = new GroupMapping("example.com");
    assertFalse(mapping.isJitGroup(new GroupId(email)));
  }

  @Test
  public void isJitGroup_whenValid_thenReturnsTrue() {
    var mapping = new GroupMapping("example.com");
    assertTrue(mapping.isJitGroup(new GroupId("jit.a.b.c@example.com")));
    assertTrue(mapping.isJitGroup(new GroupId("JIT.A.B.C@EXAMPLE.COM")));
  }

  //---------------------------------------------------------------------------
  // jitGroupFromGroup
  //---------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    "jit@example.com",
    "jit.a.b.c@d.example.com",
  })
  public void jitGroupFromGroup_whenInValid_thenThrowsException(String email) {
    var mapping = new GroupMapping("example.com");

    assertThrows(
      IllegalArgumentException.class,
      () -> mapping.jitGroupFromGroup(new GroupId(email)));
  }

  @Test
  public void jitGroupFromGroup_whenValid_thenReturnsJitGroupId() {
    var mapping = new GroupMapping("example.com");

    assertEquals(
      new JitGroupId("a", "b", "c"),
      mapping.jitGroupFromGroup(new GroupId("jit.a.b.c@example.com")));
  }

  //---------------------------------------------------------------------------
  // groupFromJitGroup
  //---------------------------------------------------------------------------

  @Test
  public void groupFromJitGroup() {
    var mapping = new GroupMapping("example.com");

    assertEquals(
      new GroupId("jit.a.b.c@example.com"),
      mapping.groupFromJitGroup(new JitGroupId("a", "b", "c")));
  }

  //---------------------------------------------------------------------------
  // groupPrefix
  //---------------------------------------------------------------------------

  @Test
  public void groupPrefix() {
    var mapping = new GroupMapping("example.com");
    assertEquals("jit.env-1.", mapping.groupPrefix("env-1"));
  }
}
