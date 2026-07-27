//
// Copyright 2026 Google LLC
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

package com.google.solutions.jitaccess.apis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public  class TestRegionId {


  // -------------------------------------------------------------------------
  // parse.
  // -------------------------------------------------------------------------

  @Test
  public void parse_whenIdPrefixed() {
    var id = RegionId.parse("projects/123456789/regions/region-1");

    assertTrue(id.isPresent());
    assertEquals("region-1", id.get().toString());
  }

  @Test
  public void parse_whenIdNotPrefixed() {
    var id = RegionId.parse(" region-1 ");

    assertTrue(id.isPresent());
    assertEquals("region-1", id.get().toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    " ",
    "region-1/"
  })
  public void parse_whenIdInvalid(String s) {
    assertFalse(RegionId.parse(null).isPresent());
    assertFalse(RegionId.parse(s).isPresent());
  }
  
  // -------------------------------------------------------------------------
  // isGlobal.
  // -------------------------------------------------------------------------

  @Test
  public void isGlobal() {
    assertFalse(new RegionId("region-1").isGlobal());
    assertTrue(new RegionId("global").isGlobal());
  }

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsId() {
    assertEquals("region-1", new RegionId("region-1").toString());
  }
}
