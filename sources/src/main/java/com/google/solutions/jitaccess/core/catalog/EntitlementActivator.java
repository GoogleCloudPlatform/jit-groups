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
 * Activates entitlements, for example by modifying IAM policies.
 */
public abstract class EntitlementActivator<TEntitlementId extends EntitlementId> {
  private final JustificationPolicy policy;
  private final EntitlementCatalog<TEntitlementId> catalog;

  protected EntitlementActivator(
    EntitlementCatalog<TEntitlementId> catalog,
    JustificationPolicy policy
  ) {
    Preconditions.checkNotNull(catalog, "catalog");
    Preconditions.checkNotNull(policy, "policy");

    this.catalog = catalog;
    this.policy = policy;
  }

  /**
   * Create a new request to activate an entitlement that permits self-approval.
   */
  public final SelfApprovalActivationRequest<TEntitlementId> createSelfApprovalRequest(
    UserId requestingUser,
    Set<TEntitlementId> entitlements,
    String justification,
    Instant startTime,
    Duration duration
  ) {
    Preconditions.checkArgument(
      startTime.isAfter(Instant.now().minus(Duration.ofMinutes(1))),
      "Start time must not be in the past");

    //
    // NB. There's no need to verify access at this stage yet.
    //
    return new SelfApprovalRequest<>(
      ActivationId.newId(ActivationType.SELF_APPROVAL),
      requestingUser,
      entitlements,
      justification,
      startTime,
      duration);
  }

  /**
   * Create a new request to activate an entitlement that requires
   * peer approval.
   */
  public PeerApprovalActivationRequest<TEntitlementId> createPeerApprovalRequest(
    UserId requestingUser,
    Set<TEntitlementId> entitlements,
    Set<UserId> reviewers,
    String justification,
    Instant startTime,
    Duration duration
  ) throws AccessException, IOException {

    Preconditions.checkArgument(
      startTime.isAfter(Instant.now().minus(Duration.ofMinutes(1))),
      "Start time must not be in the past");

    var request = new PeerApprovalRequest<>(
      ActivationId.newId(ActivationType.PEER_APPROVAL),
      requestingUser,
      entitlements,
      reviewers,
      justification,
      startTime,
      duration);

    //
    // Pre-verify access to avoid sending a peer approval requests for which
    // the access check will fail later.
    //
    this.catalog.verifyUserCanRequest(request);

    return request;
  }

    /**
   * Create a new request to activate an entitlement that requires
   * external approval.
   */
  public ExternalApprovalActivationRequest<TEntitlementId> createExternalApprovalRequest(
    UserId requestingUser,
    Set<TEntitlementId> entitlements,
    Set<UserId> reviewers,
    String justification,
    Instant startTime,
    Duration duration
  ) throws AccessException, IOException {

    Preconditions.checkArgument(
      startTime.isAfter(Instant.now().minus(Duration.ofMinutes(1))),
      "Start time must not be in the past");

    var request = new ExternalApprovalRequest<>(
      ActivationId.newId(ActivationType.EXTERNAL_APPROVAL),
      requestingUser,
      entitlements,
      reviewers,
      justification,
      startTime,
      duration);

    //
    // Pre-verify access to avoid sending an external approval requests for which
    // the access check will fail later.
    //
    this.catalog.verifyUserCanRequest(request);

    return request;
  }

  /**
   * Approve another user's request.
   */
  public final Activation<TEntitlementId> approve(
    UserId approvingUser,
    ActivationRequest<TEntitlementId> request
  ) throws AccessException, AlreadyExistsException, IOException
  {
    Preconditions.checkNotNull(policy, "policy");

    if (!request.reviewers().contains(approvingUser)) {
      throw new AccessDeniedException(
        String.format("The request does not permit approval by %s", approvingUser));
    }

    //
    // Check that the justification is ok.
    //
    policy.checkJustification(request.requestingUser(), request.justification());

    //
    // Check that the user is (still) allowed to request this entitlement.
    //
    this.catalog.verifyUserCanRequest(request);

    //
    // Check that the approving user is (still) allowed to approve this entitlement.
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
    ActivationRequest<TEntitlementId> request
  ) throws AccessException, AlreadyExistsException, IOException;

  /**
   * Create a converter for turning Peer approval requests into JWTs, and
   * vice versa.
   */
  public abstract JsonWebTokenConverter<ActivationRequest<TEntitlementId>> createTokenConverter();

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  protected static class SelfApprovalRequest<TEntitlementId extends EntitlementId>
    extends SelfApprovalActivationRequest<TEntitlementId> {
    public SelfApprovalRequest(
      ActivationId id,
      UserId requestingUser,
      Set<TEntitlementId> entitlements,
      String justification,
      Instant startTime,
      Duration duration
    ) {
      super(id, requestingUser, entitlements, justification, startTime, duration);
    }
  }

  protected static class PeerApprovalRequest<TEntitlementId extends EntitlementId>
    extends PeerApprovalActivationRequest<TEntitlementId> {
    public PeerApprovalRequest(
      ActivationId id,
      UserId requestingUser,
      Set<TEntitlementId> entitlements,
      Set<UserId> reviewers,
      String justification,
      Instant startTime,
      Duration duration
    ) {
      super(id, requestingUser, entitlements, reviewers, justification, startTime, duration);

      if (entitlements.size() != 1) {
        throw new IllegalArgumentException("Only one entitlement can be activated at a time");
      }
    }
  }

  protected static class ExternalApprovalRequest<TEntitlementId extends EntitlementId>
    extends ExternalApprovalActivationRequest<TEntitlementId> {
    public ExternalApprovalRequest(
      ActivationId id,
      UserId requestingUser,
      Set<TEntitlementId> entitlements,
      Set<UserId> reviewers,
      String justification,
      Instant startTime,
      Duration duration
    ) {
      super(id, requestingUser, entitlements, reviewers, justification, startTime, duration);

      if (entitlements.size() != 1) {
        throw new IllegalArgumentException("Only one entitlement can be activated at a time");
      }
    }
  }
}
