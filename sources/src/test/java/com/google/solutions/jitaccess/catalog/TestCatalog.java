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

package com.google.solutions.jitaccess.catalog;

import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestCatalog {
  private static final UserId SAMPLE_USER = new UserId("user@example.com");

  private static EnvironmentPolicy createEnvironmentPolicy(String name) {
    return new EnvironmentPolicy(
      name,
      name.toUpperCase(),
      new Policy.Metadata("test", Instant.EPOCH));
  }

  // -------------------------------------------------------------------------
  // environments.
  // -------------------------------------------------------------------------

  @Test
  public void environments() {
    var catalog = new Catalog(
      Mockito.mock(Subject.class),
      CatalogSources.create(List.of(
        createEnvironmentPolicy("env-1"),
        createEnvironmentPolicy("env-2"))));

    assertEquals(2, catalog.environments().size());
  }

  // -------------------------------------------------------------------------
  // environment.
  // -------------------------------------------------------------------------

  @Test
  public void environment_whenNotFound() {
    var catalog = new Catalog(
      Mockito.mock(Subject.class),
      CatalogSources.create(createEnvironmentPolicy("env-1")));

    assertFalse(catalog.environment("").isPresent());
    assertFalse(catalog.environment("ENV-1").isPresent());
  }

  @Test
  public void environment_whenAccessDenied() {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      AccessControlList.EMPTY,
      Map.of(),
      new Policy.Metadata("test", Instant.EPOCH));

    var catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(environment));

    assertFalse(catalog.environment(environment.name()).isPresent());
  }

  @Test
  public void environment() {
    var subject = Subjects.create(SAMPLE_USER);

    var environment = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new AccessControlList(List.of(
        new AccessControlList.AllowedEntry(subject.user(), PolicyPermission.VIEW.toMask()))),
      Map.of(),
      new Policy.Metadata("test", Instant.EPOCH));

    var catalog = new Catalog(
      subject,
      CatalogSources.create(environment));

    assertTrue(catalog.environment(environment.name()).isPresent());
  }

  // -------------------------------------------------------------------------
  // group.
  // -------------------------------------------------------------------------

  @Test
  public void group_whenAccessDenied_thenReturnsEmpty() throws AccessDeniedException {
    var environmentPolicy = createEnvironmentPolicy("env-1");
    var systemPolicy = new SystemPolicy("system-1", "System 1");
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .deny(SAMPLE_USER, PolicyPermission.VIEW.toMask())
        .build(),
      Map.of(),
      List.of());
    systemPolicy.add(groupPolicy);
    environmentPolicy.add(systemPolicy);

    var catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(environmentPolicy));

    assertFalse(catalog.group(groupPolicy.id()).isPresent());
  }

  @Test
  public void group_whenAccessAllowed_thenReturnsDetails() throws Exception {
    var environmentPolicy = createEnvironmentPolicy("env-1");
    var systemPolicy = new SystemPolicy("system-1", "System 1");
    var groupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.VIEW.toMask())
        .build(),
      Map.of(),
      List.of());
    systemPolicy.add(groupPolicy);
    environmentPolicy.add(systemPolicy);

    var catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(environmentPolicy));

    var details = catalog.group(groupPolicy.id());
    assertTrue(details.isPresent());
    assertEquals(groupPolicy, details.get().policy());
  }
}
