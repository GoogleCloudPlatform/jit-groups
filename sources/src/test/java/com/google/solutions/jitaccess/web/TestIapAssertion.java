//
// Copyright 2022 Google LLC
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

package com.google.solutions.jitaccess.web;

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.solutions.jitaccess.core.data.DeviceInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestIapAssertion {
  // -------------------------------------------------------------------------
  // getUserId.
  // -------------------------------------------------------------------------

  @Test
  public void getUserId() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .setSubject("subject-1")
      .set("email", "email-1"));

    assertEquals("subject-1", assertion.getUserId().id);
    assertEquals("email-1", assertion.getUserId().email);
  }

  // -------------------------------------------------------------------------
  // getDeviceInfo.
  // -------------------------------------------------------------------------

  @Test
  public void whenGoogleClaimMissing_ThenGetDeviceInfoReturnsUnknownDevice() {
    var assertion = new IapAssertion(new JsonWebToken.Payload());

    assertEquals(DeviceInfo.UNKNOWN, assertion.getDeviceInfo());
  }

  @Test
  public void whenGoogleClaimEmpty_ThenGetDeviceInfoReturnsDevice() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .set("google", Map.of()));

    assertEquals(DeviceInfo.UNKNOWN, assertion.getDeviceInfo());
  }

  @Test
  public void whenGoogleClaimSet_ThenGetDeviceInfoReturnsDevice() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .set("google", Map.of("device_id", "device-1")));

    assertEquals("device-1", assertion.getDeviceInfo().getDeviceId());
    assertEquals(List.of(), assertion.getDeviceInfo().getAccessLevels());
  }

  @Test
  public void whenGoogleClaimContainsAccessLevelsSet_ThenGetDeviceInfoReturnsDevice() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .set("google", Map.of(
        "device_id", "device-1",
        "access_levels", List.of("level-1", "level-2"))));

    assertEquals("device-1", assertion.getDeviceInfo().getDeviceId());
    assertEquals(List.of("level-1", "level-2"), assertion.getDeviceInfo().getAccessLevels());
  }
}
