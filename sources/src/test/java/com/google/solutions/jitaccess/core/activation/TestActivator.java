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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

public class TestActivator {
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
      Collection<SampleEntitlementId> entitlements,
      String justification,
      Instant startTime,
      Instant endTime) {
      super(id, user, entitlements, justification, startTime, endTime);
    }
  }

  private class SampleActivator extends EntitlementActivator<SampleEntitlementId> {
    protected SampleActivator(
      EntitlementCatalog<SampleEntitlementId> catalog,
      JustificationPolicy policy
    ) {
      super(catalog, policy);
    }

    @Override
    protected Activation<SampleEntitlementId> applyRequestCore(
      ActivationRequest<SampleEntitlementId> request
    ) throws AccessException {
      return Mockito.mock(Activation.class);
    }
  }

  // -------------------------------------------------------------------------
  // activate.
  // -------------------------------------------------------------------------

  @Test
  public void whenJustificationPolicyViolated_ThenActivateThrowsException() throws Exception {
    var user = new UserId("user@example.com");

    var request = new SampleActivationRequest(
      ActivationId.newId(ActivationType.JIT),
      user,
      List.of(new SampleEntitlementId("1")),
      "invalid justification",
      Instant.ofEpochSecond(0),
      Instant.ofEpochSecond(5));

    var policy = Mockito.mock(JustificationPolicy.class);
    Mockito.doThrow(new InvalidJustificationException("mock"))
      .when(policy).checkJustification(eq(user), anyString());

    var activator = new SampleActivator(
      Mockito.mock(EntitlementCatalog.class),
      policy);
    assertThrows(
      InvalidJustificationException.class,
      () -> activator.activate(request));
  }
}
