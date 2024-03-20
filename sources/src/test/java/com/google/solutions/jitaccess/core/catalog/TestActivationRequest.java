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

package com.google.solutions.jitaccess.core.catalog;

import com.google.solutions.jitaccess.core.auth.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestActivationRequest {
  private class SampleEntitlementId extends EntitlementId
  {
    private final String id;

    public SampleEntitlementId(String id) {
      this.id = id;
    }

    @Override
    public String catalog() {
      return "sample";
    }

    @Override
    public String id() {
      return this.id;
    }
  }

  private class SampleActivationRequest extends ActivationRequest<SampleEntitlementId>
  {
    public SampleActivationRequest(
      ActivationId id,
      UserId user,
      Set<SampleEntitlementId> entitlements,
      String justification,
      Instant startTime,
      Duration duration) {
      super(id, user, entitlements, justification, startTime, duration);
    }

    @Override
    public ActivationType type() {
      return ActivationType.JIT;
    }
  }

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toStringReturnsSummary() {
    var request = new SampleActivationRequest(
      new ActivationId("sample-1"),
      new UserId("user@example.com"),
      Set.of(
        new SampleEntitlementId("1")),
      "some justification",
      Instant.ofEpochSecond(0),
      Duration.ofMinutes(5));

    assertEquals(
      "[sample-1] entitlements=sample:1, startTime=1970-01-01T00:00:00Z, " +
        "duration=PT5M, justification=some justification",
      request.toString());
  }
}
