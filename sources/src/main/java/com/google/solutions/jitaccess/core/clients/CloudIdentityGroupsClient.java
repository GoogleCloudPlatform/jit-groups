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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.cloudidentity.v1.CloudIdentity;
import com.google.api.services.cloudidentity.v1.model.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.GroupEmail;
import com.google.solutions.jitaccess.core.*;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
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
  private static final int SEARCH_PAGE_SIZE = 1000;

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

  private static boolean isAlreadyExistsError(
    GoogleJsonResponseException e
  ) {
    return
      e.getStatusCode() == 409 &&
        "ALREADY_EXISTS".equals(e.getDetails().get("status"));
  }

  private static void translateAndThrowApiException(
    GoogleJsonResponseException e
  ) throws AccessException, IOException {
    switch (e.getStatusCode()) {
      case 400:
        throw new IllegalArgumentException("Invalid argument", e);
      case 401:
        throw new NotAuthenticatedException("Not authenticated", e);
      case 403:
        throw new AccessDeniedException("Not found or access denied", e);
      case 404:
        throw new NotFoundException("Not found", e);
      default:
        throw (GoogleJsonResponseException)e.fillInStackTrace();
    }
  }

  //---------------------------------------------------------------------
  // Manage groups.
  //---------------------------------------------------------------------

  /**
   * Look up a group ID by email.
   */
  private GroupId lookupGroup(
    CloudIdentity client,
    GroupEmail email
  ) throws AccessException, IOException {
    try {
      var id = client
        .groups()
        .lookup()
        .setGroupKeyId(email.email)
        .execute()
        .getName();

      return new GroupId(id, email);
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null;
    }
  }

  /**
   * Get details for an existing group.
   */
  private Group getGroup(
    CloudIdentity client,
    GroupId groupId
  ) throws AccessException, IOException {
    try {
      return client
        .groups()
        .get(groupId.toString())
        .execute();
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null;
    }
  }

  /**
   * Get details for an existing group.
   */
  public Group getGroup(
    GroupId groupId
  ) throws AccessException, IOException {
    return getGroup(createClient(), groupId);
  }

  /**
   * Get details for an existing group.
   */
  public Group getGroup(
    GroupEmail groupEmail
  ) throws AccessException, IOException {
    var client = createClient();
    return getGroup(client, lookupGroup(client, groupEmail));
  }

  /**
   * Create group in an idempotent way.
   */
  public GroupId createGroup(
    GroupEmail emailAddress,
    String description
  ) throws AccessException, IOException {
    try {
      var group = new Group()
        .setParent("customers/" + this.options.customerId)
        .setGroupKey(new EntityKey().setId(emailAddress.email))
        .setDescription(description)
        .setLabels(Map.of("cloudidentity.googleapis.com/groups.discussion_forum", ""));

      var client = createClient();

      //
      // Try to create the group. This might fail if it already exists.
      //

      GroupId groupId;
      try {
        var createOperation = client
          .groups()
          .create(group)
          .setInitialGroupConfig("WITH_INITIAL_OWNER")
          .execute();

        if (!createOperation.getDone()) {
          throw new IncompleteOperationException(
            String.format(
              "The creation of group '%s' was initiated, but hasn't completed",
              group.getGroupKey().getId()));
        }

        groupId = new GroupId(
          (String)createOperation.getResponse().get("name"),
          emailAddress);
      }
      catch (GoogleJsonResponseException e) {
        if (isAlreadyExistsError(e)) {
          //
          // Group already exists. That's ok, but we need to find out
          // its ID.
          //
          groupId = lookupGroup(client, emailAddress);
        }
        else {
          throw (GoogleJsonResponseException)e.fillInStackTrace();
        }
      }

      //
      // Make sure the group has the right security settings.
      //

      var updateOperation = client
        .groups()
        .updateSecuritySettings(
          String.format("%s/securitySettings", groupId),
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

      return groupId;
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null;
    }
  }

  /**
   * Delete a group.
   */
  public void deleteGroup(
    GroupId groupId
  ) throws AccessException, IOException {
    try {
      var createOperation = createClient()
        .groups()
        .delete(groupId.toString())
        .execute();

      if (!createOperation.getDone()) {
        throw new IncompleteOperationException(
          String.format(
            "Deletion of group '%s' was initiated, but hasn't completed",
            groupId.email));
      }
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
    }
  }

  //---------------------------------------------------------------------
  // Manage memberships.
  //---------------------------------------------------------------------

  /**
   * Look up a membership ID by group and user email.
   */
  private MembershipId lookupGroupMembership(
    CloudIdentity client,
    GroupId groupId,
    UserEmail userEmail
  ) throws AccessException, IOException {
    try {
      return new MembershipId(client
        .groups()
        .memberships()
        .lookup(groupId.toString())
        .setMemberKeyId(userEmail.email)
        .execute()
        .getName());
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null;
    }
  }

  /**
   * Get details for an existing group membership.
   */
  private Membership getMembership(
    CloudIdentity client,
    MembershipId membershipId
  ) throws AccessException, IOException {
    try {
      return client
        .groups()
        .memberships()
        .get(membershipId.id)
        .execute();
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null;
    }
  }

  /**
   * Get details for an existing group membership.
   */
  public Membership getMembership(
    MembershipId membershipId
  ) throws AccessException, IOException {
    return getMembership(createClient(), membershipId);
  }

  /**
   * Get details for an existing group membership.
   */
  public Membership getMembership(
    GroupId groupId,
    UserEmail userEmail
  ) throws AccessException, IOException {
    var client = createClient();
    var id = lookupGroupMembership(client, groupId, userEmail);
    return getMembership(client, id);
  }

  /**
   * Delete a group membership in an idempotent way.
   */
  public void deleteMembership(
    MembershipId membershipId
  ) throws AccessException, IOException {
    try {
      createClient()
        .groups()
        .memberships()
        .delete(membershipId.id)
        .execute();
    }
    catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        //
        // Not found, that's ok.
        //
      }
      else {
        translateAndThrowApiException(e);
      }
    }
  }

  private MembershipId updateMembership(
    CloudIdentity client,
    GroupId groupId,
    UserEmail userEmail,
    MembershipRole role
  ) throws AccessException, IOException {
    var membershipId = lookupGroupMembership(client, groupId, userEmail);
    try {
      client
        .groups()
        .memberships()
        .modifyMembershipRoles(
          membershipId.id,
          new ModifyMembershipRolesRequest()
            .setUpdateRolesParams(List.of(
              new UpdateMembershipRolesParams()
                .setFieldMask("expiryDetail.expire_time")
                .setMembershipRole(role))))
        .execute();

      return membershipId;
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null;
    }
  }

  /**
   * Add a member to a group in an idempotent way.
   */
  public MembershipId addMembership(
    GroupId groupId,
    UserEmail userEmail,
    Instant expiry
  ) throws AccessException, IOException {
    var client = createClient();

    var role = new MembershipRole()
      .setName("MEMBER")
      .setExpiryDetail(new ExpiryDetail()
        .setExpireTime(expiry
          .atOffset(ZoneOffset.UTC)
          .truncatedTo(ChronoUnit.SECONDS)
          .format(DateTimeFormatter.ISO_DATE_TIME)));

    try {
      //
      // Try to add new membership.
      //
      var operation = client
        .groups()
        .memberships()
        .create(
          groupId.toString(),
          new Membership()
            .setPreferredMemberKey(new EntityKey().setId(userEmail.email))
            .setRoles(List.of(role)))
        .execute();

      if (!operation.getDone()) {
        throw new IncompleteOperationException(
          String.format(
            "Adding membership to group '%s' was initiated, but hasn't completed",
            groupId.email));
      }

      return new MembershipId((String)operation.getResponse().get("name"));
    }
    catch (GoogleJsonResponseException e) {
      if (isAlreadyExistsError(e)) {
        //
        // Membership exists, but the expiry might be incorrect.
        //
        return updateMembership(client, groupId, userEmail, role);
      }
      else {
        translateAndThrowApiException(e);
        return null;
      }
    }
  }

  public Collection<MembershipRelation> searchDirectGroupMemberships(
    UserEmail userEmail
  ) throws AccessException, IOException {
    Preconditions.checkArgument(userEmail.email.indexOf('\'') < 0);

    try {
      var client = createClient();
      var result = new ArrayList<MembershipRelation>();
      String pageToken = null;
      do {
        var page = client
          .groups()
          .memberships()
          .searchDirectGroups("groups/-")
          .setQuery(String.format("member_key_id=='%s'", userEmail.email))
          .setPageToken(pageToken)
          .setPageSize(SEARCH_PAGE_SIZE)
          .execute();

        if (page.getMemberships() != null) {
          result.addAll(page.getMemberships());
        }

        pageToken = page.getNextPageToken();
      } while (pageToken != null);

      return result;
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null;
    }
  }

  //---------------------------------------------------------------------------
  // Inner classes.
  //---------------------------------------------------------------------------

  public record MembershipId(String id) {}

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
