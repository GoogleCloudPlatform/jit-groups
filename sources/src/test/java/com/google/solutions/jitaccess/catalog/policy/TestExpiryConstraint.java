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

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestExpiryConstraint {
  private static final ExpiryConstraint FIXED =
    new ExpiryConstraint(Duration.ofMinutes(1), Duration.ofMinutes(1));

  private static final ExpiryConstraint USER_DEFINED =
    new ExpiryConstraint(Duration.ofMinutes(1), Duration.ofDays(3));

  //---------------------------------------------------------------------------
  // isFixedDuration.
  //---------------------------------------------------------------------------

  @Test
  public void isFixedDuration() {
    assertTrue(FIXED.isFixedDuration());
    assertFalse(USER_DEFINED.isFixedDuration());
  }

  //---------------------------------------------------------------------------
  // description.
  //---------------------------------------------------------------------------

  @Test
  public void description_whenFixedDuration() {
    assertEquals("Membership expires after 1 minute", FIXED.displayName());
  }

  @Test
  public void description_whenUserDefinedDuration() {
    assertEquals("You must choose an expiry between 1 minute and 3 days", USER_DEFINED.displayName());
  }

  //---------------------------------------------------------------------------
  // createCheck.
  //---------------------------------------------------------------------------

  @Test
  public void createCheck_whenFixedDuration_thenCheckSucceeds() throws Exception {
    var check = FIXED.createCheck();

    assertSame(FIXED, check.constraint());
    assertEquals(0, check.input().size());
    assertTrue(check.evaluate());
  }

  @Test
  public void createCheck_whenUserDefinedDurationAndInputMissing_thenCheckFails() throws Exception {
    var check = USER_DEFINED.createCheck();

    assertSame(USER_DEFINED, check.constraint());
    assertEquals(1, check.input().size());
    assertFalse(check.evaluate());
  }

  @Test
  public void createCheck_whenUserDefinedDurationAndInputInvalid_thenThrowsException() {
    var check = USER_DEFINED.createCheck();

    assertSame(USER_DEFINED, check.constraint());
    assertEquals(1, check.input().size());
    var expiry = check.input().stream().findFirst().get();

    assertThrows(
      IllegalArgumentException.class,
      () -> expiry.set("invalid"));
  }

  @Test
  public void createCheck_whenUserDefinedDurationAndInputOutOfRange_thenSetFails() {
    var check = USER_DEFINED.createCheck();

    assertSame(USER_DEFINED, check.constraint());
    assertEquals(1, check.input().size());
    var expiry = check.input().stream().findFirst().get();

    assertEquals(ExpiryConstraint.NAME, expiry.name());
    assertEquals(Duration.class, expiry.type());
    assertNull(expiry.get());

    assertThrows(
      IllegalArgumentException.class,
      () -> expiry.set(String.valueOf(USER_DEFINED.maxDuration().toSeconds() + 1)));
  }

  @Test
  public void createCheck_whenUserDefinedDurationAndInputInRange_thenCheckSucceeds() throws Exception {
    var check = USER_DEFINED.createCheck();

    assertSame(USER_DEFINED, check.constraint());
    assertEquals(1, check.input().size());
    var expiry = check.input().stream().findFirst().get();

    assertEquals(ExpiryConstraint.NAME, expiry.name());
    assertEquals(Duration.class, expiry.type());
    assertNull(expiry.get());

    expiry.set("PT1M");
    expiry.set(USER_DEFINED.minDuration().toString());

    assertTrue(check.evaluate());
  }

  //---------------------------------------------------------------------------
  // extractExpiry.
  //---------------------------------------------------------------------------

  @Test
  public void extractExpiry_whenFixedDuration() {
    assertEquals(FIXED.minDuration(), FIXED.extractExpiry(List.of()).get());
  }

  @Test
  public void extractExpiry_whenInputMissing() {
    assertFalse(USER_DEFINED.extractExpiry(List.of()).isPresent());
  }

  @Test
  public void extractExpiry_whenUserDefined() {
    var input = USER_DEFINED.createCheck().input();
    input.get(0).set("PT2M");
    assertEquals(
      Duration.ofMinutes(2),
      USER_DEFINED.extractExpiry(input).get());
  }
}
