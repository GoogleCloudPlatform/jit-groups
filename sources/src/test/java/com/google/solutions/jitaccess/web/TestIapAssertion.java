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
import com.google.solutions.jitaccess.catalog.auth.EndUserId;
import com.google.solutions.jitaccess.catalog.auth.ServiceAccountId;
import com.google.solutions.jitaccess.catalog.auth.Directory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestIapAssertion {
  // -------------------------------------------------------------------------
  // user.
  // -------------------------------------------------------------------------

  @Test
  public void user_whenServiceAgent() {
    assertThrows(
      IllegalArgumentException.class,
      () -> new IapAssertion(new JsonWebToken.Payload()
        .setSubject("subject-1")
        .set("email", "123@appspot.gserviceaccount.com")));
  }

  @Test
  public void user_whenServiceAccount() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .setSubject("subject-1")
      .set("email", "IAP-USER@project-1.iam.gserviceaccount.com"));

    assertInstanceOf(ServiceAccountId.class, assertion.user());
    assertEquals("iap-user@project-1.iam.gserviceaccount.com", assertion.user().value());
  }
  
  @Test
  public void user_whenEndUser() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .setSubject("subject-1")
      .set("email", "USER@example.com"));

    assertInstanceOf(EndUserId.class, assertion.user());
    assertEquals("user@example.com", assertion.user().value());
  }

  // -------------------------------------------------------------------------
  // directory.
  // -------------------------------------------------------------------------

  @Test
  public void directory_whenServiceAccount() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .setSubject("subject-1")
      .set("email", "IAP-USER@project-1.iam.gserviceaccount.com"));

    assertEquals(Directory.PROJECT, assertion.directory());
  }

  @Test
  public void directory_whenHostedDomainNotSet() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .setSubject("subject-1")
      .set("email", "user@example.com"));

    assertEquals(Directory.CONSUMER, assertion.directory());
  }

  @Test
  public void directory_whenHostedDomainSet() {
    var assertion = new IapAssertion(new JsonWebToken.Payload()
      .setSubject("subject-1")
      .set("email", "user@example.com")
      .set("hd", "example.COM"));

    assertEquals(Directory.Type.CLOUD_IDENTITY, assertion.directory().type());
    assertEquals("example.com", assertion.directory().hostedDomain());
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
