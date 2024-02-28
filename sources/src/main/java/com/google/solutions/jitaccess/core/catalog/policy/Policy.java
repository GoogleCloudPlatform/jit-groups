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
import com.google.solutions.jitaccess.core.catalog.jitrole.JitRole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * A parsed and validated policy.
 *
 * @param id unique ID of the policy
 * @param name name or description for the policy
 * @param roles list of JIT roles managed by this policy
 */
public record Policy(
  @NotNull String id,
  @NotNull String name,
  @NotNull List<Role> roles
) {

  /**
   * A JIT role that an eligible user can activate if they
   * meet the specified constraints.
   *
   * @param id unique ID of the entitlement
   * @param name name or description for the entitlement
   * @param acl list of principals that can request to activate
   * @param constraints
   */
  public record Role(
    @NotNull JitRole id,
    @NotNull String name,
    @NotNull AccessControlList acl,
    @NotNull Constraints constraints
  ) {
  }

  public class RoleAccessRights {
    private RoleAccessRights() {
    }

    /**
     * Request to activate this role.
     */
    public static final int REQUEST = 1;

    /**
     * Approve activation requests from other users.
     */
    public static final int APPROVE_OTHERS = 2;

    /**
     * Self-approve activation requests.
     */
    public static final int APPROVE_SELF = 4;

    /**
     * "JIT" access: Request and self-approve.
     */
    public static final int REQUEST_WITH_SELF_APPROVAL = REQUEST | APPROVE_SELF;

    /**
     * "Peer approval" access: Require approval from others and allow to
     * approve others' requests.
     */
    public static final int REQUEST_WITH_PEER_APPROVAL = REQUEST | APPROVE_OTHERS;

    public static int parse(@NotNull String s) {
      var elements = s.split(",");
      if (elements.length == 1) {
        switch (s.trim().toUpperCase()) {
          case "REQUEST":
            return REQUEST;
          case "APPROVE_OTHERS":
            return APPROVE_OTHERS;
          case "APPROVE_SELF":
            return APPROVE_SELF;
          case "REQUEST_WITH_SELF_APPROVAL":
            return REQUEST_WITH_SELF_APPROVAL;
          case "REQUEST_WITH_PEER_APPROVAL":
            return REQUEST_WITH_PEER_APPROVAL;
          default:
            throw new IllegalArgumentException(
              "Unrecognized access right: " + s);
        }
      }
      else {
        return Arrays.stream(elements)
          .map(RoleAccessRights::parse)
          .reduce(0, (lhs, rhs) -> lhs | rhs);
      }
    }
  }

  /**
   * Constraints for activating this role.
   *
   * @param defaultActivationDuration default time
   * @param minActivationDuration minimum time for which this entitlement can be requested
   * @param maxActivationDuration maximum time for which this entitlement can be requested
   * @param approvalConstraints
   */
  public record Constraints (
    @Nullable Duration defaultActivationDuration,
    @Nullable Duration minActivationDuration,
    @Nullable Duration maxActivationDuration,
    @Nullable ApprovalConstraints approvalConstraints
  ) {}

  /**
   * Constraints for approval workflows.
   *
   * @param minimumNumberOfPeersToNotify minimum number of peers to notify
   * @param maximumNumberOfPeersToNotify maximum number of peers to notify
   */
  public record ApprovalConstraints (
    @Nullable Integer minimumNumberOfPeersToNotify,
    @Nullable Integer maximumNumberOfPeersToNotify
  ) {
    public ActivationType activationType() {
      return ActivationType.MPA;
    }
  }
}
