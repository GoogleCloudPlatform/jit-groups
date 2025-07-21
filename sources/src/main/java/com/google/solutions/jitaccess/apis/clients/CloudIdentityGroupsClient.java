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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudidentity.v1.CloudIdentity;
import com.google.api.services.cloudidentity.v1.model.*;
import com.google.api.services.groupssettings.Groupssettings;
import com.google.api.services.groupssettings.model.Groups;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.CustomerId;
import com.google.solutions.jitaccess.auth.GroupId;
import com.google.solutions.jitaccess.auth.IamPrincipalId;
import com.google.solutions.jitaccess.common.Coalesce;
import jakarta.inject.Singleton;
import org.crac.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Client for the Cloud Identity Groups API.
 */
@Singleton
public class CloudIdentityGroupsClient {
  public static final String OAUTH_GROUPS_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  public static final String OAUTH_SETTINGS_SCOPE = "https://www.googleapis.com/auth/apps.groups.settings";
  private static final String PREDICATE_USERS_AND_SERVICE_ACCOUNTS_ONLY =
    "member.type == 1 || member.type == 2";
  private static final int SEARCH_PAGE_SIZE = 1000;
  public static final String LABEL_DISCUSSION_FORUM = "cloudidentity.googleapis.com/groups.discussion_forum";
  public static final String LABEL_SECURITY = "cloudidentity.googleapis.com/groups.security";
  private static final int MAX_GROUP_SETTINGS_PATCH_ATTEMPTS = 5;

  private final @NotNull Options options;
  private final @NotNull GoogleCredentials credentials;
  private final @NotNull HttpTransport.Options httpOptions;

  /**
   * Default, restrictive access settings:
   *
   * - Allow external members.
   * - Disable most self-service features on groups.google.com to
   *   the extent possible.
   *
   */
  private final @NotNull Groups RESTRICTED_SETTINGS = new Groups()
    .setIncludeInGlobalAddressList(Boolean.FALSE.toString())
    .setAllowExternalMembers(Boolean.TRUE.toString())
    .setAllowWebPosting(Boolean.FALSE.toString())
    .setShowInGroupDirectory(Boolean.FALSE.toString())
    .setWhoCanAdd("ALL_OWNERS_CAN_ADD")
    .setWhoCanApproveMembers("NONE_CAN_APPROVE")
    .setWhoCanContactOwner("ALL_MANAGERS_CAN_CONTACT")
    .setWhoCanDiscoverGroup("ALL_MEMBERS_CAN_DISCOVER")
    .setWhoCanInvite("NONE_CAN_INVITE")
    .setWhoCanJoin("INVITED_CAN_JOIN")
    .setWhoCanLeaveGroup("NONE_CAN_LEAVE")
    .setWhoCanModerateMembers("OWNERS_ONLY")
    .setWhoCanModifyMembers("OWNERS_ONLY")
    .setWhoCanPostAnnouncements("OWNERS_ONLY")
    .setWhoCanPostMessage("ALL_OWNERS_CAN_POST")
    .setWhoCanViewGroup("ALL_MANAGERS_CAN_VIEW")
    .setWhoCanViewMembership("ALL_MANAGERS_CAN_VIEW");

  /**
   * GKE compatible access settings.
   */
  private  final @NotNull Groups GKE_COMPATIBLE = RESTRICTED_SETTINGS
    .setWhoCanViewMembership("ALL_MEMBERS_CAN_VIEW");

  public CloudIdentityGroupsClient(
    @NotNull GoogleCredentials credentials,
    @NotNull Options options,
    @NotNull HttpTransport.Options httpOptions
  ) {
    Preconditions.checkNotNull(credentials, "credentials");
    Preconditions.checkNotNull(options, "options");
    Preconditions.checkNotNull(httpOptions, "httpOptions");

    this.credentials = credentials;
    this.options = options;
    this.httpOptions = httpOptions;
  }

  private @NotNull CloudIdentity createClient() throws IOException {
    return Builders
      .newBuilder(CloudIdentity.Builder::new, this.credentials, this.httpOptions)
      .build();
  }

  private @NotNull Groupssettings createSettingsClient() throws IOException {
    return Builders
      .newBuilder(Groupssettings.Builder::new, this.credentials, this.httpOptions)
      .build();
  }

  private static boolean isAlreadyExistsError(
    @NotNull GoogleJsonResponseException e
  ) {
    return
      e.getStatusCode() == 409 &&
        "ALREADY_EXISTS".equals(e.getDetails().get("status"));
  }

  private static void translateAndThrowApiException(
    @NotNull GoogleJsonResponseException e
  ) throws AccessException, IOException {
    switch (e.getStatusCode()) {
      case 400:
        throw new IllegalArgumentException("Invalid argument", e);
      case 401:
        throw new NotAuthenticatedException("Not authenticated", e);
      case 403: {
        if (e.getDetails() != null) {
          var message = e.getDetails().get("message");
          if (message instanceof String &&
            (((String) message).contains("3005") || ((String) message).contains("3006"))) {
            throw new AccessDeniedException("This feature requires a Cloud Identity Premium or Workspace subscription");
          }
        }

        throw new AccessDeniedException(
          "The group or membership does not exist in Cloud Identity, or access to Cloud Identity API is denied", e);
      }
      case 404:
        throw new ResourceNotFoundException("The group or membership does not exist", e);
      default:
        throw (GoogleJsonResponseException)e.fillInStackTrace();
    }
  }

  /**
   * Update group settings to restrictive defaults.
   */
  private void setGroupAccess(
    @NotNull GroupId emailAddress,
    @NotNull AccessProfile profile
  ) throws IOException {
    var settingsClient = createSettingsClient();

    var settings = profile == AccessProfile.GkeCompatible
      ? this.GKE_COMPATIBLE
      : this.RESTRICTED_SETTINGS;

    //
    // The group settings API is prone to fail for newly created groups.
    //
    for (int attempt = 0; attempt < MAX_GROUP_SETTINGS_PATCH_ATTEMPTS; attempt++) {
      try {
        settingsClient
          .groups()
          .update(emailAddress.email, settings)
          .execute();

        //
        // Successful update -> quit loop.
        //
        return;
      }
      catch (GoogleJsonResponseException e) {
        if (
          e.getStatusCode() == 404 ||
          e.getStatusCode() == 400 &&
          e.getDetails() != null &&
          e.getDetails().getErrors() != null &&
          e.getDetails().getErrors()
            .stream()
            .anyMatch(err -> e.getMessage() != null && err.getMessage().contains("INVALID_GAIA_GROUP"))) {

          //
          // This is most likely an intermittent error.
          //
          try {
            Thread.sleep(200);
          }
          catch (InterruptedException ignored) {
          }
        }
        else {
          throw (GoogleJsonResponseException) e.fillInStackTrace();
        }
      }
    }
  }

  //---------------------------------------------------------------------
  // Manage groups.
  //---------------------------------------------------------------------

  /**
   * Lookup a group ID by email.
   */
  @SuppressWarnings({"DataFlowIssue"})
  private @NotNull GroupKey lookupGroup(
    @NotNull CloudIdentity client,
    @NotNull GroupId email
  ) throws AccessException, IOException {
    try {
      var id = client
        .groups()
        .lookup()
        .setGroupKeyId(email.email)
        .execute()
        .getName();

      return new GroupKey(id);
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null; // Unreachable.
    }
  }

  /**
   * Get details for an existing group.
   */
  private @NotNull Group getGroup(
    @NotNull CloudIdentity client,
    @NotNull GroupKey groupKey
  ) throws AccessException, IOException {
    try {
      return client
        .groups()
        .get(groupKey.toString())
        .execute();
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null; // Unreachable.
    }
  }

  /**
   * Get details for an existing group.
   */
  public @NotNull Group getGroup(
    @NotNull GroupKey groupKey
  ) throws AccessException, IOException {
    return getGroup(createClient(), groupKey);
  }

  /**
   * Get details for an existing group.
   */
  public @NotNull Group getGroup(
    @NotNull GroupId groupId
  ) throws AccessException, IOException {
    var client = createClient();
    return getGroup(client, lookupGroup(client, groupId));
  }

  /**
   * Lookup a group ID by email.
   */
  public @NotNull GroupKey lookupGroup(
    @NotNull GroupId groupId
  ) throws AccessException, IOException {
    return lookupGroup(createClient(), groupId);
  }

  /**
   * Create group in an idempotent way.
   */
  public @NotNull GroupKey createGroup(
    @NotNull GroupId emailAddress,
    @NotNull GroupType type,
    @NotNull String displayName,
    @NotNull String description,
    @NotNull AccessProfile accessProfile
  ) throws AccessException, IOException {
    try {
      var labels = new HashMap<String, String>();
      labels.put(LABEL_DISCUSSION_FORUM, "");

      if (type == GroupType.Security) {
        labels.put(LABEL_SECURITY, "");
      }

      var group = new Group()
        .setParent("customers/" + this.options.customerId)
        .setGroupKey(new EntityKey().setId(emailAddress.email))
        .setDisplayName(displayName)
        .setDescription(description)
        .setLabels(labels);

      var client = createClient();

      //
      // Try to create the group. This might fail if it already exists.
      //

      GroupKey groupKey;
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

        groupKey = new GroupKey((String)createOperation.getResponse().get("name"));
      }
      catch (GoogleJsonResponseException e) {
        if (isAlreadyExistsError(e) || e.getStatusCode() == 403) {
          //
          // Group already exists. That's ok, but we need to find out
          // its ID.
          //
          // NB. A 403 could also be a permission-denied error. If that's
          // the case, the following call will fail.
          //
          groupKey = lookupGroup(client, emailAddress);
        }
        else {
          throw (GoogleJsonResponseException)e.fillInStackTrace();
        }
      }

      //
      // Lock down security settings.
      //
      var updateOperation = client
        .groups()
        .updateSecuritySettings(
          String.format("%s/securitySettings", groupKey),
          new SecuritySettings()
            .setMemberRestriction(new MemberRestriction()
              .setQuery(PREDICATE_USERS_AND_SERVICE_ACCOUNTS_ONLY)))
          .setUpdateMask("memberRestriction.query")
        .execute();

      if (!updateOperation.getDone()) {
        throw new IncompleteOperationException(
          String.format(
            "Setting security settings for group '%s' was initiated, but hasn't completed",
            group.getGroupKey().getId()));
      }

      //
      // Lock down group settings.
      //
      setGroupAccess(emailAddress, accessProfile);

      return groupKey;
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null; // Unreachable.
    }
  }

  /**
   * Update the group description.
   */
  public void patchGroup(
    @NotNull GroupKey groupKey,
    @NotNull String description
  ) throws AccessException, IOException {
    try {
      var createOperation = createClient()
        .groups()
        .patch(groupKey.toString(), new Group().setDescription(description))
        .setUpdateMask("description")
        .execute();

      if (!createOperation.getDone()) {
        throw new IncompleteOperationException(
          String.format(
            "Patching of group '%s' was initiated, but hasn't completed",
            groupKey));
      }
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
    }
  }

  /**
   * Delete a group.
   */
  public void deleteGroup(
    @NotNull GroupKey groupKey
  ) throws AccessException, IOException {
    try {
      var createOperation = createClient()
        .groups()
        .delete(groupKey.toString())
        .execute();

      if (!createOperation.getDone()) {
        throw new IncompleteOperationException(
          String.format(
            "Deletion of group '%s' was initiated, but hasn't completed",
            groupKey));
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
  private @NotNull MembershipId lookupGroupMembership(
    @NotNull CloudIdentity client,
    @NotNull GroupKey groupKey,
    @NotNull IamPrincipalId member
  ) throws AccessException, IOException {
    try {
      return new MembershipId(client
        .groups()
        .memberships()
        .lookup(groupKey.toString())
        .setMemberKeyId(member.value())
        .execute()
        .getName());
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null; // Unreachable.
    }
  }

  /**
   * Get details for an existing group membership.
   */
  private @NotNull Membership getMembership(
    @NotNull CloudIdentity client,
    @NotNull MembershipId membershipId
  ) throws AccessException, IOException {
    try {
      var membership = client
        .groups()
        .memberships()
        .get(membershipId.id)
        .execute();

      //
      // The API automatically filters out expired memberships.
      //
      assert membership
        .getRoles()
        .stream()
        .allMatch(
          r -> r.getExpiryDetail() == null ||
            Instant.parse(r.getExpiryDetail().getExpireTime()).isAfter(Instant.now()));

      return membership;
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null; // Unreachable.
    }
  }

  /**
   * Get details for an existing group membership.
   */
  public @NotNull Membership getMembership(
    @NotNull MembershipId membershipId
  ) throws AccessException, IOException {
    return getMembership(createClient(), membershipId);
  }

  /**
   * Get details for an existing group membership.
   */
  public @NotNull Membership getMembership(
    @NotNull GroupKey groupKey,
    @NotNull IamPrincipalId member
  ) throws AccessException, IOException {
    var client = createClient();
    var id = lookupGroupMembership(client, groupKey, member);
    return getMembership(client, id);
  }

  /**
   * Delete a group membership in an idempotent way.
   */
  private void deleteMembership(
    @NotNull CloudIdentity client,
    @NotNull MembershipId membershipId
  ) throws AccessException, IOException {
    try {
      client
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

  /**
   * Delete a group membership in an idempotent way.
   */
  public void deleteMembership(
    @NotNull MembershipId membershipId
  ) throws AccessException, IOException {
    deleteMembership(createClient(), membershipId);
  }

  /**
   * Delete a group membership in an idempotent way.
   */
  public void deleteMembership(
    @NotNull GroupId groupId,
    @NotNull IamPrincipalId member
  ) throws AccessException, IOException {
    var client = createClient();

    //
    // Lookup membership, assuming it exists.
    //
    MembershipId membershipId;
    try
    {
      membershipId = lookupGroupMembership(
        client,
        lookupGroup(groupId),
        member);
    }
    catch (AccessException e)
    {
      //
      // Membership doesn't exist, so there's nothing to delete.
      //
      return;
    }

    deleteMembership(client, membershipId);
  }

  private @NotNull MembershipId updateMembership(
    @NotNull CloudIdentity client,
    @NotNull GroupKey groupKey,
    @NotNull IamPrincipalId member,
    @NotNull MembershipRole role
  ) throws AccessException, IOException {
    var membershipId = lookupGroupMembership(client, groupKey, member);
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
      return null; // Unreachable.
    }
  }

  /**
   * Add a member to a group in an idempotent way.
   */
  private @NotNull MembershipId addMembership(
    @NotNull CloudIdentity client,
    @NotNull GroupKey groupKey,
    @NotNull IamPrincipalId member,
    @Nullable Instant expiry
  ) throws AccessException, IOException {
    var role = new MembershipRole()
      .setName("MEMBER");

    if (expiry != null) {
      role.setExpiryDetail(new ExpiryDetail()
        .setExpireTime(expiry
          .atOffset(ZoneOffset.UTC)
          .truncatedTo(ChronoUnit.SECONDS)
          .format(DateTimeFormatter.ISO_DATE_TIME)));
    }

    try {
      //
      // Try to add new membership.
      //
      var operation = client
        .groups()
        .memberships()
        .create(
          groupKey.toString(),
          new Membership()
            .setPreferredMemberKey(new EntityKey().setId(member.value()))
            .setRoles(List.of(role)))
        .execute();

      if (!operation.getDone()) {
        throw new IncompleteOperationException(
          String.format(
            "Adding membership to group '%s' was initiated, but hasn't completed",
            groupKey));
      }

      return new MembershipId((String)operation.getResponse().get("name"));
    }
    catch (GoogleJsonResponseException e) {
      if (isAlreadyExistsError(e)) {
        //
        // Membership exists, but the expiry might be incorrect.
        //
        return updateMembership(client, groupKey, member, role);
      }
      else {
        translateAndThrowApiException(e);
        return null; // Unreachable.
      }
    }
  }

  /**
   * Permanently add a member to a group in an idempotent way.
   */
  public @NotNull MembershipId addPermanentMembership(
    @NotNull GroupKey groupKey,
    @NotNull IamPrincipalId member
  ) throws AccessException, IOException {
    return addMembership(createClient(), groupKey, member, null);
  }

  /**
   * Permanently add a member to a group in an idempotent way.
   */
  public @NotNull MembershipId addPermanentMembership(
    @NotNull GroupId groupId,
    @NotNull IamPrincipalId member
  ) throws AccessException, IOException {
    var client = createClient();
    return addMembership(
      client,
      lookupGroup(client, groupId),
      member,
      null);
  }

  /**
   * Add a member to a group in an idempotent way.
   */
  public @NotNull MembershipId addMembership(
    @NotNull GroupKey groupKey,
    @NotNull IamPrincipalId member,
    @NotNull Instant expiry
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(expiry, "expiry");
    return addMembership(createClient(), groupKey, member, expiry);
  }

  /**
   * Add a member to a group in an idempotent way.
   */
  public @NotNull MembershipId addMembership(
    @NotNull GroupId groupId,
    @NotNull IamPrincipalId member,
    @NotNull Instant expiry
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(expiry, "expiry");

    var client = createClient();
    return addMembership(
      client,
      lookupGroup(client, groupId),
      member,
      expiry);
  }

  /**
   * List members of a group.
   */
  private @NotNull List<Membership> listMemberships(
    @NotNull CloudIdentity client,
    @NotNull GroupKey groupKey
  ) throws AccessException, IOException {
    try {
      var result = new ArrayList<Membership>();
      String pageToken = null;
      do {
        var page = client
          .groups()
          .memberships()
          .list(groupKey.toString())
          .setView("FULL") // Include expiry details
          .setPageToken(pageToken)
          .setPageSize(SEARCH_PAGE_SIZE)
          .execute();

        if (page.getMemberships() != null) {
          result.addAll(page.getMemberships());
        }

        pageToken = page.getNextPageToken();

        //
        // The API automatically filters out expired memberships.
        //
        assert Coalesce.emptyIfNull(page.getMemberships())
          .stream()
          .flatMap(m -> m.getRoles().stream())
          .allMatch(
            r -> r.getExpiryDetail() == null ||
            Instant.parse(r.getExpiryDetail().getExpireTime()).isAfter(Instant.now()));

      } while (pageToken != null);

      return Collections.unmodifiableList(result);
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null; // Unreachable.
    }
  }

  /**
   * List members of a group.
   */
  public @NotNull List<Membership> listMemberships(
    @NotNull GroupId groupId
  ) throws AccessException, IOException {
    var client = createClient();
    return listMemberships(client, lookupGroup(client, groupId));
  }

  /**
   * List groups a user is a member of.
   */
  public @NotNull List<MembershipRelation> listMembershipsByUser(
    @NotNull IamPrincipalId member
  ) throws AccessException, IOException {
    Preconditions.checkArgument(member.value().indexOf('\'') < 0);

    try {
      var client = createClient();
      var result = new LinkedList<MembershipRelation>();
      String pageToken = null;
      do {
        var page = client
          .groups()
          .memberships()
          .searchDirectGroups("groups/-")
          .setQuery(String.format("member_key_id=='%s'", member.value()))
          .setPageToken(pageToken)
          .setPageSize(SEARCH_PAGE_SIZE)
          .execute();

        if (page.getMemberships() != null) {
          result.addAll(page.getMemberships());
        }

        //
        // The API does **NOT** include expiry details.
        //
        assert page.getMemberships() == null || page
          .getMemberships()
          .stream()
          .flatMap(m -> m.getRoles().stream())
          .allMatch(r -> r.getExpiryDetail() == null);

        pageToken = page.getNextPageToken();
      } while (pageToken != null);

      return Collections.unmodifiableList(result);
    }
    catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 500) {
        //
        // The API returns a 500 if the user is invalid,
        // treat as a 404 instead.
        //
        throw new ResourceNotFoundException("Not found", e);
      }
      else {
        translateAndThrowApiException(e);
      }

      return null; // Unreachable.
    }
  }

  /**
   * Search for groups that match a certain CEL query.
   */
  public @NotNull List<Group> searchGroups(
    @NotNull String query,
    boolean fullDetails
  ) throws AccessException, IOException {
    try {
      var client = createClient();
      var result = new LinkedList<Group>();
      String pageToken = null;
      do {
        var page = client
          .groups()
          .search()
          .setQuery(query)
          .setPageToken(pageToken)
          .setPageSize(SEARCH_PAGE_SIZE)
          .setView(fullDetails ? "FULL" : "BASIC")
          .execute();

        if (page.getGroups() != null) {
          result.addAll(page.getGroups());
        }

        pageToken = page.getNextPageToken();
      } while (pageToken != null);

      return Collections.unmodifiableList(result);
    }
    catch (GoogleJsonResponseException e) {
      translateAndThrowApiException(e);
      return null; // Unreachable.
    }
  }

  /**
   * Search for groups by prefix.
   */
  public @NotNull List<Group> searchGroupsByPrefix(
    @NotNull String groupNamePrefix,
    boolean fullDetails
  ) throws AccessException, IOException {
    Preconditions.checkArgument(groupNamePrefix.indexOf('\'') < 0);

    return searchGroups(
      String.format("parent=='customers/%s' && group_key.startsWith('%s')",
        this.options.customerId,
        groupNamePrefix),
      fullDetails);
  }

  /**
   * Search groups by email address, can be used to validate if a
   * set of groups exists.
   */
  public @NotNull List<Group> searchGroupsById(
    @NotNull Set<GroupId> groupIds,
    boolean fullDetails
  ) throws AccessException, IOException {
    Preconditions.checkArgument(groupIds
      .stream()
      .allMatch(g -> g.email.indexOf('\'') < 0));

    return searchGroups(
      String.format(
        "parent=='customers/%s' && (%s)",
        this.options.customerId,
        String.join("||", groupIds.stream()
          .map(g -> String.format("group_key=='%s'", g.email))
          .toList())),
      fullDetails);
  }

  //---------------------------------------------------------------------------
  // Inner types.
  //---------------------------------------------------------------------------

  public enum GroupType {
    /**
     * Normal group. Creating this type of group doesn't require special
     * privileges.
     */
    DiscussionForum,

    /**
     * Security group. Creating this type of group requires the 'Groups Admin'
     * admin role, or an equivalent custom role that has the privilege to
     * assign security labels.
     */
    Security
  }

  public enum AccessProfile {
    /**
     * Use restrictive access settings.
     */
    Restricted,

    /**
     * Use access settings that are restrictive, but still compatible GKE RBAC.
     */
    GkeCompatible
  }

  public record MembershipId(String id) {}

  public record Options(
    @NotNull CustomerId customerId
  ) {
    public Options {
      Preconditions.checkNotNull(customerId, "customerId");
    }
  }

  static class IncompleteOperationException extends AccessException {
    public IncompleteOperationException(String message) {
      super(message);
    }
  }
}
