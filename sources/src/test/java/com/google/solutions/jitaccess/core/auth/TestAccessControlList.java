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

package com.google.solutions.jitaccess.core.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

public class TestAccessControlList {
  private static final UserId TEST_USER_1 = new UserId("test-1@example.com");
  private static final UserId TEST_USER_2 = new UserId("test-2@example.com");
  private static final GroupId TEST_GROUP_1 = new GroupId("group-1@example.com");

  private record TestSubject(
    PrincipalId id,
    Set<PrincipalId> principals) implements Subject {
  }

  @Test
  public void emptyAcl() {
    var acl = new AccessControlList(Set.of());
    var subject = new TestSubject(TEST_USER_1, Set.of());

    assertFalse(acl.isAllowed(subject));
  }

  @Test
  public void aclWithNoMatches() {
    var acl = new AccessControlList(Set.of(TEST_GROUP_1, TEST_USER_2));
    var subject = new TestSubject(
      TEST_USER_1,
      Set.of(TEST_USER_1));

    assertFalse(acl.isAllowed(subject));
  }

  @Test
  public void aclWithSingleMatch() {
    var acl = new AccessControlList(Set.of(TEST_GROUP_1));
    var subject = new TestSubject(
      TEST_USER_1,
      Set.of(TEST_USER_1, TEST_GROUP_1));

    assertTrue(acl.isAllowed(subject));
  }

  @Test
  public void aclWithMultipleMatches() {
    var acl = new AccessControlList(Set.of(TEST_USER_1, TEST_GROUP_1));
    var subject = new TestSubject(
      TEST_USER_1,
      Set.of(TEST_USER_1, TEST_GROUP_1));

    assertTrue(acl.isAllowed(subject));
  }
}
