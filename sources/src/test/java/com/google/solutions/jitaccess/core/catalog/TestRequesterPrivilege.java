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

import com.google.solutions.jitaccess.cel.TimeSpan;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRequesterPrivilege {

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toStringReturnsName() {
    var privilege = new RequesterPrivilege<SamplePrivilegeId>(
        new SamplePrivilegeId("1"),
        "Sample privilege",
        new SelfApproval(),
        RequesterPrivilege.Status.INACTIVE);

    assertEquals("Sample privilege", privilege.toString());
  }

  // -------------------------------------------------------------------------
  // compareTo.
  // -------------------------------------------------------------------------

  @Test
  public void compareToOrdersByStatusThenName() {
    var availableA = new RequesterPrivilege<SamplePrivilegeId>(
        new SamplePrivilegeId("A"),
        "Privilege A",
        new SelfApproval(),
        RequesterPrivilege.Status.INACTIVE);
    var activeA = new RequesterPrivilege<SamplePrivilegeId>(
        new SamplePrivilegeId("A"),
        "Privilege A",
        new SelfApproval(),
        RequesterPrivilege.Status.ACTIVE,
        new TimeSpan(Instant.now(), Duration.ofMinutes(1)));
    var activeA2 = new RequesterPrivilege<SamplePrivilegeId>(
        new SamplePrivilegeId("A"),
        "Entitlement A",
        new SelfApproval(),
        RequesterPrivilege.Status.ACTIVE,
        new TimeSpan(Instant.now(), Duration.ofMinutes(2)));
    var pendingA = new RequesterPrivilege<SamplePrivilegeId>(
        new SamplePrivilegeId("A"),
        "Privilege A",
        new SelfApproval(),
        RequesterPrivilege.Status.ACTIVATION_PENDING);

    var availableB = new RequesterPrivilege<SamplePrivilegeId>(
        new SamplePrivilegeId("B"),
        "Privilege B",
        new SelfApproval(),
        RequesterPrivilege.Status.INACTIVE);

    var privileges = List.of(
        availableB,
        pendingA,
        availableA,
        activeA,
        activeA2);

    var sorted = new TreeSet<RequesterPrivilege<SamplePrivilegeId>>();
    sorted.addAll(privileges);

    Assertions.assertIterableEquals(
        List.of(
            availableA,
            availableB,
            activeA,
            activeA2,
            pendingA),
        sorted);
  }
}
