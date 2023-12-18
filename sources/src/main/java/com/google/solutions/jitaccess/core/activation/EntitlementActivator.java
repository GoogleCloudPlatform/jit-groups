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

package com.google.solutions.jitaccess.core.activation;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.UserId;

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
  public final JitActivationRequest<TEntitlementId> createJitRequest(
    UserId requestingUser,
    Set<TEntitlementId> entitlements,
    String justification,
    Instant startTime,
    Instant endTime
  ) {
    //
    // NB. There's no need to verify access at this stage yet.
    //
    return new JitRequest(
      requestingUser,
      entitlements,
      justification,
      startTime,
      endTime);
  }

  /**
   * Create a new request to activate an entitlement that requires
   * multi-party approval.
   */
  public MpaActivationRequest<TEntitlementId> createMpaRequest(
    UserId requestingUser,
    Set<TEntitlementId> entitlements,
    Set<UserId> reviewers,
    String justification,
    Instant startTime,
    Instant endTime
  ) throws AccessException {

    //
    // Pre-verify access to avoid sending an MPA requests for which
    // the access check will fail later.
    //
    this.catalog.canRequest(requestingUser, entitlements);

    return new MpaRequest(
      requestingUser,
      entitlements,
      reviewers,
      justification,
      startTime,
      endTime);
  }

  /**
   * Activate an entitlement that permits self-approval.
   */
  public final Activation<TEntitlementId> activate(
    JitActivationRequest<TEntitlementId> request
  ) throws AccessException
  {
    Preconditions.checkNotNull(policy, "policy");

    //
    // Check that the justification is ok.
    //
    policy.checkJustification(request.requestingUser(), request.justification());

    //
    // Check that the user is (still) allowed to activate this entitlement.
    //
    this.catalog.canRequest(request.requestingUser(), request.entitlements());

    //
    // Request is legit, apply it.
    //
    return provisionAccess(request);
  }

  /**
   * Approve another user's request.
   */
  public final Activation<TEntitlementId> approve(
    UserId approvingUser,
    MpaActivationRequest<TEntitlementId> request
  ) throws AccessException
  {
    Preconditions.checkNotNull(policy, "policy");

    if (approvingUser.equals(request.requestingUser())) {
      throw new IllegalArgumentException(
        "MPA activation requires the caller and beneficiary to be the different");
    }

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
    this.catalog.canRequest(request.requestingUser(), request.entitlements());

    //
    // Check that the approving user is (still) allowed to approve this entitlement.
    //
    this.catalog.canApprove(approvingUser, request.entitlements());

    //
    // Request is legit, apply it.
    //
    return provisionAccess(request);
  }

  /**
   * Apply a request to grant a project role.
   */
  protected abstract Activation provisionAccess(
    ActivationRequest<TEntitlementId> request
  ) throws AccessException;

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  private static class JitRequest<TEntitlementId extends  EntitlementId>
    extends JitActivationRequest<TEntitlementId> {
    public JitRequest(
      UserId requestingUser,
      Set<TEntitlementId> entitlements,
      String justification,
      Instant startTime,
      Instant endTime
    ) {
      super(requestingUser, entitlements, justification, startTime, endTime);
    }
  }

  private static class MpaRequest<TEntitlementId extends EntitlementId>
    extends MpaActivationRequest<TEntitlementId> {
    public MpaRequest(
      UserId requestingUser,
      Set<TEntitlementId> entitlements,
      Set<UserId> reviewers,
      String justification,
      Instant startTime,
      Instant endTime
    ) {
      super(requestingUser, entitlements, reviewers, justification, startTime, endTime);

      if (entitlements.size() != 1) {
        throw new IllegalArgumentException("Only one entitlement can be activated at a time");
      }
    }
  }
}