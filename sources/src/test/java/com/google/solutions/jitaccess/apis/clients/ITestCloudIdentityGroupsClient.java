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

package com.google.solutions.jitaccess.apis.clients;

import com.google.solutions.jitaccess.catalog.auth.GroupId;
import com.google.solutions.jitaccess.catalog.auth.UserId;
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
  public void getGroup_whenUnauthenticated_thenThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.getGroup(new GroupId("test@example.com")));
  }

  @Test
  public void getGroup_whenCallerLacksPermission_thenThrowsException() {
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
  public void getGroup_whenGroupNotFound_thenThrowsException() {
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

    client.createGroup(
      TEST_GROUP_EMAIL,
      CloudIdentityGroupsClient.GroupType.DiscussionForum,
      "name",
      "description");
    var group = client.getGroup(TEST_GROUP_EMAIL);

    assertEquals(TEST_GROUP_EMAIL.email, group.getGroupKey().getId());
    assertEquals("name", group.getDisplayName());
    assertEquals("description", group.getDescription());
  }

  //---------------------------------------------------------------------
  // createGroup.
  //---------------------------------------------------------------------

  @Test
  public void createGroup_whenUnauthenticated_thenThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.createGroup(
        new GroupId("test@example.com"),
        CloudIdentityGroupsClient.GroupType.DiscussionForum,
        "name",
        "description"));
  }

  @Test
  public void createGroup_whenCustomerIdDoesNotMatchDomain_thenThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      IllegalArgumentException.class,
      () -> client.createGroup(
        new GroupId("doesnotexist@google.com"),
        CloudIdentityGroupsClient.GroupType.DiscussionForum,
        "name",
        "description"));
  }

  @Test
  public void createGroup_createGroupIsIdempotent() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    //
    // Delete group if it exists.
    //
    var oldId = client.createGroup(
      TEST_GROUP_EMAIL,
      CloudIdentityGroupsClient.GroupType.DiscussionForum,
      "name",
      "description");
    client.deleteGroup(oldId);

    //
    // Create.
    //
    var createdId = client.createGroup(
      TEST_GROUP_EMAIL,
      CloudIdentityGroupsClient.GroupType.DiscussionForum,
      "name",
      "description");
    assertNotEquals(oldId, createdId);

    //
    // Create again.
    //
    var sameId = client.createGroup(
      TEST_GROUP_EMAIL,
      CloudIdentityGroupsClient.GroupType.DiscussionForum,
      "name",
      "description");
    assertNotEquals(oldId, createdId);
  }

  //---------------------------------------------------------------------
  // patchGroup.
  //---------------------------------------------------------------------

  @Test
  public void patchGroup_whenUnauthenticated_thenThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.patchGroup(new GroupKey("1"), "description"));
  }

  @Test
  public void patchGroup_whenGroupIdInvalid_thenThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      IllegalArgumentException.class,
      () -> client.patchGroup(new GroupKey("doesnotexist"),  "description"));
  }

  @Test
  public void patchGroup() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var createdId = client.createGroup(
      TEST_GROUP_EMAIL,
      CloudIdentityGroupsClient.GroupType.DiscussionForum,
      "name",
      "description");

    client.patchGroup(createdId, "new description");

    var group = client.getGroup(createdId);
    assertEquals("name", group.getDisplayName());
    assertEquals("new description", group.getDescription());
  }

  //---------------------------------------------------------------------
  // deleteGroup.
  //---------------------------------------------------------------------

  @Test
  public void deleteGroup_whenUnauthenticated_thenThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.deleteGroup(new GroupKey("1")));
  }

  @Test
  public void deleteGroup_whenGroupIdInvalid_thenThrowsException() {
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
  public void getMembership_whenGroupIdInvalid_thenThrowsException() {
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
  public void getMembership() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var groupId = client.createGroup(
      TEST_GROUP_EMAIL,
      CloudIdentityGroupsClient.GroupType.DiscussionForum,
      "name",
      "description");

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
  public void addMembership_whenUnauthenticated_thenThrowsException() {
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
  public void addMembership_whenGroupIdInvalid_thenThrowsException() {
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
  public void addMembership_isIdempotent() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);
    var groupId = client.createGroup(
      TEST_GROUP_EMAIL,
      CloudIdentityGroupsClient.GroupType.DiscussionForum,
      "name",
      "description");
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
  public void addMembership_whenExpiryIsInThePast_thenThrowsException() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var groupId = client.createGroup(
      TEST_GROUP_EMAIL,
      CloudIdentityGroupsClient.GroupType.DiscussionForum,
      "name",
      "description");

    assertThrows(
      IllegalArgumentException.class,
      () -> client.addMembership(
        groupId,
        ITestEnvironment.TEMPORARY_ACCESS_USER,
        Instant.now().minusSeconds(300)));
  }

  @Test
  public void addMembership_whenMembershipExists_thenUpdatesExpiry() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var groupId = client.createGroup(
      TEST_GROUP_EMAIL,
      CloudIdentityGroupsClient.GroupType.DiscussionForum,
      "name",
      "description");
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
  public void deleteMembership_whenUnauthenticated_thenThrowsException() {
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
  public void deleteMembership_isIdempotent() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var groupId = client.createGroup(
      TEST_GROUP_EMAIL,
      CloudIdentityGroupsClient.GroupType.DiscussionForum,
      "name",
      "description");
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
  public void listMemberships_whenUnauthenticated_thenThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.listMemberships(TEST_GROUP_EMAIL));
  }

  @Test
  public void listMemberships() throws Exception {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        ITestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var groupId = client.createGroup(
      TEST_GROUP_EMAIL,
      CloudIdentityGroupsClient.GroupType.DiscussionForum,
      "name",
      "description");
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
  public void listMembershipsByUser_whenUnauthenticated_thenThrowsException() {
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
  public void listMembershipsByUser_whenUserNotFound_thenThrowsException() {
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
  public void searchGroups_whenUnauthenticated_thenThrowsException() {
    var client = new CloudIdentityGroupsClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.searchGroups("", false));
  }

  @Test
  public void searchGroups_whenQueryInvalid_thenThrowsException() {
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

    client.createGroup(
      TEST_GROUP_EMAIL,
      CloudIdentityGroupsClient.GroupType.DiscussionForum,
      "name",
      "description");
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
