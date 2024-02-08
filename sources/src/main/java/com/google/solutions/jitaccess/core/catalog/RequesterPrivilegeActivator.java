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

package com.google.solutions.jitaccess.core.catalog;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AlreadyExistsException;
import com.google.solutions.jitaccess.core.UserId;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Activates requester privileges, for example by modifying IAM policies.
 */
public abstract class RequesterPrivilegeActivator<TPrivilegeId extends PrivilegeId> {
  private final JustificationPolicy policy;
  private final RequesterPrivilegeCatalog<TPrivilegeId> catalog;

  protected RequesterPrivilegeActivator(
      RequesterPrivilegeCatalog<TPrivilegeId> catalog,
      JustificationPolicy policy) {
    Preconditions.checkNotNull(catalog, "catalog");
    Preconditions.checkNotNull(policy, "policy");

    this.catalog = catalog;
    this.policy = policy;
  }

  /**
   * Create a new request to activate a privilege.
   */
  public final ActivationRequest<TPrivilegeId> createActivationRequest(
      UserId requestingUser,
      Set<UserId> reviewers,
      RequesterPrivilege<TPrivilegeId> requesterPrivilege,
      String justification,
      Instant startTime,
      Duration duration) throws AccessException, IOException {
    Preconditions.checkArgument(
        startTime.isAfter(Instant.now().minus(Duration.ofMinutes(1))),
        "Start time must not be in the past");

    var request = new ActivationRequest<TPrivilegeId>(
        ActivationId.newId(requesterPrivilege.activationType()),
        requestingUser,
        reviewers,
        requesterPrivilege.id(),
        requesterPrivilege.activationType(),
        justification,
        startTime,
        duration);

    this.catalog.verifyUserCanRequest(request);
    return request;
  }

  /**
   * Approve another user's request.
   */
  public final Activation<TPrivilegeId> approve(
      UserId approvingUser,
      ActivationRequest<TPrivilegeId> request) throws AccessException, AlreadyExistsException, IOException {
    Preconditions.checkNotNull(policy, "policy");

    if (!(request.reviewers().contains(approvingUser)
        || request.activationType() instanceof SelfApproval)) {
      throw new AccessDeniedException(
          String.format("The request does not permit approval by %s", approvingUser));
    }

    //
    // Check that the justification is ok.
    //
    policy.checkJustification(request.requestingUser(), request.justification());

    //
    // Check that the user is (still) allowed to request this privilege.
    //
    this.catalog.verifyUserCanRequest(request);

    //
    // Check that the approving user is (still) allowed to approve this privilege
    // request.
    //
    this.catalog.verifyUserCanApprove(approvingUser, request);

    //
    // Request is legit, apply it.
    //
    provisionAccess(approvingUser, request);

    return new Activation<>(request);
  }

  /**
   * Apply a request.
   */
  protected abstract void provisionAccess(
      UserId approvingUser,
      ActivationRequest<TPrivilegeId> request) throws AccessException, AlreadyExistsException, IOException;

  /**
   * Create a converter for turning MPA requests into JWTs, and
   * vice versa.
   */
  public abstract JsonWebTokenConverter<ActivationRequest<TPrivilegeId>> createTokenConverter();
}
