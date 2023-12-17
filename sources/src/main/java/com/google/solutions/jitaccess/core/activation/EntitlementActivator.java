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
import com.google.solutions.jitaccess.core.AccessException;

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
   * Verify and apply a request to activate an entitlement.
   */
  public Activation<TEntitlementId> activate(
    ActivationRequest<TEntitlementId> request
  ) throws AccessException
  {
    Preconditions.checkNotNull(policy, "policy");

    //
    // Check that the justification is ok.
    //
    policy.checkJustification(request.requestingUser(), request.justification());

    //
    // Check that the user is allowed to request this access.
    //
    this.catalog.verifyAccess(request);

    //
    // Request is legit, apply it.
    //
    return applyRequestCore(request);
  }

  protected abstract Activation applyRequestCore(
    ActivationRequest<TEntitlementId> request
  ) throws AccessException;
}
