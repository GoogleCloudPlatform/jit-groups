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

package com.google.solutions.jitaccess.catalog.legacy;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class TestTimeSpan {

  //---------------------------------------------------------------------------
  // equals.
  //---------------------------------------------------------------------------

  @Test
  public void whenSameOrEquivalent_ThenEqualsReturnsTrue() {
    var lhs = new TimeSpan(Instant.EPOCH, Duration.ofMinutes(1));
    var rhs = new TimeSpan(Instant.EPOCH, Duration.ofMinutes(1));

    assertTrue((lhs.equals(lhs)));
    assertTrue((lhs.equals(rhs)));
  }

  @Test
  public void whenNotEquivalent_ThenEqualsReturnsFalse() {
    var lhs = new TimeSpan(Instant.EPOCH, Duration.ofMinutes(1));

    assertFalse(lhs.equals(new TimeSpan(Instant.now(), Duration.ofMinutes(1))));
    assertFalse(lhs.equals(new TimeSpan(Instant.EPOCH, Duration.ofMinutes(2))));
    assertFalse(lhs.equals(null));
  }

  //---------------------------------------------------------------------------
  // compareTo.
  //---------------------------------------------------------------------------

  @Test
  public void whenEndsBefore_ThenCompareToReturnsNegative() {
    var lhs = new TimeSpan(Instant.EPOCH, Duration.ofMinutes(1));
    var rhs = new TimeSpan(Instant.EPOCH, Duration.ofMinutes(2));

    assertTrue(lhs.compareTo(rhs) < 0);
  }

  @Test
  public void whenEndsAtSameTime_ThenCompareToReturnsZero() {
    var lhs = new TimeSpan(Instant.EPOCH, Duration.ofMinutes(1));
    var rhs = new TimeSpan(Instant.EPOCH, Duration.ofMinutes(1));

    assertEquals(0, lhs.compareTo(rhs));
  }
}