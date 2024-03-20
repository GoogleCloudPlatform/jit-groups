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

package com.google.solutions.jitaccess.core.clients;

import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.GroupId;
import com.google.solutions.jitaccess.core.auth.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ITestCloudIdentityGroupsClient {
  private final String INVALID_CUSTOMER_ID = "Cinvalid";
  private static final GroupId TEST_GROUP_EMAIL = new GroupId(
    String.format(
      "jitaccess-test@%s",
      ITestEnvironment.CLOUD_IDENTITY_DOMAIN));

  @BeforeEach
  public void setUp() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    try {
      var groupId = new GroupKey(client.getGroup(TEST_GROUP_EMAIL).getName());
      client.deleteGroup(groupId);
    }
    catch (AccessDeniedException ignored) {
      //
      // Group doesn't exist, ok.
      //
    }
  }

  //---------------------------------------------------------------------
  // getGroup.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenGetGroupThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.getGroup(new GroupId("test@example.com")));
  }

  @Test
  public void whenCallerLacksPermission_ThenGetGroupThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> client.getGroup(new GroupId("test@example.com")));
  }

  @Test
  public void whenGroupNotFound_ThenGetThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> client.getGroup(new GroupId(String.format(
        "jitaccess-doesnotexist@%s",
        ITestEnvironment.CLOUD_IDENTITY_DOMAIN))));
  }

  @Test
  public void getGroup() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    client.createGroup(TEST_GROUP_EMAIL, "description");
    var group = client.getGroup(TEST_GROUP_EMAIL);

    assertEquals(TEST_GROUP_EMAIL.email, group.getGroupKey().getId());
  }

  //---------------------------------------------------------------------
  // createGroup.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenCreateGroupThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.createGroup(
        new GroupId("test@example.com"),
        "test group"));
  }

  @Test
  public void whenCustomerIdDoesNotMatchDomain_ThenCreateGroupThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      IllegalArgumentException.class,
      () -> client.createGroup(
        new GroupId("doesnotexist@google.com"),
        "test group"));
  }

  @Test
  public void createGroupIsIdempotent() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    //
    // Delete group if it exists.
    //
    var oldId = client.createGroup(TEST_GROUP_EMAIL, "test group");
    client.deleteGroup(oldId);

    //
    // Create.
    //
    var createdId = client.createGroup(TEST_GROUP_EMAIL, "test group");
    assertNotEquals(oldId, createdId);

    //
    // Create again.
    //
    var sameId = client.createGroup(TEST_GROUP_EMAIL, "test group");
    assertNotEquals(oldId, createdId);
  }

  //---------------------------------------------------------------------
  // deleteGroup.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenDeleteGroupThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.deleteGroup(new GroupKey("1")));
  }

  @Test
  public void whenGroupIdInvalid_ThenDeleteGroupThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      IllegalArgumentException.class,
      () -> client.deleteGroup(new GroupKey("doesnotexist")));
  }

  //---------------------------------------------------------------------
  // getMembership.
  //---------------------------------------------------------------------

  @Test
  public void whenGroupIdInvalid_ThenGetMembershipThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      IllegalArgumentException.class,
      () -> client.getMembership(
        new GroupKey("doesnotexist"),
        ITestEnvironment.NO_ACCESS_USER));
  }

  @Test
  public void getMembershipReturnsExpiryDetails() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var groupId = client.createGroup(TEST_GROUP_EMAIL, "test group");

    var membershipExpiry = Instant.now().plusSeconds(300);
    var id = client.addMembership(
      groupId,
      ITestEnvironment.TEMPORARY_ACCESS_USER,
      membershipExpiry);
    var membership = client.getMembership(
      groupId,
      ITestEnvironment.TEMPORARY_ACCESS_USER);

    assertEquals(id.id(), membership.getName());
    assertEquals(
      membershipExpiry.truncatedTo(ChronoUnit.SECONDS),
      Instant.parse(membership.getRoles().get(0).getExpiryDetail().getExpireTime()));
  }

  //---------------------------------------------------------------------
  // addMembership.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenAddMembershipThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.addMembership(
        new GroupKey("test"),
        new UserId("user@example.com"),
        Instant.now().plusSeconds(300)));
  }

  @Test
  public void whenGroupIdInvalid_ThenAddMembershipThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      IllegalArgumentException.class,
      () -> client.addMembership(
        new GroupKey("invalid"),
        new UserId("user@example.com"),
        Instant.now().plusSeconds(300)));
  }

  @Test
  public void addMembershipIsIdempotent() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);
    var groupId = client.createGroup(TEST_GROUP_EMAIL, "test group");
    var userEmail = ITestEnvironment.TEMPORARY_ACCESS_USER;

    //
    // Add member twice.
    //
    var expiry = Instant.now().plusSeconds(300);
    var id = client.addMembership(groupId, userEmail, expiry);
    var sameId = client.addMembership(groupId, userEmail, expiry);

    assertEquals(id, sameId);
  }

  @Test
  public void whenExpiryIsInThePast_ThenAddMembershipThrowsException() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var groupId = client.createGroup(TEST_GROUP_EMAIL, "test group");

    assertThrows(
      IllegalArgumentException.class,
      () -> client.addMembership(
        groupId,
        ITestEnvironment.TEMPORARY_ACCESS_USER,
        Instant.now().minusSeconds(300)));
  }

  @Test
  public void whenMembershipExists_ThenAddMembershipUpdatesExpiry() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var groupId = client.createGroup(TEST_GROUP_EMAIL, "test group");
    var userEmail = ITestEnvironment.TEMPORARY_ACCESS_USER;

    //
    // Add membership with an initial expiry.
    //
    var initialExpiry = Instant.now().plusSeconds(300);
    client.addMembership(groupId, userEmail, initialExpiry);

    assertEquals(
      initialExpiry.truncatedTo(ChronoUnit.SECONDS),
      Instant.parse(client
        .getMembership(groupId, userEmail)
        .getRoles()
        .get(0)
        .getExpiryDetail()
        .getExpireTime()));

    //
    // Change expiry.
    //
    var newExpiry = Instant.now().plusSeconds(400);
    client.addMembership(groupId, userEmail, newExpiry);

    assertEquals(
      newExpiry.truncatedTo(ChronoUnit.SECONDS),
      Instant.parse(client
        .getMembership(groupId, userEmail)
        .getRoles()
        .get(0)
        .getExpiryDetail()
        .getExpireTime()));
  }

  //---------------------------------------------------------------------
  // deleteMembership.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenDeleteMembershipThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.deleteMembership(
        new CloudIdentityGroupsClient.MembershipId("groups/1/memberships/1")));
  }

  @Test
  public void deleteMembershipIsIdempotent() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var groupId = client.createGroup(TEST_GROUP_EMAIL, "test group");
    var id = client.addMembership(
      groupId,
      ITestEnvironment.TEMPORARY_ACCESS_USER,
      Instant.now().plusSeconds(300));

    //
    // Delete twice.
    //
    client.deleteMembership(id);
    client.deleteMembership(id);

    //
    // Check deletion was effective.
    //
    assertThrows(
      ResourceNotFoundException.class,
      () -> client.getMembership(id));
  }

  //---------------------------------------------------------------------
  // listMemberships.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenListMembershipsThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.listMemberships(TEST_GROUP_EMAIL));
  }

  @Test
  public void listMembershipsReturnsExpiryDetails() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var groupId = client.createGroup(TEST_GROUP_EMAIL, "test group");
    var membershipExpiry = Instant.now().plusSeconds(300);
    client.addMembership(
      groupId,
      ITestEnvironment.TEMPORARY_ACCESS_USER,
      membershipExpiry);

    var memberships = client.listMemberships(TEST_GROUP_EMAIL);
    assertEquals(2, memberships.size());

    var membership = memberships
      .stream()
      .filter(m -> m.getPreferredMemberKey().getId().equals(ITestEnvironment.TEMPORARY_ACCESS_USER.toString()))
      .findFirst();

    assertTrue(membership.isPresent());

    assertEquals(1, membership.get().getRoles().size());
    assertEquals(
      membershipExpiry.truncatedTo(ChronoUnit.SECONDS),
      Instant.parse(membership.get().getRoles().get(0).getExpiryDetail().getExpireTime()));
  }

  //---------------------------------------------------------------------
  // listMembershipsByUser.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenListMembershipsByUserThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.listMembershipsByUser(
        ITestEnvironment.TEMPORARY_ACCESS_USER));
  }

  @Test
  public void whenUserNotFound_ThenListMembershipsByUserThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      ResourceNotFoundException.class,
      () -> client.listMembershipsByUser(
      new UserId("doesnotexist@" + ITestEnvironment.CLOUD_IDENTITY_DOMAIN)));
  }

  @Test
  public void listMembershipsByUser() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var memberships = client.listMembershipsByUser(
      ITestEnvironment.TEMPORARY_ACCESS_USER);
    assertEquals(0, memberships.size());
  }

  //---------------------------------------------------------------------
  // searchGroups.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenSearchGroupsThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.searchGroups("", false));
  }

  @Test
  public void whenQueryInvalid_ThenSearchGroupsThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      IllegalArgumentException.class,
      () -> client.searchGroups("invalid", false));
  }

  @Test
  public void searchGroups() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    client.createGroup(TEST_GROUP_EMAIL, "test group");
    var groups = client.searchGroups(
      client.createSearchQueryForPrefix("jitaccess-"),
      true);

    var foundGroup = groups.stream()
      .filter(g -> g.getGroupKey().getId().equals(TEST_GROUP_EMAIL.email))
      .findFirst();

    assertTrue(foundGroup.isPresent());
    assertNotNull(foundGroup.get().getDescription());
    assertNotEquals("", foundGroup.get().getDescription());
  }
}
