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

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPrincipal {
  // -------------------------------------------------------------------------
  // isPermanent.
  // -------------------------------------------------------------------------

  @Test
  public void isPermanent() {
    var permanent = new Principal(new GroupId("group@example.com"));
    var temporary = new Principal(new GroupId("group@example.com"), Instant.now());

    assertTrue(permanent.isPermanent());
    assertFalse(temporary.isPermanent());
  }

  // -------------------------------------------------------------------------
  // isValid.
  // -------------------------------------------------------------------------

  @Test
  public void isValid() {
    var permanent = new Principal(new GroupId("group@example.com"));
    var temporary = new Principal(new GroupId("group@example.com"), Instant.now().plusSeconds(30));
    var expired = new Principal(new GroupId("group@example.com"), Instant.now().minusSeconds(30));

    assertTrue(permanent.isValid());
    assertTrue(temporary.isValid());
    assertFalse(expired.isValid());
  }
}
