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

import com.google.solutions.jitaccess.cel.TimeSpan;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
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
  // availableRequesterPrivileges.
  // -------------------------------------------------------------------------

  @Test
  public void whenActiveIsEmpty_ThenAvailablePrivilegesHaveRightStatus() {
    var available1 = new RequesterPrivilege<StringId>(
        new StringId("available-1"),
        "available-1",
        new SelfApproval(),
        RequesterPrivilege.Status.INACTIVE);
    var available2 = new RequesterPrivilege<StringId>(
        new StringId("available-2"),
        "available-2",
        new SelfApproval(),
        RequesterPrivilege.Status.INACTIVE);

    var set = RequesterPrivilegeSet.build(
        Set.of(available1, available2),
        Set.of(),
        Set.of(),
        Set.of());

    assertEquals(Set.of(available1, available2), set.availableRequesterPrivileges());
  }

  @Test
  public void whenOnePrivilegeActive_ThenAvailablePrivilegesHaveRightStatus() {
    var selfApproval = new SelfApproval();
    var available1 = new RequesterPrivilege<StringId>(
        new StringId("available-1"),
        "available-1",
        selfApproval,
        RequesterPrivilege.Status.INACTIVE);
    var available2 = new RequesterPrivilege<StringId>(
        new StringId("available-2"),
        "available-2",
        new SelfApproval(),
        RequesterPrivilege.Status.INACTIVE);

    var validity1 = new TimeSpan(Instant.now(), Duration.ofMinutes(1));
    var set = RequesterPrivilegeSet.build(
        Set.of(available1, available2),
        Set.of(
            new RequesterPrivilegeSet.ActivatedRequesterPrivilege<>(
                available1.id(),
                validity1)),
        Set.of(),
        Set.of());

    assertIterableEquals(List.of(
        available2,
        new RequesterPrivilege<StringId>(
            new StringId("available-1"),
            "available-1",
            selfApproval,
            RequesterPrivilege.Status.ACTIVE,
            validity1)),
        set.availableRequesterPrivileges());
  }

  @Test
  public void whenAllPrivilegesActive_ThenAvailablePrivilegesHaveRightStatus() {
    var selfApproval = new SelfApproval();
    var selfApproval2 = new SelfApproval();

    var available1 = new RequesterPrivilege<StringId>(
        new StringId("available-1"),
        "available-1",
        selfApproval,
        RequesterPrivilege.Status.INACTIVE);
    var available2 = new RequesterPrivilege<StringId>(
        new StringId("available-2"),
        "available-2",
        selfApproval2,
        RequesterPrivilege.Status.INACTIVE);

    var validity = new TimeSpan(Instant.now(), Duration.ofMinutes(1));
    var set = RequesterPrivilegeSet.build(
        Set.of(available1, available2),
        Set.of(
            new RequesterPrivilegeSet.ActivatedRequesterPrivilege<>(
                available1.id(),
                validity),
            new RequesterPrivilegeSet.ActivatedRequesterPrivilege<>(
                available2.id(),
                validity)),
        Set.of(),
        Set.of());

    assertIterableEquals(List.of(
        new RequesterPrivilege<StringId>(
            new StringId("available-1"),
            "available-1",
            selfApproval,
            RequesterPrivilege.Status.ACTIVE,
            validity),
        new RequesterPrivilege<StringId>(
            new StringId("available-2"),
            "available-2",
            selfApproval2,
            RequesterPrivilege.Status.ACTIVE,
            validity)),
        set.availableRequesterPrivileges());
  }

  @Test
  public void whenUnavailablePrivilegesIsActive_ThenAvailablePrivilegesHaveRightStatus() {

    var available1 = new RequesterPrivilege<StringId>(
        new StringId("available-1"),
        "available-1",
        new SelfApproval(),
        RequesterPrivilege.Status.INACTIVE);
    var available2 = new RequesterPrivilege<StringId>(
        new StringId("available-2"),
        "available-2",
        new SelfApproval(),
        RequesterPrivilege.Status.INACTIVE);

    var validity1 = new TimeSpan(Instant.now().minus(Duration.ofMinutes(2)), Duration.ofMinutes(1));
    var unavailableId = new StringId("unavailable-1");
    var set = RequesterPrivilegeSet.build(
        Set.of(available1, available2),
        Set.of(),
        Set.of(new RequesterPrivilegeSet.ActivatedRequesterPrivilege<>(unavailableId, validity1)),
        Set.of());

    assertEquals(Set.of(available1, available2), set.availableRequesterPrivileges());
    assertIterableEquals(List.of(
        available1.id(),
        available2.id()),
        set.availableRequesterPrivileges().stream().map(privilege -> privilege.id())
            .collect(Collectors.toList()));
    assertEquals(unavailableId, set.expiredRequesterPrivileges().first().id());
  }
}
