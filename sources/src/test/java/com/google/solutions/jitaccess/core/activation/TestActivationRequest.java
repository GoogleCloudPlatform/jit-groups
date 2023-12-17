//
// Copyright 2023 Google LLC
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

package com.google.solutions.jitaccess.core.activation;

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.entitlements.EntitlementId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class TestActivationRequest {

  private class SampleActivationRequest extends ActivationRequest
  {
    public SampleActivationRequest(
      ActivationId id,
      UserId user,
      Collection<EntitlementId> entitlements,
      String justification,
      Instant startTime,
      Instant endTime) {
      super(id, user, entitlements, justification, startTime, endTime);
    }

    @Override
    protected void applyCore() throws AccessException {

    }
  }

  // -------------------------------------------------------------------------
  // apply.
  // -------------------------------------------------------------------------

  @Test
  public void whenJustificationPolicyViolated_ThenApplyThrowsException() throws Exception {
    var user = new UserId("user@example.com");

    var policy = Mockito.mock(JustificationPolicy.class);
    Mockito.doThrow(new InvalidJustificationException("mock"))
        .when(policy).checkJustification(eq(user), anyString());

    var request = new SampleActivationRequest(
      ActivationId.newId(ActivationType.JIT),
      user,
      List.of(new EntitlementId("cat", "1")),
      "invalid justification",
      Instant.ofEpochSecond(0),
      Instant.ofEpochSecond(5));

    assertThrows(
      InvalidJustificationException.class,
      () -> request.apply(policy));
  }

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toStringReturnsSummary() {
    var request = new SampleActivationRequest(
      new ActivationId("sample-1"),
      new UserId("user@example.com"),
      List.of(
        new EntitlementId("cat", "1"),
        new EntitlementId("cat", "2")),
      "invalid justification",
      Instant.ofEpochSecond(0),
      Instant.ofEpochSecond(5));

    assertEquals(
      "[sample-1] entitlements=cat:1,cat:2, duration=1970-01-01T00:00:00Z-1970-01-01T00:00:05Z, " +
        "justification=invalid justification",
      request.toString());
  }
}
