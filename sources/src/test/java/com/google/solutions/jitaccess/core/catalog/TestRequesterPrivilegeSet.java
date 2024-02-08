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

package com.google.solutions.jitaccess.core.catalog;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class TestRequesterPrivilegeSet {
  private class StringId extends PrivilegeId {
    private final String id;

    public StringId(String id) {
      this.id = id;
    }

    @Override
    public String catalog() {
      return "test";
    }

    @Override
    public String id() {
      return this.id;
    }
  }

  // -------------------------------------------------------------------------
  // allRequesterPrivileges.
  // -------------------------------------------------------------------------

  @Test
  public void whenActiveIsEmpty_ThenAllRequesterPrivilegesReturnsConsolidatedSet() {
    var available1 = new RequesterPrivilege<StringId>(
        new StringId("available-1"),
        "available-1",
        new SelfApproval(),
        RequesterPrivilege.Status.AVAILABLE);
    var available2 = new RequesterPrivilege<StringId>(
        new StringId("available-2"),
        "available-2",
        new SelfApproval(),
        RequesterPrivilege.Status.AVAILABLE);

    var set = new RequesterPrivilegeSet<StringId>(
        Set.of(available1, available2),
        Set.of(),
        Set.of());

    assertEquals(Set.of(available1, available2), set.availableRequesterPrivileges());
    assertEquals(Set.of(), set.activeRequesterPrivilegeIds());
    assertIterableEquals(List.of(available1, available2), set.allRequesterPrivileges());
  }

  @Test
  public void whenOnePrivilegeActive_ThenAllRequesterPrivilegesReturnsConsolidatedSet() {
    var selfApproval = new SelfApproval();
    var available1 = new RequesterPrivilege<StringId>(
        new StringId("available-1"),
        "available-1",
        selfApproval,
        RequesterPrivilege.Status.AVAILABLE);
    var available2 = new RequesterPrivilege<StringId>(
        new StringId("available-2"),
        "available-2",
        new SelfApproval(),
        RequesterPrivilege.Status.AVAILABLE);

    var set = new RequesterPrivilegeSet<StringId>(
        Set.of(available1, available2),
        Set.of(available1.id()),
        Set.of());

    assertEquals(Set.of(available1, available2), set.availableRequesterPrivileges());
    assertEquals(Set.of(available1.id()), set.activeRequesterPrivilegeIds());
    assertIterableEquals(List.of(
        available2,
        new RequesterPrivilege<StringId>(
            new StringId("available-1"),
            "available-1",
            selfApproval,
            RequesterPrivilege.Status.ACTIVE)),
        set.allRequesterPrivileges());
  }

  @Test
  public void whenAllPrivilegesActive_ThenAllRequesterPrivilegesReturnsConsolidatedSet() {
    var selfApproval = new SelfApproval();
    var selfApproval2 = new SelfApproval();

    var available1 = new RequesterPrivilege<StringId>(
        new StringId("available-1"),
        "available-1",
        selfApproval,
        RequesterPrivilege.Status.AVAILABLE);
    var available2 = new RequesterPrivilege<StringId>(
        new StringId("available-2"),
        "available-2",
        selfApproval2,
        RequesterPrivilege.Status.AVAILABLE);

    var set = new RequesterPrivilegeSet<StringId>(
        Set.of(available1, available2),
        Set.of(available1.id(), available2.id()),
        Set.of());

    assertEquals(Set.of(available1, available2), set.availableRequesterPrivileges());
    assertEquals(Set.of(available1.id(), available2.id()), set.activeRequesterPrivilegeIds());
    assertIterableEquals(List.of(
        new RequesterPrivilege<StringId>(
            new StringId("available-1"),
            "available-1",
            selfApproval,
            RequesterPrivilege.Status.ACTIVE),
        new RequesterPrivilege<StringId>(
            new StringId("available-2"),
            "available-2",
            selfApproval2,
            RequesterPrivilege.Status.ACTIVE)),
        set.allRequesterPrivileges());
  }

  @Test
  public void whenUnavailablePrivilegesIsActive_ThenAllRequesterPrivilegesReturnsConsolidatedSet() {

    var available1 = new RequesterPrivilege<StringId>(
        new StringId("available-1"),
        "available-1",
        new SelfApproval(),
        RequesterPrivilege.Status.AVAILABLE);
    var available2 = new RequesterPrivilege<StringId>(
        new StringId("available-2"),
        "available-2",
        new SelfApproval(),
        RequesterPrivilege.Status.AVAILABLE);

    var unavailableId = new StringId("unavailable-1");
    var set = new RequesterPrivilegeSet<StringId>(
        Set.of(available1, available2),
        Set.of(unavailableId),
        Set.of());

    assertEquals(Set.of(available1, available2), set.availableRequesterPrivileges());
    assertIterableEquals(List.of(
        available1.id(),
        available2.id(),
        unavailableId),
        set.allRequesterPrivileges().stream().map(privilege -> privilege.id()).collect(Collectors.toList()));
  }
}
