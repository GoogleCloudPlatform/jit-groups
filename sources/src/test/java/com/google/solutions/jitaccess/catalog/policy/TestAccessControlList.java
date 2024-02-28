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

package com.google.solutions.jitaccess.catalog.policy;

import com.google.solutions.jitaccess.catalog.auth.GroupId;
import com.google.solutions.jitaccess.catalog.auth.Principal;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestAccessControlList {

  private static final UserId TEST_USER = new UserId("test-1@example.com");
  private static final UserId TEST_USER_OTHER = new UserId("test-2@example.com");
  private static final GroupId TEST_GROUP_1 = new GroupId("group-1@example.com");

  private record TestSubject(
    UserId user,
    Set<Principal> principals) implements Subject {
  }

  private static class Rights {
    public static final int READ = 1;
    public static final int WRITE = 2;
    public static final int EXECUTE = 4;
  }

  //---------------------------------------------------------------------------
  // isAllowed - Allow ACEs.
  //---------------------------------------------------------------------------

  @Test
  public void isAllowed_whenAclEmpty() {
    var acl = new AccessControlList(Set.of());
    var subject = new TestSubject(TEST_USER, Set.of());

    assertFalse(acl.isAllowed(subject, Rights.READ));
    assertFalse(acl.isAllowed(subject, Rights.WRITE));
  }

  @Test
  public void isAllowed_whenAclDoesNotIncludeAnyEntriesForSubject() {
    var acl = new AccessControlList.Builder()
      .allow(TEST_USER_OTHER, Rights.READ | Rights.WRITE | Rights.EXECUTE)
      .allow(TEST_GROUP_1, Rights.READ | Rights.WRITE | Rights.EXECUTE)
      .build();

    var subject = new TestSubject(
      TEST_USER,
      Set.of(new Principal(TEST_USER)));

    assertFalse(acl.isAllowed(subject, Rights.READ));
    assertFalse(acl.isAllowed(subject, Rights.WRITE));
    assertFalse(acl.isAllowed(subject, Rights.EXECUTE));
  }

  @Test
  public void isAllowed_whenAclHasRightsSpreadOverMultipleEntries() {
    var acl = new AccessControlList.Builder()
      .allow(TEST_USER, Rights.READ)
      .allow(TEST_USER, Rights.WRITE)
      .build();

    var subject = new TestSubject(
      TEST_USER,
      Set.of(new Principal(TEST_USER)));

    assertTrue(acl.isAllowed(subject, Rights.READ));
    assertTrue(acl.isAllowed(subject, Rights.WRITE));
    assertTrue(acl.isAllowed(subject, Rights.READ | Rights.WRITE));
    assertFalse(acl.isAllowed(subject, Rights.EXECUTE));
  }

  @Test
  public void isAllowed_whenAclHasRightsSpreadOverMultiplePrincipals() {
    var acl = new AccessControlList.Builder()
      .allow(TEST_USER, Rights.READ)
      .allow(TEST_GROUP_1, Rights.WRITE | Rights.EXECUTE)
      .build();

    var subject = new TestSubject(
      TEST_USER,
      Set.of(new Principal(TEST_USER), new Principal(TEST_GROUP_1)));

    assertTrue(acl.isAllowed(subject, Rights.READ));
    assertTrue(acl.isAllowed(subject, Rights.WRITE));
    assertTrue(acl.isAllowed(subject, Rights.READ | Rights.WRITE));
    assertTrue(acl.isAllowed(subject, Rights.EXECUTE));
  }

  @Test
  public void isAllowed_whenAclOnlyHasSubsetOfRights() {
    var acl = new AccessControlList.Builder()
      .allow(TEST_USER, Rights.READ)
      .build();

    var subject = new TestSubject(
      TEST_USER,
      Set.of(new Principal(TEST_USER), new Principal(TEST_GROUP_1)));

    assertFalse(acl.isAllowed(subject, Rights.READ | Rights.WRITE));
  }

  @Test
  public void isAllowed_whenPrincipalExpired() {
    var acl = new AccessControlList.Builder()
      .allow(TEST_USER, Rights.READ)
      .allow(TEST_GROUP_1, Rights.EXECUTE)
      .build();

    var subject = new TestSubject(
      TEST_USER,
      Set.of(
        new Principal(TEST_USER),
        new Principal(TEST_GROUP_1, Instant.now().minusSeconds(10))));

    assertTrue(acl.isAllowed(subject, Rights.READ));
    assertFalse(acl.isAllowed(subject, Rights.EXECUTE));
  }

  //---------------------------------------------------------------------------
  // isAllowed - Deny ACEs.
  //---------------------------------------------------------------------------

  @Test
  public void isAllowed_whenAclDeniesAllRights() {
    var acl = new AccessControlList.Builder()
      .deny(TEST_USER, Rights.READ | Rights.WRITE)
      .deny(TEST_USER, Rights.EXECUTE)
      .build();

    var subject = new TestSubject(
      TEST_USER,
      Set.of(new Principal(TEST_USER), new Principal(TEST_GROUP_1)));

    assertFalse(acl.isAllowed(subject, Rights.READ));
    assertFalse(acl.isAllowed(subject, Rights.WRITE));
    assertFalse(acl.isAllowed(subject, Rights.EXECUTE));
  }

  @Test
  public void isAllowed_whenAclDeniesSomeRights() {
    var acl = new AccessControlList.Builder()
      .allow(TEST_USER, Rights.READ | Rights.WRITE | Rights.EXECUTE)
      .deny(TEST_GROUP_1, Rights.EXECUTE)
      .build();

    var subject = new TestSubject(
      TEST_USER,
      Set.of(new Principal(TEST_USER), new Principal(TEST_GROUP_1)));

    assertTrue(acl.isAllowed(subject, Rights.READ | Rights.WRITE));
    assertFalse(acl.isAllowed(subject, Rights.EXECUTE));
    assertFalse(acl.isAllowed(subject, Rights.READ | Rights.WRITE | Rights.EXECUTE));
  }

  @Test
  public void isAllowed_whenAllowEntryShadowedByDenyEntry() {
    var acl = new AccessControlList.Builder()
      .deny(TEST_USER, Rights.READ)
      .allow(TEST_USER, Rights.READ)
      .build();

    var subject = new TestSubject(
      TEST_USER,
      Set.of(new Principal(TEST_USER)));

    assertFalse(acl.isAllowed(subject, Rights.READ));
  }

  //---------------------------------------------------------------------------
  // allowedPrincipals - Allow ACEs.
  //---------------------------------------------------------------------------

  @Test
  public void allowedPrincipals_whenAclEmpty() {
    var acl = new AccessControlList.Builder().build();

    assertEquals(0, acl.allowedPrincipals(Rights.READ).size());
  }

  @Test
  public void allowedPrincipals_whenNoPrincipalHasRequiredRights() {
    var acl = new AccessControlList.Builder()
      .allow(TEST_USER, Rights.READ | Rights.WRITE)
      .allow(TEST_USER_OTHER, Rights.READ)
      .build();

    assertEquals(0, acl.allowedPrincipals(Rights.EXECUTE).size());
  }

  @Test
  public void allowedPrincipals_whenPrincipalHasRightsSpreadOverMultipleEntries() {
    var acl = new AccessControlList.Builder()
      .allow(TEST_USER, Rights.READ)
      .allow(TEST_USER, Rights.WRITE)
      .build();

    var allowed = acl.allowedPrincipals(Rights.READ | Rights.WRITE);
    assertEquals(1, allowed.size());
    assertTrue(allowed.contains(TEST_USER));
  }

  @Test
  public void allowedPrincipals_whenPrincipalsHaveSubsetOfRights() {
    var acl = new AccessControlList.Builder()
      .allow(TEST_USER, Rights.READ)
      .allow(TEST_GROUP_1, Rights.READ | Rights.WRITE)
      .build();

    assertEquals(0, acl.allowedPrincipals(Rights.READ | Rights.WRITE | Rights.EXECUTE).size());
  }

  //---------------------------------------------------------------------------
  // allowedPrincipals - Deny ACEs.
  //---------------------------------------------------------------------------

  @Test
  public void allowedPrincipals_whenAclDeniesSomeRights() {
    var acl = new AccessControlList.Builder()
      .allow(TEST_USER, Rights.READ | Rights.WRITE | Rights.EXECUTE)
      .deny(TEST_USER, Rights.EXECUTE)
      .build();

    assertEquals(0, acl.allowedPrincipals(Rights.READ | Rights.WRITE | Rights.EXECUTE).size());
    assertEquals(1, acl.allowedPrincipals(Rights.READ | Rights.WRITE).size());
  }

  @Test
  public void allowedPrincipals_whenAllowEntryShadowedByDenyEntry() {
    var acl = new AccessControlList.Builder()
      .deny(TEST_USER, Rights.READ)
      .allow(TEST_USER, Rights.READ)
      .build();

    assertEquals(0, acl.allowedPrincipals(Rights.READ).size());
  }
}
