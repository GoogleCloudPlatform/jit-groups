package com.google.solutions.jitaccess.core.clients;

import com.google.solutions.jitaccess.GroupId;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.NotAuthenticatedException;
import com.google.solutions.jitaccess.core.UserId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestCloudIdentityGroupsClient {
  private final String INVALID_CUSTOMER_ID = "Cinvalid";
  private static final GroupId TEST_GROUP = new GroupId(
    String.format(
      "jitaccess-test@%s",
      IntegrationTestEnvironment.CLOUD_IDENTITY_DOAMIN));

  //---------------------------------------------------------------------
  // createGroup.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenCreateGroupThrowsException() {
    var adapter = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> adapter.createGroup(
        new GroupId("test@example.com"),
        "test group"));
  }

  @Test
  public void whenCallerLacksPermission_ThenSecurityGroupThrowsException() {
    var adapter = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> adapter.createGroup(
        new GroupId("test@example.com"),
        "test group"));
  }

  //---------------------------------------------------------------------
  // addGroupMember.
  //---------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenAddGroupMemberThrowsException() {
    var adapter = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.INVALID_CREDENTIAL,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> adapter.addGroupMember(
        new GroupId("test@example.com"),
        new UserId("user@example.com")));
  }

  @Test
  public void whenCallerLacksPermission_ThenAddGroupMemberThrowsException() {
    var adapter = new CloudIdentityGroupsClient(
      IntegrationTestEnvironment.APPLICATION_CREDENTIALS,
      new CloudIdentityGroupsClient.Options(INVALID_CUSTOMER_ID),
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> adapter.addGroupMember(
        new GroupId("test@example.com"),
        new UserId("user@example.com")));
  }

  //---------------------------------------------------------------------
  // TODO: REMOVE!.
  //---------------------------------------------------------------------

//  @Test
//  public void __test() throws Exception {
//    var customerId = "C00jbgy10";
//    var email = "jitaccess--test11@c.joonix.net";
//
//    var adapter = new CloudIdentityGroupsClient(
//      IntegrationTestEnvironment.APPLICATION_CREDENTIALS,
//      new CloudIdentityGroupsClient.Options(customerId),
//      HttpTransport.Options.DEFAULT);
//
//    //adapter.createGroup(new GroupId(email), "Sample");
//
//    adapter.addGroupMember(new GroupId(email),new UserId("a-alice@c.joonix.net"));
//  }
}
