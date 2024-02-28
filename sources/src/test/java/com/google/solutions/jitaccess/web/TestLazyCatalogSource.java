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

package com.google.solutions.jitaccess.web;

import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.apis.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.catalog.Catalog;
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.catalog.auth.GroupMapping;
import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.catalog.policy.Policy;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.FileNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestLazyCatalogSource {
  // -------------------------------------------------------------------------
  // environments.
  // -------------------------------------------------------------------------

  @Test
  public void environments() {
    var loader = new LazyCatalogSource(
      Set.of("one", "two"),
      env -> { throw new UnsupportedOperationException(); },
      policy -> { throw new UnsupportedOperationException(); },
      Mockito.mock(GroupMapping.class),
      Mockito.mock(CloudIdentityGroupsClient.class),
      Duration.ZERO,
      Mockito.mock(Logger.class));

    assertEquals(2, loader.environmentPolicies().size());

    var one = loader.environmentPolicies()
      .stream()
      .filter(e -> e.name().equals("one"))
      .findFirst();
    assertTrue(one.isPresent());
    assertEquals("one", one.get().description());

    var two = loader.environmentPolicies()
      .stream()
      .filter(e -> e.name().equals("two"))
      .findFirst();
    assertTrue(two.isPresent());
    assertEquals("two", two.get().description());
  }

  // -------------------------------------------------------------------------
  // lookup.
  // -------------------------------------------------------------------------

  @Test
  public void lookup_whenEnvironmentInvalid() {
    var logger = Mockito.mock(Logger.class);

    var loader = new LazyCatalogSource(
      Set.of(),
      env -> { throw new UnsupportedOperationException(); },
      policy -> { throw new UnsupportedOperationException(); },
      Mockito.mock(GroupMapping.class),
      Mockito.mock(CloudIdentityGroupsClient.class),
      Duration.ZERO,
      logger);

    assertFalse(loader
      .provisioner(Mockito.mock(Catalog.class), "not-found")
      .isPresent());

    verify(logger, times(0)).error(
      any(),
      anyString(),
      any(Exception.class));
  }

  @Test
  public void lookup_whenProducingPolicyThrowsUnsupportedOperationException() {
    var logger = Mockito.mock(Logger.class);

    var loader = new LazyCatalogSource(
      Set.of("env"),
      env -> { throw new UnsupportedOperationException(); },
      policy -> { throw new UnsupportedOperationException(); },
      Mockito.mock(GroupMapping.class),
      Mockito.mock(CloudIdentityGroupsClient.class),
      Duration.ZERO,
      logger);

    assertFalse(loader
      .provisioner(Mockito.mock(Catalog.class), "env")
      .isPresent());

    verify(logger, times(1)).error(
      eq(EventIds.LOAD_ENVIRONMENT),
      anyString(),
      any(UnsupportedOperationException.class));
  }

  @Test
  public void lookup_whenProducingPolicyThrowsFileNotFoundException() {
    var logger = Mockito.mock(Logger.class);

    var loader = new LazyCatalogSource(
      Set.of("env"),
      env -> { throw new UncheckedExecutionException(new FileNotFoundException()); },
      policy -> { throw new UnsupportedOperationException(); },
      Mockito.mock(GroupMapping.class),
      Mockito.mock(CloudIdentityGroupsClient.class),
      Duration.ZERO,
      logger);

    assertFalse(loader
      .provisioner(Mockito.mock(Catalog.class), "env")
      .isPresent());

    verify(logger, times(1)).error(
      eq(EventIds.LOAD_ENVIRONMENT),
      anyString(),
      any(FileNotFoundException.class));
  }

  @Test
  public void lookup_whenEnvironmentNameMismatchesWithPolicy() throws Exception {
    var logger = Mockito.mock(Logger.class);

    var loader = new LazyCatalogSource(
      Set.of("env"),
      env -> new EnvironmentPolicy("different", "", new Policy.Metadata("mock", Instant.now())),
      policy -> { throw new UnsupportedOperationException(); },
      Mockito.mock(GroupMapping.class),
      Mockito.mock(CloudIdentityGroupsClient.class),
      Duration.ZERO,
      logger);

    assertFalse(loader
      .provisioner(Mockito.mock(Catalog.class), "env")
      .isPresent());

    verify(logger, times(1)).error(
      eq(EventIds.LOAD_ENVIRONMENT),
      anyString(),
      any(IllegalStateException.class));
  }

  @Test
  public void lookup() {
    var logger = Mockito.mock(Logger.class);

    var loader = new LazyCatalogSource(
      Set.of("env"),
      env -> new EnvironmentPolicy("env", "", new Policy.Metadata("mock", Instant.now())),
      policy -> Mockito.mock(ResourceManagerClient.class),
      Mockito.mock(GroupMapping.class),
      Mockito.mock(CloudIdentityGroupsClient.class),
      Duration.ZERO,
      logger);

    var environment = loader.provisioner(Mockito.mock(Catalog.class), "env");
    assertTrue(environment.isPresent());
  }
}
