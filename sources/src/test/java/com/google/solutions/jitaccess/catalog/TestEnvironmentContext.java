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
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.auth.EndUserId;
import com.google.solutions.jitaccess.auth.GroupId;
import com.google.solutions.jitaccess.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.legacy.LegacyPolicy;
import com.google.solutions.jitaccess.catalog.policy.*;
import com.google.solutions.jitaccess.catalog.provisioning.Environment;
import com.google.solutions.jitaccess.catalog.provisioning.Provisioner;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestEnvironmentContext {
  private static final EndUserId SAMPLE_USER = new EndUserId("user-1@example.com");

  @NotNull
  private static Provisioner createProvisioner(JitGroupId... groupIds) throws AccessException, IOException {
    var provisioner = Mockito.mock(Provisioner.class);
    when(provisioner.cloudIdentityGroupId(any()))
      .thenReturn(new GroupId("provisioned-id@example.com"));
    when(provisioner.provisionedGroups())
      .thenReturn(List.of(groupIds));
    return provisioner;
  }

  // -------------------------------------------------------------------------
  // systems.
  // -------------------------------------------------------------------------

  @Test
  public void systems_whenAccessPartiallyDenied_thenResultIsFiltered() {
    var allowedSystemPolicy = new SystemPolicy(
      "allowed-1",
      "",
      new AccessControlList(List.of(
        new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.VIEW.toMask())
      )),
      Map.of());
    var deniedSystemPolicy = new SystemPolicy(
      "denied-1",
      "",
      new AccessControlList(List.of(
        new AccessControlList.DeniedEntry(SAMPLE_USER, PolicyPermission.VIEW.toMask())
      )),
      Map.of());

    var environmentPolicy = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));

    environmentPolicy.add(allowedSystemPolicy);
    environmentPolicy.add(deniedSystemPolicy);

    var catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      Environments.create(List.of(environmentPolicy)));

    var environment = catalog.environment(environmentPolicy.name()).get();
    var systems = environment.systems();

    assertEquals(1, systems.size());
    assertSame(allowedSystemPolicy, systems.stream().findFirst().get().policy());
  }

  // -------------------------------------------------------------------------
  // system.
  // -------------------------------------------------------------------------

  @Test
  public void system_whenNotFound() {
    var environmentPolicy = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));

    var catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      Environments.create(environmentPolicy));

    var environment = catalog.environment(environmentPolicy.name()).get();
    assertFalse(environment.system("notfound").isPresent());
  }

  @Test
  public void system_whenAccessDenied() {
    var environmentPolicy = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));
    var systemPolicy = new SystemPolicy(
      "system-1",
      "System 1",
      new AccessControlList.Builder()
        .deny(SAMPLE_USER, -1)
        .build(),
      Map.of());
    environmentPolicy.add(systemPolicy);

    var catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      Environments.create(environmentPolicy));

    var environment = catalog.environment(environmentPolicy.name()).get();
    assertFalse(environment.system(systemPolicy.name()).isPresent());
  }

  @Test
  public void system() {
    var subject = Subjects.create(SAMPLE_USER);

    var environmentPolicy = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));
    var systemPolicy = new SystemPolicy(
      "system-1",
      "System 1",
      new AccessControlList(List.of(new AccessControlList.AllowedEntry(
        SAMPLE_USER,
        PolicyPermission.VIEW.toMask()))),
      Map.of());
    environmentPolicy.add(systemPolicy);

    var catalog = new Catalog(
      subject,
      Environments.create(environmentPolicy));

    var environment = catalog.environment(environmentPolicy.name()).get();
    assertTrue(environment.system(systemPolicy.name()).isPresent());
  }

  // -------------------------------------------------------------------------
  // export.
  // -------------------------------------------------------------------------

  @Test
  public void export_whenAccessDenied() {
    var environment = Mockito.mock(Environment.class);
    when(environment.policy())
      .thenReturn(new EnvironmentPolicy(
        "env",
        "env",
        new Policy.Metadata("test", Instant.EPOCH)));

    var context = new EnvironmentContext(
      environment,
      Subjects.create(SAMPLE_USER));

    assertFalse(context.canExport());
    assertFalse(context.export().isPresent());
  }

  @Test
  public void export() {
    var environment = Mockito.mock(Environment.class);
    when(environment.loadPolicy())
      .thenReturn(PolicyDocumentSource.fromString("original yaml"));
    when(environment.policy())
      .thenReturn(new EnvironmentPolicy(
        "env",
        "env",
        new AccessControlList.Builder()
          .allow(SAMPLE_USER, PolicyPermission.EXPORT.toMask())
          .build(),
        Map.of(),
        new Policy.Metadata("test", Instant.EPOCH)));

    var context = new EnvironmentContext(
      environment,
      Subjects.create(SAMPLE_USER));

    assertTrue(context.canExport());
    assertTrue(context.export().isPresent());
    assertEquals("original yaml", context.export().get().yaml());
  }

  // -------------------------------------------------------------------------
  // reconcile.
  // -------------------------------------------------------------------------

  @Test
  public void reconcile_whenAccessDenied() throws Exception {
    var environment = Mockito.mock(Environment.class);
    when(environment.policy())
      .thenReturn(
        new EnvironmentPolicy(
        "env",
        "env",
        new Policy.Metadata("test", Instant.EPOCH)));
    var context = new EnvironmentContext(
      environment,
      Subjects.create(SAMPLE_USER));

    assertFalse(context.canReconcile());
    assertFalse(context.reconcile().isPresent());
  }

  @Test
  public void reconcile_whenEnvironmentContainsOrphanedGroup() throws Exception {
    var environmentPolicy = new EnvironmentPolicy(
      "env",
      "env",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.RECONCILE.toMask())
        .build(),
      Map.of(),
      new Policy.Metadata("test", Instant.EPOCH));
    var systemPolicy = new SystemPolicy("system", "System");
    environmentPolicy.add(systemPolicy);

    var orphanedJitGroupId = new JitGroupId("env", "orphaned", "orphaned");

    var provisioner = createProvisioner(orphanedJitGroupId);
    var environment = Mockito.mock(Environment.class);
    when(environment.policy())
      .thenReturn(environmentPolicy);
    when(environment.provisioner())
      .thenReturn(provisioner);

    var context = new EnvironmentContext(
      environment,
      Subjects.create(SAMPLE_USER));

    assertTrue(context.canReconcile());

    var result = context.reconcile();
    assertTrue(result.isPresent());

    var resultMap = result.get().stream().collect(Collectors.toMap(r -> r.groupId(), r -> r));
    assertTrue(resultMap.get(orphanedJitGroupId).isOrphaned());
    assertFalse(resultMap.get(orphanedJitGroupId).isCompliant());
  }

  @Test
  public void reconcile_whenGroupBroken() throws Exception {
    var environmentPolicy = new EnvironmentPolicy(
      "env",
      "env",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.RECONCILE.toMask())
        .build(),
      Map.of(),
      new Policy.Metadata("test", Instant.EPOCH));
    var systemPolicy = new SystemPolicy("system", "System");
    environmentPolicy.add(systemPolicy);

    var brokenJitGroup = new JitGroupPolicy("broken", "broken");
    systemPolicy.add(brokenJitGroup);

    var provisioner = createProvisioner(brokenJitGroup.id());
    doThrow(new AccessDeniedException("mock")).when(provisioner).reconcile(eq(brokenJitGroup));

    var environment = Mockito.mock(Environment.class);
    when(environment.policy())
      .thenReturn(environmentPolicy);
    when(environment.provisioner())
      .thenReturn(provisioner);

    var context = new EnvironmentContext(
      environment,
      Subjects.create(SAMPLE_USER));

    assertTrue(context.canReconcile());

    var result = context.reconcile();
    assertTrue(result.isPresent());

    var resultMap = result.get().stream().collect(Collectors.toMap(r -> r.groupId(), r -> r));
    assertFalse(resultMap.get(brokenJitGroup.id()).isCompliant());
    assertInstanceOf(
      AccessDeniedException.class,
      resultMap.get(brokenJitGroup.id()).exception().get());
  }

  @Test
  public void reconcile_whenGroupCompliant() throws Exception {
    var environmentPolicy = new EnvironmentPolicy(
      "env",
      "env",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.RECONCILE.toMask())
        .build(),
      Map.of(),
      new Policy.Metadata("test", Instant.EPOCH));
    var systemPolicy = new SystemPolicy("system", "System");
    environmentPolicy.add(systemPolicy);

    var compliantJitGroup = new JitGroupPolicy("compliant", "compliant");
    systemPolicy.add(compliantJitGroup);

    var provisioner = createProvisioner(compliantJitGroup.id());
    doNothing().when(provisioner).reconcile(eq(compliantJitGroup));

    var environment = Mockito.mock(Environment.class);
    when(environment.policy())
      .thenReturn(environmentPolicy);
    when(environment.provisioner())
      .thenReturn(provisioner);

    var context = new EnvironmentContext(
      environment,
      Subjects.create(SAMPLE_USER));

    assertTrue(context.canReconcile());

    var result = context.reconcile();
    assertTrue(result.isPresent());

    var resultMap = result.get().stream().collect(Collectors.toMap(r -> r.groupId(), r -> r));
    assertTrue(resultMap.get(compliantJitGroup.id()).isCompliant());
  }

  @Test
  public void reconcile_whenLegacyRoleIncompatible() throws Exception {
    var incompatibleGroupId = new JitGroupId(LegacyPolicy.NAME, "123", "incompatible-role");

    var legacyPolicy = Mockito.mock(LegacyPolicy.class);
    when(legacyPolicy
      .isAccessAllowed(
        any(),
        eq(EnumSet.of(PolicyPermission.RECONCILE))))
      .thenReturn(true);
    when(legacyPolicy.incompatibilities())
      .thenReturn(List.of(new JitGroupCompliance(
        incompatibleGroupId,
        null,
        null,
        new IllegalArgumentException("mock"))));

    var provisioner = createProvisioner();
    var environment = Mockito.mock(Environment.class);
    when(environment.policy())
      .thenReturn(legacyPolicy);
    when(environment.provisioner())
      .thenReturn(provisioner);

    var context = new EnvironmentContext(
      environment,
      Subjects.create(SAMPLE_USER));

    assertTrue(context.canReconcile());
    var result = context.reconcile();
    var resultMap = result.get().stream().collect(Collectors.toMap(r -> r.groupId(), r -> r));
    assertFalse(resultMap.get(incompatibleGroupId).isCompliant());
    assertInstanceOf(
      IllegalArgumentException.class,
      resultMap.get(incompatibleGroupId).exception().get());
  }
}
