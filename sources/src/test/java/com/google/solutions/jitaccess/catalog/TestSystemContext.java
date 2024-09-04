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
import com.google.solutions.jitaccess.catalog.auth.EndUserId;
import com.google.solutions.jitaccess.catalog.policy.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestSystemContext {
  private static final EndUserId SAMPLE_USER = new EndUserId("user-1@example.com");

  // -------------------------------------------------------------------------
  // groups.
  // -------------------------------------------------------------------------

  @Test
  public void groups_whenAccessToSomeGroupsDenied_thenResultIsFiltered() throws Exception {
    var environmentPolicy =  new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));
    var systemPolicy = new SystemPolicy("system-1", "System 1");
    var allowedGroupPolicy = new JitGroupPolicy(
      "allowed-1",
      "Group 1",
      new AccessControlList(
        List.of(new AccessControlList.AllowedEntry(
          SAMPLE_USER,
          PolicyPermission.VIEW.toMask()))),
      Map.of(),
      List.of());
    var deniedGroupPolicy = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList.Builder()
        .deny(SAMPLE_USER, -1)
        .build(),
      Map.of(),
      List.of());
    systemPolicy.add(allowedGroupPolicy);
    systemPolicy.add(deniedGroupPolicy);
    environmentPolicy.add(systemPolicy);

    var catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(environmentPolicy));

    var system = catalog
      .environment(environmentPolicy.name()).get()
      .system(systemPolicy.name()).get();

    var groups = system.groups();
    assertEquals(1, groups.size());
    assertSame(allowedGroupPolicy, groups.stream().findFirst().get().policy());
  }


  // -------------------------------------------------------------------------
  // group.
  // -------------------------------------------------------------------------

  @Test
  public void group_whenAccessDenied_thenReturnsEmpty() throws AccessDeniedException {
    var environmentPolicy =  new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));
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

    var system = catalog
      .environment(environmentPolicy.name()).get()
      .system(systemPolicy.name()).get();

    assertFalse(system.group(groupPolicy.name()).isPresent());
  }

  @Test
  public void group_whenAccessAllowed_thenReturnsDetails() throws Exception {
    var environmentPolicy =  new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));
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

    var system = catalog
      .environment(environmentPolicy.name()).get()
      .system(systemPolicy.name()).get();

    var details = system.group(groupPolicy.name());
    assertTrue(details.isPresent());
    assertEquals(groupPolicy, details.get().policy());
  }
}
