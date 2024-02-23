package com.google.solutions.jitaccess.core.clients;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.cloudidentity.v1.CloudIdentity;
import com.google.api.services.cloudidentity.v1.model.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.GroupId;
import com.google.solutions.jitaccess.core.*;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

/**
 * Client for the Cloud Identity Groups API.
 */
@Singleton
public class CloudIdentityGroupsClient {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  private static final String LOCAL_USERS_AND_SERVICEACCOUNTS_ONLY =
    "member.customer_id == groupCustomerId() && (member.type == 1 || member.type == 2)";

  private final Options options;
  private final GoogleCredentials credentials;
  private final HttpTransport.Options httpOptions;

  public CloudIdentityGroupsClient(
    GoogleCredentials credentials,
    Options options,
    HttpTransport.Options httpOptions
  ) {
    Preconditions.checkNotNull(credentials, "credentials");
    Preconditions.checkNotNull(options, "options");
    Preconditions.checkNotNull(httpOptions, "httpOptions");

    this.credentials = credentials;
    this.options = options;
    this.httpOptions = httpOptions;
  }

  private CloudIdentity createClient() throws IOException {
    try {
      return new CloudIdentity.Builder(
        HttpTransport.newTransport(),
        new GsonFactory(),
        HttpTransport.newAuthenticatingRequestInitializer(this.credentials, this.httpOptions))
        .setApplicationName(ApplicationVersion.USER_AGENT)
        .build();
    }
    catch (GeneralSecurityException e) {
      throw new IOException("Creating a Cloud Identity client failed", e);
    }
  }

  public void createGroup(
    GroupId emailAddress,
    String description
  ) throws AccessException, IOException {
    try {
      var group = new Group()
        .setParent("customers/" + this.options.customerId)
        .setGroupKey(new EntityKey().setId(emailAddress.email()))
        .setDescription(description)
        .setLabels(Map.of("cloudidentity.googleapis.com/groups.discussion_forum", ""));

      var client = createClient().groups();
      var createOperation = client
        .create(group)
        .setInitialGroupConfig("WITH_INITIAL_OWNER")
        .execute();

      if (!createOperation.getDone()) {
        throw new IncompleteOperationException(
          String.format(
            "The creation of group '%s' was initiated, but hasn't completed",
            group.getGroupKey().getId()));
      }

      //
      // Get the key (groups/00xxxxxxxxx) of the newly created group
      // and security settings. This requires a second call.
      //
      var groupKey = (String)createOperation.getResponse().get("name");

      var updateOperation = client.updateSecuritySettings(
        String.format("%s/securitySettings", groupKey),
        new SecuritySettings()
          .setMemberRestriction(new MemberRestriction()
            .setQuery(LOCAL_USERS_AND_SERVICEACCOUNTS_ONLY)))
        .setUpdateMask("memberRestriction.query")
        .execute();

      if (!updateOperation.getDone()) {
        throw new IncompleteOperationException(
          String.format(
            "Setting security settings for group '%s' was initiated, but hasn't completed",
            group.getGroupKey().getId()));
      }
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException("Access to Cloud Identity API was denied", e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }

  public void addGroupMember(
    GroupId groupId,
    UserId userId
  ) throws AccessException, IOException {
    try {
      var operation = createClient()
        .groups()
        .memberships()
        .create(
          String.format("groups/%s", groupId.email()),
          new Membership()
            .setPreferredMemberKey(new EntityKey().setId(userId.email))
            //.setRoles(List.of(new MembershipRole()))
        )
        .execute();

      if (!operation.getDone()) {
        throw new IncompleteOperationException(
          String.format(
            "Adding membership to group '%s' was initiated, but hasn't completed",
            groupId.email()));
      }
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException("Access to Cloud Identity API was denied", e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }

  //---------------------------------------------------------------------------
  // Inner classes.
  //---------------------------------------------------------------------------

  public record Options(
    String customerId
  ) {
    public Options {
      Preconditions.checkNotNull(customerId, "customerId");
      Preconditions.checkArgument(
        customerId.startsWith("C"),
        "Customer ID must use format Cxxxxxxxx");
    }
  }

  class IncompleteOperationException extends AccessException {
    public IncompleteOperationException(String message) {
      super(message);
    }
  }
}
