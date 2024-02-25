package com.google.solutions.jitaccess.core.clients;

import com.google.solutions.jitaccess.core.*;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestCloudIdentityGroupsClient {
  private final String INVALID_CUSTOMER_ID = "Cinvalid";
  private static final GroupEmail TEST_GROUP_EMAIL = new GroupEmail(
    String.format(
      "jitaccess-test@%s",
      IntegrationTestEnvironment.CLOUD_IDENTITY_DOAMIN));

  //---------------------------------------------------------------------
  // getGroup.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenGetGroupThrowsException() {
    var client = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.getGroup(new GroupEmail("test@example.com")));
  }

  @Test
  public void whenCallerLacksPermission_ThenGetGroupThrowsException() {
    var client = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        IntegrationTestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> client.getGroup(new GroupEmail("test@example.com")));
  }

  @Test
  public void whenGroupNotFound_ThenGetThrowsException() {
    var client = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        IntegrationTestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> client.getGroup(new GroupEmail(String.format(
        "jitaccess-doesnotexist@%s",
        IntegrationTestEnvironment.CLOUD_IDENTITY_DOAMIN))));
  }

  @Test
  public void getGroup() throws Exception {
    var client = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        IntegrationTestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
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
      IntegrationTestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.createGroup(
        new GroupEmail("test@example.com"),
        "test group"));
  }

  @Test
  public void whenCustomerIdDoesNotMatchDomain_ThenCreateGroupThrowsException() {
    var client = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        IntegrationTestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      IllegalArgumentException.class,
      () -> client.createGroup(
        new GroupEmail("doesnotexist@google.com"),
        "test group"));
  }

  @Test
  public void createGroupIsIdempotent() throws Exception {
    var client = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(IntegrationTestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
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
      IntegrationTestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.deleteGroup(new GroupId("1", "test@example.com")));
  }

  @Test
  public void whenGroupIdInvalid_ThenDeleteGroupThrowsException() {
    var client = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        IntegrationTestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      IllegalArgumentException.class,
      () -> client.deleteGroup(new GroupId("1", "doesnotexist@google.com")));
  }

  //---------------------------------------------------------------------
  // getMembership.
  //---------------------------------------------------------------------

  @Test
  public void whenGroupIdInvalid_ThenGetMembershipThrowsException() {
    var client = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        IntegrationTestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      IllegalArgumentException.class,
      () -> client.getMembership(
        new GroupId("1", "doesnotexist@google.com"),
        IntegrationTestEnvironment.NO_ACCESS_USER));
  }

  @Test
  public void getMembership() throws Exception {
    var client = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        IntegrationTestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var groupId = client.createGroup(TEST_GROUP_EMAIL, "test group");
    var id = client.addMembership(
      groupId,
      IntegrationTestEnvironment.TEMPORARY_ACCESS_USER);
    var membership = client.getMembership(
      groupId,
      IntegrationTestEnvironment.TEMPORARY_ACCESS_USER);

    assertEquals(id.id(), membership.getName());
  }

  //---------------------------------------------------------------------
  // addMembership.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenAddMembershipThrowsException() {
    var client = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> client.addMembership(
        new GroupId("1", "test@example.com"),
        new UserEmail("user@example.com")));
  }

  @Test
  public void whenGroupIdInvalid_ThenAddMembershipThrowsException() {
    var client = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        IntegrationTestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      IllegalArgumentException.class,
      () -> client.addMembership(
        new GroupId("1", "doesnotexist@google.com"),
        new UserEmail("user@example.com")));
  }

  @Test
  public void addMembershipIsIdempotent() throws Exception {
    var client = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(IntegrationTestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    //
    // Create group.
    //
    var groupId = client.createGroup(TEST_GROUP_EMAIL, "test group");

    //
    // Add member twice.
    //
    var id = client.addMembership(groupId, IntegrationTestEnvironment.TEMPORARY_ACCESS_USER);
    var sameId = client.addMembership(groupId, IntegrationTestEnvironment.TEMPORARY_ACCESS_USER);

    assertEquals(id, sameId);
  }

  //---------------------------------------------------------------------
  // deleteMembership.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenDeleteMembershipThrowsException() {
    var client = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.INVALID_CREDENTIAL,
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
      IntegrationTestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(
        IntegrationTestEnvironment.CLOUD_IDENTITY_ACCOUNT_ID),
      HttpTransport.Options.DEFAULT);

    var groupId = client.createGroup(TEST_GROUP_EMAIL, "test group");
    var id = client.addMembership(
      groupId,
      IntegrationTestEnvironment.TEMPORARY_ACCESS_USER);

    //
    // Delete twice.
    //
    client.deleteMembership(id);
    client.deleteMembership(id);

    //
    // Check deletion was effective.
    //
    assertThrows(
      NotFoundException.class,
      () -> client.getMembership(id));
  }
}
