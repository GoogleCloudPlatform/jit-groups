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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Catalog that implements self approval and MPA based
 * activation for project role-based privileges.
 */
@Singleton
public class MpaProjectRoleCatalog implements RequesterPrivilegeCatalog<ProjectRoleBinding, ProjectId> {
  private final @NotNull ProjectRoleRepository repository;
  private final @NotNull ResourceManagerClient resourceManagerClient;
  private final @NotNull Options options;

  public MpaProjectRoleCatalog(
      @NotNull ProjectRoleRepository repository,
      @NotNull ResourceManagerClient resourceManagerClient,
      @NotNull Options options) {
    Preconditions.checkNotNull(repository, "repository");
    Preconditions.checkNotNull(resourceManagerClient, "resourceManagerClient");
    Preconditions.checkNotNull(options, "options");

    this.repository = repository;
    this.resourceManagerClient = resourceManagerClient;
    this.options = options;
  }

  void validateRequest(@NotNull ActivationRequest<ProjectRoleBinding> request) {
    Preconditions.checkNotNull(request, "request");
    Preconditions.checkArgument(
        request.duration().toSeconds() >= this.options.minActivationDuration().toSeconds(),
        String.format(
            "The activation duration must be no shorter than %d minutes",
            this.options.minActivationDuration().toMinutes()));
    Preconditions.checkArgument(
        request.duration().toSeconds() <= this.options.maxActivationDuration().toSeconds(),
        String.format(
            "The activation duration must be no longer than %d minutes",
            this.options.maxActivationDuration().toMinutes()));

    if (request.activationType() instanceof PeerApproval
        || request.activationType() instanceof ExternalApproval) {
      Preconditions.checkArgument(
          request.reviewers() != null &&
              request.reviewers()
                  .size() >= this.options.minNumberOfReviewersPerActivationRequest,
          String.format(
              "At least %d reviewers must be specified",
              this.options.minNumberOfReviewersPerActivationRequest));
      Preconditions.checkArgument(
          request.reviewers().size() <= this.options.maxNumberOfReviewersPerActivationRequest,
          String.format(
              "The number of reviewers must not exceed %s",
              this.options.maxNumberOfReviewersPerActivationRequest));
    }
  }

  void verifyUserCanActivateRequesterPrivileges(
      @NotNull UserEmail user,
      @NotNull ProjectId projectId,
      @NotNull ActivationType activationType,
      @NotNull Collection<ProjectRoleBinding> privileges) throws AccessException, IOException {
    // Verify that the user has eligible role bindings
    // for all privileges.
    //
    // NB. It doesn't matter whether the user has already
    // activated the role.
    //

    var userPrivileges = this.repository
        .findRequesterPrivileges(
            user,
            projectId,
            Set.of(activationType),
            EnumSet.of(RequesterPrivilege.Status.INACTIVE))
        .available()
        .stream()
        .collect(Collectors.toMap(privilege -> privilege.id(), privilege -> privilege));

    assert userPrivileges.values().stream().allMatch(e -> e.status() == RequesterPrivilege.Status.INACTIVE);

    for (var requestedPrivilege : privileges) {
      var grantedPrivilege = userPrivileges.get(requestedPrivilege);
      if (grantedPrivilege == null) {
        throw new AccessDeniedException(
            String.format(
                "The user %s is not allowed to activate %s using %s",
                user,
                requestedPrivilege.id(),
                activationType.name()));
      }
      var grantedPrivilegeType = grantedPrivilege.activationType();
      if (!grantedPrivilegeType.isParentTypeOf(activationType)) {
        throw new AccessDeniedException(
            String.format(
                "The user %s is not allowed to activate %s using %s",
                user,
                requestedPrivilege.id(),
                activationType.name()));
      }
    }
  }

  void verifyUserCanReviewRequest(
      UserEmail user,
      ProjectId projectId,
      ActivationType activationType,
      ProjectRoleBinding privilege) throws AccessException, IOException {

    var reviewers = this.repository
        .findReviewerPrivelegeHolders(
            privilege,
            activationType);

    if (!reviewers.contains(user)) {
      throw new AccessDeniedException(String.format(
          "The user %s is not allowed to review %s activation request of type %s",
          user,
          privilege.id(),
          activationType));
    }
  }

  public Options options() {
    return this.options;
  }

  // ---------------------------------------------------------------------------
  // Overrides.
  // ---------------------------------------------------------------------------

  @Override
  public SortedSet<ProjectId> listScopes(
      UserEmail user) throws AccessException, IOException {
    if (Strings.isNullOrEmpty(this.options.availableProjectsQuery)) {
      //
      // Find projects for which the user has any role bindings (eligible
      // or regular bindings). This method is slow, but accurate.
      //
      return this.repository.findProjectsWithRequesterPrivileges(user);
    } else {
      //
      // List all projects that the application's service account
      // can enumerate. This method is fast, but almost certainly
      // returns some projects that the user doesn't have any
      // privileges for. Depending on the nature of the projects,
      // this might be acceptable or considered information disclosure.
      //
      return this.resourceManagerClient.searchProjectIds(
          this.options.availableProjectsQuery);
    }
  }

  @Override
  public RequesterPrivilegeSet<ProjectRoleBinding> listRequesterPrivileges(
      UserEmail user,
      ProjectId projectId) throws AccessException, IOException {
    return this.repository.findRequesterPrivileges(
        user,
        projectId,
        Set.of(new SelfApproval(), new PeerApproval(""),
            new ExternalApproval("")),
        EnumSet.of(RequesterPrivilege.Status.INACTIVE, RequesterPrivilege.Status.ACTIVE));
  }

  @Override
  public @NotNull SortedSet<UserEmail> listReviewers(
      UserEmail requestingUser,
      @NotNull RequesterPrivilege<ProjectRoleBinding> privilege) throws AccessException, IOException {

    //
    // Check that the requesting user is allowed to request approval,
    // and isn't just trying to do enumeration.
    //

    verifyUserCanActivateRequesterPrivileges(
        requestingUser,
        privilege.id().projectId(),
        privilege.activationType(),
        List.of(privilege.id()));

    return this.repository
        .findReviewerPrivelegeHolders(privilege.id(), privilege.activationType())
        .stream()
        .filter(u -> !u.equals(requestingUser)) // Exclude requesting user
        .collect(Collectors.toCollection(TreeSet::new));
  }

  @Override
  public void verifyUserCanRequest(
      @NotNull ActivationRequest<ProjectRoleBinding> request) throws AccessException, IOException {

    validateRequest(request);

    //
    // Check if the requesting user is allowed to activate this
    // privilege.
    //
    verifyUserCanActivateRequesterPrivileges(
        request.requestingUser(),
        ProjectActivationRequest.projectId(request),
        request.activationType(),
        Set.of(request.requesterPrivilege()));
  }

  @Override
  public void verifyUserCanApprove(
      UserEmail approvingUser,
      @NotNull ActivationRequest<ProjectRoleBinding> request) throws AccessException, IOException {

    validateRequest(request);

    //
    // Check if the approving user (!) is allowed to activate this
    // privilege.
    //
    // NB. The base class already checked that the requesting user
    // is allowed.
    //
    switch (request.activationType().name()) {
      case "NONE":
        throw new IllegalArgumentException("Activation request of type none cannot be approved.");
      case "SELF_APPROVAL":
        if (request.requestingUser() != approvingUser) {
          throw new AccessDeniedException(
              String.format(
                  "%s is not allowed to approve self-approval activation request created by %s.",
                  approvingUser,
                  request.requestingUser()));
        }
        break;
      default:
        verifyUserCanReviewRequest(
            approvingUser,
            ProjectActivationRequest.projectId(request),
            request.activationType(),
            request.requesterPrivilege());
        ;
    }

  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  /**
   * If a query is provided, the class performs a Resource Manager project
   * search instead of Policy Analyzer query to list projects. This is faster,
   * but results in non-personalized results.
   *
   * @param availableProjectsQuery optional, search query, for example:
   *                               - parent:folders/{folder_id}
   * @param maxActivationDuration  maximum duration for an activation
   */
  public record Options(
      String availableProjectsQuery,
      Duration maxActivationDuration,
      int minNumberOfReviewersPerActivationRequest,
      int maxNumberOfReviewersPerActivationRequest) {

    static final int MIN_ACTIVATION_TIMEOUT_MINUTES = 5;

    public Options {
      Preconditions.checkNotNull(maxActivationDuration, "maxActivationDuration");

      Preconditions.checkArgument(!maxActivationDuration.isNegative());
      Preconditions.checkArgument(
          maxActivationDuration.toMinutes() >= MIN_ACTIVATION_TIMEOUT_MINUTES,
          "Activation timeout must be at least 5 minutes");
      Preconditions.checkArgument(
          minNumberOfReviewersPerActivationRequest > 0,
          "The minimum number of reviewers cannot be 0");
      Preconditions.checkArgument(
          minNumberOfReviewersPerActivationRequest <= maxNumberOfReviewersPerActivationRequest,
          "The minimum number of reviewers must not exceed the maximum");
    }

    public Duration minActivationDuration() {
      return Duration.ofMinutes(MIN_ACTIVATION_TIMEOUT_MINUTES);
    }
  }
}
