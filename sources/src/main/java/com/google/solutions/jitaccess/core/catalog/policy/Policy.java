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

package com.google.solutions.jitaccess.core.catalog.policy;

import com.google.solutions.jitaccess.core.auth.AccessControlList;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * A parsed and validated policy.
 * @param id unique ID of the policy
 * @param name name or description for the policy
 * @param entitlements list of entitlements managed by this policy
 */
public record Policy(
  @NotNull String id,
  @NotNull String name,
  @NotNull List<Entitlement> entitlements
) {

  /**
   * An entitlement that an eligible user can activate if they
   * meet the specified requirements.
   *
   * @param id unique ID of the entitlement
   * @param name name or description for the entitlement
   * @param expiry time after which an activation expires
   * @param acl list of principals that can request to activate
   * @param approvalRequirement approvals required to activate this requirement
   */
  public record Entitlement(
    @NotNull String id,
    @NotNull String name,
    @NotNull Duration expiry, // TODO: min, max
    @NotNull AccessControlList acl,
    @NotNull ApprovalRequirement approvalRequirement
  ) {
  }

  /**
   * Base interface for approval requirements.
   */
  public interface ApprovalRequirement {
    /**
     * Type of activation required.
     */
    ActivationType activationType();
  }

  /**
   * Indicates that eligible principals can self-approve.
   */
  public record SelfApprovalRequirement() implements ApprovalRequirement {
    public ActivationType activationType() {
      return ActivationType.JIT;
    }
  }

  /**
   * Indicates that eligible principals need approval
   * from a peer.
   *
   * @param minimumNumberOfPeersToNotify minimum number of peers to notify
   * @param maximumNumberOfPeersToNotify maximum number of peers to notify
   */
  public record PeerApprovalRequirement(
    Integer minimumNumberOfPeersToNotify,
    Integer maximumNumberOfPeersToNotify
  ) implements ApprovalRequirement {
    public ActivationType activationType() {
      return ActivationType.MPA;
    }
  }
}
