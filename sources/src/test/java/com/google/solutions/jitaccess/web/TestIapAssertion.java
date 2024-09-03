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
import com.google.solutions.jitaccess.catalog.auth.ServiceAccountId;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.auth.UserType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestIapAssertion {
  // -------------------------------------------------------------------------
  // email.
  // -------------------------------------------------------------------------

  @Test
  public void email() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .setSubject("subject-1")
      .set("email", "user@example.com"));

    assertEquals("user@example.com", assertion.email().email);
  }

  // -------------------------------------------------------------------------
  // principal.
  // -------------------------------------------------------------------------

  @Test
  public void user_whenServiceAccount() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .setSubject("subject-1")
      .set("email", "IAP-USER@project-1.iam.gserviceaccount.com"));

    assertInstanceOf(ServiceAccountId.class, assertion.user());
    assertEquals("iap-user@project-1.iam.gserviceaccount.com", assertion.user().value());
    assertEquals(UserType.SERVICE_ACCOUNT, assertion.userType());
  }
  
  @Test
  public void user_whenUser() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .setSubject("subject-1")
      .set("email", "USER@example.com"));

    assertInstanceOf(UserId.class, assertion.user());
    assertEquals("user@example.com", assertion.user().value());
  }

  // -------------------------------------------------------------------------
  // hostedDomain.
  // -------------------------------------------------------------------------

  @Test
  public void hostedDomain_whenNotSet() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .setSubject("subject-1")
      .set("email", "user@example.com"));

    assertFalse(assertion.hostedDomain().isPresent());
    assertEquals(UserType.CONSUMER, assertion.userType());
  }

  @Test
  public void hostedDomain() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .setSubject("subject-1")
      .set("email", "user@example.com")
      .set("hd", "example.COM"));

    assertTrue(assertion.hostedDomain().isPresent());
    assertEquals("example.com", assertion.hostedDomain().get());
    assertEquals(UserType.MANAGED, assertion.userType());
  }

  // -------------------------------------------------------------------------
  // device.
  // -------------------------------------------------------------------------

  @Test
  public void device_whenGoogleClaimSetMissing() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .set("email", "user@example.com"));

    assertEquals(IapDevice.UNKNOWN, assertion.device());
  }

  @Test
  public void device_whenGoogleClaimSetEmpty() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .set("email", "user@example.com")
      .set("google", Map.of()));

    assertEquals(IapDevice.UNKNOWN, assertion.device());
  }

  @Test
  public void device_whenGoogleClaimSetPresent() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .set("email", "user@example.com")
      .set("google", Map.of("device_id", "device-1")));

    assertEquals("device-1", assertion.device().deviceId());
    assertEquals(List.of(), assertion.device().accessLevels());
  }

  @Test
  public void device_whenGoogleClaimContainsAccessLevelsSet() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .set("email", "user@example.com")
      .set("google", Map.of(
        "device_id", "device-1",
        "access_levels", List.of("level-1", "level-2"))));

    assertEquals("device-1", assertion.device().deviceId());
    assertEquals(List.of("level-1", "level-2"), assertion.device().accessLevels());
  }
}
