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

package com.google.solutions.jitaccess.auth;

import com.google.solutions.jitaccess.catalog.Subjects;
import com.google.solutions.jitaccess.catalog.policy.AccessControlList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestAbstractSecurableComponent {
  private static final EndUserId SAMPLE_USER = new EndUserId("user@example.com");
  
  private static final int SAMPLE_PERMISSION = 1;

  private static class SampleComponent extends AbstractSecurableComponent {
    private @Nullable AbstractSecurableComponent parent;
    private @Nullable AccessControlList acl;

    public SampleComponent(
      @Nullable AbstractSecurableComponent parent,
      @Nullable AccessControlList acl
    ) {
      this.parent = parent;
      this.acl = acl;
    }

    @Override
    protected @NotNull Optional<? extends AbstractSecurableComponent> container() {
      return Optional.ofNullable(this.parent);
    }

    @Override
    protected @NotNull Optional<AccessControlList> accessControlList() {
      return Optional.ofNullable(this.acl);
    }
  }

  //---------------------------------------------------------------------------
  // effectiveAccessControlList.
  //---------------------------------------------------------------------------

  @Test
  public void effectiveAccessControlList_whenAclAndContainerIsEmpty() {
    var component = new SampleComponent(null, null);

    var acl = component.effectiveAccessControlList();
    assertTrue(acl.entries().isEmpty());
  }


  @Test
  public void effectiveAccessControlList_whenContainerIsEmpty() {
    var component = new SampleComponent(
      null,
      new AccessControlList.Builder().allow(SAMPLE_USER, -1).build());

    var acl = component.effectiveAccessControlList();
    assertEquals(1, acl.entries().size());
  }

  @Test
  public void effectiveAccessControlList_whenContainerHasAcl() {
    var parent = new SampleComponent(
      null,
      new AccessControlList.Builder().deny(SAMPLE_USER, -1).build());

    var component = new SampleComponent(
      parent,
      new AccessControlList.Builder().allow(SAMPLE_USER, -1).build());

    var acl = component.effectiveAccessControlList();
    assertEquals(2, acl.entries().size());

    var aces = List.copyOf(acl.entries());
    assertInstanceOf(AccessControlList.DeniedEntry.class, aces.get(0));
    assertInstanceOf(AccessControlList.AllowedEntry.class, aces.get(1));
  }

  //---------------------------------------------------------------------------
  // isAccessAllowed.
  //---------------------------------------------------------------------------

  @Test
  public void isAccessAllowed_whenPolicyHasNoAcl() {
    var component = new SampleComponent(null, null);

    assertFalse(component.isAccessAllowed(
      Subjects.create(SAMPLE_USER),
      SAMPLE_PERMISSION));
  }

  @Test
  public void isAccessAllowed_whenPolicyHasEmptyAcl() {
    var component = new SampleComponent(null, AccessControlList.EMPTY);

    assertFalse(component.isAccessAllowed(
      Subjects.create(SAMPLE_USER),
      SAMPLE_PERMISSION));
  }

  @Test
  public void isAccessAllowed_whenContainerHasNoAcl() {
    var parent = new SampleComponent(null, null);

    var component = new SampleComponent(
      parent,
      new AccessControlList.Builder().allow(SAMPLE_USER, -1).build());

    assertTrue(component.isAccessAllowed(
      Subjects.create(SAMPLE_USER),
      SAMPLE_PERMISSION));
  }

  @Test
  public void isAccessAllowed_whenContainerDeniesAccess() {
    var parent = new SampleComponent(
      null,
      new AccessControlList.Builder().deny(SAMPLE_USER, -1).build());

    var component = new SampleComponent(
      parent,
      new AccessControlList.Builder().allow(SAMPLE_USER, -1).build());

    assertFalse(component.isAccessAllowed(
      Subjects.createWithPrincipalIds(SAMPLE_USER, Set.of()),
      SAMPLE_PERMISSION));
  }

  @Test
  public void isAccessAllowed_whenChildDeniesAccess() {
    var parent = new SampleComponent(
      null,
      new AccessControlList.Builder().allow(SAMPLE_USER, -1).build());

    var component = new SampleComponent(
      parent,
      new AccessControlList.Builder().deny(SAMPLE_USER, -1).build());

    assertFalse(component.isAccessAllowed(
      Subjects.createWithPrincipalIds(SAMPLE_USER, Set.of()),
      SAMPLE_PERMISSION));
  }

  @Test
  public void isAccessAllowed_whenContainerAndChildGrantAccess() {
    var parent = new SampleComponent(
      null,
      new AccessControlList.Builder().allow(SAMPLE_USER, -1).build());

    var component = new SampleComponent(
      parent,
      new AccessControlList.Builder().allow(SAMPLE_USER, SAMPLE_PERMISSION).build());

    assertTrue(component.isAccessAllowed(
      Subjects.createWithPrincipalIds(SAMPLE_USER, Set.of()),
      SAMPLE_PERMISSION));
  }
}
