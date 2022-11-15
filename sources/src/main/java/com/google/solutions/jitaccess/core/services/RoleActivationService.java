//
// Copyright 2022 Google LLC
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

package com.google.solutions.jitaccess.core.services;

import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AlreadyExistsException;
import com.google.solutions.jitaccess.core.adapters.IamConditions;
import com.google.solutions.jitaccess.core.adapters.ResourceManagerAdapter;
import com.google.solutions.jitaccess.core.adapters.UserId;

import javax.enterprise.context.RequestScoped;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

@RequestScoped
public class RoleActivationService {

  private final RoleDiscoveryService roleDiscoveryService;
  private final ResourceManagerAdapter resourceManagerAdapter;
  private final Options options;

  public RoleActivationService(
    RoleDiscoveryService roleDiscoveryService,
    ResourceManagerAdapter resourceManagerAdapter,
    Options configuration)
  {
    Preconditions.checkNotNull(roleDiscoveryService, "roleDiscoveryService");
    Preconditions.checkNotNull(resourceManagerAdapter, "resourceManagerAdapter");
    Preconditions.checkNotNull(configuration, "configuration");

    this.roleDiscoveryService = roleDiscoveryService;
    this.resourceManagerAdapter = resourceManagerAdapter;
    this.options = configuration;
  }


  /**
   * Activate a role binding, either for the calling user (JIT) or
   * for another beneficiary (MPA).
   */
  public OffsetDateTime activateEligibleRoleBinding(
    UserId callerUserId,
    UserId beneficiaryUserId,
    RoleBinding role,
    String justification) throws AccessException, AlreadyExistsException, IOException {

    Preconditions.checkNotNull(callerUserId, "userId");
    Preconditions.checkNotNull(role, "role");
    Preconditions.checkNotNull(justification, "justification");

    assert (RoleDiscoveryService.isSupportedResource(role.getFullResourceName()));

    //
    // Check that the justification looks reasonable.
    //
    if (!this.options.getJustificationPattern().matcher(justification).matches()) {
      throw new AccessDeniedException(
        String.format("Justification does not meet criteria: %s", this.options.getJustificationHint()));
    }

    //
    // Double-check that the (calling) user is really allowed to (JIT/MPA-) activate
    // this role. This is to avoid us from being tricked to grant
    // access to a role that they aren't eligible for.
    //
    var eligibleRoles = this.roleDiscoveryService.listEligibleRoleBindings(callerUserId);
    if (!eligibleRoles.getRoleBindings().contains(role)) {
      throw new AccessDeniedException(
        String.format("Your user %s is not eligible to activate this role", callerUserId));
    }

    String bindingDescription;
    if (callerUserId.equals(beneficiaryUserId)) {
      //
      // JIT access: The caller is trying to activate a role for themselves.
      //
      if (role.getStatus() != RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_JIT_APPROVAL) {
        throw new IllegalArgumentException("The role does not permit self-approval");
      }

      //
      // We already checked that the caller is eligible, so we're good to proceed.
      //
      bindingDescription = String.format("Self-approved, justification: %s", justification);
    }
    else {
      //
      // Multi-party approval: The caller is trying to activate a role for somebody else.
      //
      if (role.getStatus() != RoleBinding.RoleBindingStatus.ELIGIBLE_FOR_MPA_APPROVAL) {
        throw new IllegalArgumentException("The role does not permit multi party-approval");
      }

      //
      // We already checked that the caller is eligible, but we still need to check that the beneficiary
      // is eligible too.
      //
      if (!this.roleDiscoveryService.listEligibleRoleBindings(beneficiaryUserId).getRoleBindings().contains(role)) {
        throw new AccessDeniedException(
          String.format("Your user %s is not eligible to have this role activated for them", callerUserId));
      }

      //
      // Both the caller and the beneficiary are eligible, so we're good to proceed.
      //
      bindingDescription = String.format("Approved by %s, justification: %s", callerUserId.getEmail(), justification);
    }

    //
    // Add time-bound IAM binding for the beneficiary.
    //
    // Replace existing bindings for same user and role to avoid
    // accumulating junk, and to prevent hitting the binding limit.
    //
    var elevationStartTime = OffsetDateTime.now();
    var elevationEndTime = elevationStartTime.plus(this.options.getActivationDuration());

    var binding = new Binding()
      .setMembers(List.of("user:" + beneficiaryUserId))
      .setRole(role.getRole())
      .setCondition(new com.google.api.services.cloudresourcemanager.v3.model.Expr()
        .setTitle(JitConstraints.ELEVATION_CONDITION_TITLE)
        .setDescription(bindingDescription)
        .setExpression(IamConditions.createTemporaryConditionClause(elevationStartTime, elevationEndTime)));

    this.resourceManagerAdapter.addIamBinding(
      role.getResourceName(),
      binding,
      EnumSet.of(ResourceManagerAdapter.IamBindingOptions.REPLACE_BINDINGS_FOR_SAME_PRINCIPAL_AND_ROLE),
      justification);

    return elevationEndTime;
  }

  public Options getOptions() {
    return options;
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public static class Options {
    private final Duration activationDuration;
    private final String justificationHint;
    private final Pattern justificationPattern;

    public Options(
      String justificationHint,
      Pattern justificationPattern,
      Duration activationDuration) {
      this.activationDuration = activationDuration;
      this.justificationHint = justificationHint;
      this.justificationPattern = justificationPattern;
    }

    /**
     * Duration for an elevation.
     */
    public Duration getActivationDuration() {
      return this.activationDuration;
    }

    /**
     * Hint for justification pattern.
     */
    public String getJustificationHint() {
      return this.justificationHint;
    }

    /**
     * Pattern to validate justifications.
     */
    public Pattern getJustificationPattern() {
      return this.justificationPattern;
    }
  }
}
