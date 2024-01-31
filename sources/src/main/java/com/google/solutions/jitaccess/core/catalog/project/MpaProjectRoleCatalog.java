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
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Catalog that implements JIT and approval based
 * activation for project role-based entitlements.
 */
@Singleton
public class MpaProjectRoleCatalog extends ProjectRoleCatalog {
  private final ProjectRoleRepository repository;
  private final ResourceManagerClient resourceManagerClient;
  private final Options options;

  public MpaProjectRoleCatalog(
    ProjectRoleRepository repository,
    ResourceManagerClient resourceManagerClient,
    Options options
  ) {
    Preconditions.checkNotNull(repository, "repository");
    Preconditions.checkNotNull(resourceManagerClient, "resourceManagerClient");
    Preconditions.checkNotNull(options, "options");

    this.repository = repository;
    this.resourceManagerClient = resourceManagerClient;
    this.options = options;
  }

  void validateRequest(ActivationRequest<ProjectRoleBinding> request) {
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

    if (request instanceof PeerApprovalActivationRequest<ProjectRoleBinding> peerApprovalRequest) {
      Preconditions.checkArgument(
        peerApprovalRequest.reviewers() != null &&
          peerApprovalRequest.reviewers().size() >= this.options.minNumberOfReviewersPerActivationRequest,
        String.format(
          "At least %d reviewers must be specified",
          this.options.minNumberOfReviewersPerActivationRequest ));
      Preconditions.checkArgument(
        peerApprovalRequest.reviewers().size() <= this.options.maxNumberOfReviewersPerActivationRequest,
        String.format(
          "The number of reviewers must not exceed %s",
          this.options.maxNumberOfReviewersPerActivationRequest));
    }
    if (request instanceof ExternalApprovalActivationRequest<ProjectRoleBinding> externalApprovalRequest) {
      Preconditions.checkArgument(
        externalApprovalRequest.reviewers() != null &&
          externalApprovalRequest.reviewers().size() >= this.options.minNumberOfReviewersPerActivationRequest,
        String.format(
          "At least %d reviewers must be specified",
          this.options.minNumberOfReviewersPerActivationRequest ));
      Preconditions.checkArgument(
        externalApprovalRequest.reviewers().size() <= this.options.maxNumberOfReviewersPerActivationRequest,
        String.format(
          "The number of reviewers must not exceed %s",
          this.options.maxNumberOfReviewersPerActivationRequest));
    }
  }

  void verifyUserCanActivateEntitlements(
    UserId user,
    ProjectId projectId,
    ActivationType activationType,
    Collection<ProjectRoleBinding> entitlements
  ) throws AccessException, IOException {
    //
    // Verify that the user has eligible role bindings
    // for all entitlements.
    //
    // NB. It doesn't matter whether the user has already
    // activated the role.
    //
    var userEntitlements = this.repository
      .findEntitlements(
        user,
        projectId,
        EnumSet.copyOf(Stream.of(EntitlementType.JIT, EntitlementType.PEER, EntitlementType.REQUESTER, EntitlementType.REVIEWER).filter(e -> e.activationType == activationType).toList()),
        EnumSet.of(Entitlement.Status.AVAILABLE))
      .availableEntitlements()
      .stream()
      .collect(Collectors.toMap(ent -> ent.id(), ent -> ent));

    assert userEntitlements.values().stream().allMatch(e -> e.entitlementType().activationType == activationType);
    assert userEntitlements.values().stream().allMatch(e -> e.status() == Entitlement.Status.AVAILABLE);

    for (var requestedEntitlement : entitlements) {
      var grantedEntitlement = userEntitlements.get(requestedEntitlement);
      if (grantedEntitlement == null) {
        throw new AccessDeniedException(
          String.format(
            "The user %s is not allowed to activate %s using %s",
            user,
            requestedEntitlement.id(),
            activationType));
      }
    }
  }

  public Options options() {
    return this.options;
  }

  //---------------------------------------------------------------------------
  // Overrides.
  //---------------------------------------------------------------------------

  @Override
  public SortedSet<ProjectId> listProjects(
    UserId user
  ) throws AccessException, IOException {
    if (Strings.isNullOrEmpty(this.options.availableProjectsQuery)) {
      //
      // Find projects for which the user has any role bindings (eligible
      // or regular bindings). This method is slow, but accurate.
      //
      return this.repository.findProjectsWithEntitlements(user);
    }
    else {
      //
      // List all projects that the application's service account
      // can enumerate. This method is fast, but almost certainly
      // returns some projects that the user doesn't have any
      // entitlements for. Depending on the nature of the projects,
      // this might be acceptable or considered information disclosure.
      //
      return this.resourceManagerClient.searchProjectIds(
        this.options.availableProjectsQuery);
    }
  }

  @Override
  public EntitlementSet<ProjectRoleBinding> listEntitlements(
    UserId user,
    ProjectId projectId
  ) throws AccessException, IOException {
    return this.repository.findEntitlements(
      user,
      projectId,
      EnumSet.of(EntitlementType.JIT, EntitlementType.PEER, EntitlementType.REQUESTER),
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));
  }

  @Override
  public SortedSet<UserId> listReviewers(
    UserId requestingUser,
    Entitlement<ProjectRoleBinding> entitlement
  ) throws AccessException, IOException {

    //
    // Check that the requesting user is allowed to request approval,
    // and isn't just trying to do enumeration.
    //

    verifyUserCanActivateEntitlements(
      requestingUser,
      entitlement.id().projectId(),
      entitlement.entitlementType().activationType,
      List.of(entitlement.id()));

    EntitlementType reviewerEntitlementType = EntitlementType.NONE;
    switch (entitlement.entitlementType().activationType) {
      case SELF_APPROVAL:
        return new TreeSet<UserId>(List.of(requestingUser));
      case PEER_APPROVAL:
        reviewerEntitlementType = EntitlementType.PEER;
        break;
      case EXTERNAL_APPROVAL:
        reviewerEntitlementType = EntitlementType.REVIEWER;
        break;
      case NONE:
        return new TreeSet<UserId>();
    }
    return this.repository
      .findEntitlementHolders(entitlement.id(), reviewerEntitlementType)
      .stream()
      .filter(u -> !u.equals(requestingUser)) // Exclude requesting user
      .collect(Collectors.toCollection(TreeSet::new));
  }

  @Override
  public void verifyUserCanRequest(
    ActivationRequest<ProjectRoleBinding> request
  ) throws AccessException, IOException {

    validateRequest(request);

    //
    // Check if the requesting user is allowed to activate this
    // entitlement.
    //
    verifyUserCanActivateEntitlements(
      request.requestingUser(),
      ProjectActivationRequest.projectId(request),
      request.type(),
      request.entitlements());
  }

  @Override
  public void verifyUserCanApprove(
    UserId approvingUser,
    ActivationRequest<ProjectRoleBinding> request
  ) throws AccessException, IOException {

    validateRequest(request);

    //
    // Check if the approving user (!) is allowed to activate this
    // entitlement.
    //
    // NB. The base class already checked that the requesting user
    // is allowed.
    //
    verifyUserCanActivateEntitlements(
      approvingUser,
      ProjectActivationRequest.projectId(request),
      request.type(),
      request.entitlements());
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
   *      - parent:folders/{folder_id}
   * @param maxActivationDuration maximum duration for an activation
   */
  public record Options(
    String availableProjectsQuery,
    Duration maxActivationDuration,
    int minNumberOfReviewersPerActivationRequest,
    int maxNumberOfReviewersPerActivationRequest
  ) {
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
