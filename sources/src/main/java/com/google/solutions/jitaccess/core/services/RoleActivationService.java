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

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AlreadyExistsException;
import com.google.solutions.jitaccess.core.adapters.IamConditions;
import com.google.solutions.jitaccess.core.adapters.IamCredentialsAdapter;
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
  private final TokenService tokenService;
  private final Options options;

  private void checkJustification(String justification) throws AccessDeniedException{
    if (!this.options.getJustificationPattern().matcher(justification).matches()) {
      throw new AccessDeniedException(
        String.format("Justification does not meet criteria: %s", this.options.getJustificationHint()));
    }
  }

  private void checkUserHasRoleBinding(UserId user, RoleBinding roleBinding) throws AccessException, IOException {
    if (roleBinding.getStatus() == RoleBinding.RoleBindingStatus.ACTIVATED) {
      throw new IllegalArgumentException("The role binding must be in eligible state");
    }

    if (!this.roleDiscoveryService.listEligibleRoleBindings(user).getRoleBindings().contains(roleBinding)) {
      throw new AccessDeniedException(
        String.format("The user %s does not have an eligible binding for %s", user, roleBinding));
    }
  }

  public RoleActivationService(
    RoleDiscoveryService roleDiscoveryService,
    TokenService tokenService,
    ResourceManagerAdapter resourceManagerAdapter,
    Options configuration)
  {
    Preconditions.checkNotNull(roleDiscoveryService, "roleDiscoveryService");
    Preconditions.checkNotNull(tokenService, "tokenService");
    Preconditions.checkNotNull(resourceManagerAdapter, "resourceManagerAdapter");
    Preconditions.checkNotNull(configuration, "configuration");

    this.roleDiscoveryService = roleDiscoveryService;
    this.resourceManagerAdapter = resourceManagerAdapter;
    this.tokenService = tokenService;
    this.options = configuration;
  }

  /**
   * Activate a role binding, either for the calling user (JIT) or
   * for another beneficiary (MPA).
   */
  public OffsetDateTime activateEligibleRoleBinding( // TODO: Return ActivatedRoleBinding
    UserId caller,
    UserId beneficiary,
    RoleBinding role,
    String justification) throws AccessException, AlreadyExistsException, IOException {

    Preconditions.checkNotNull(caller, "userId");
    Preconditions.checkNotNull(role, "role");
    Preconditions.checkNotNull(justification, "justification");

    assert (RoleDiscoveryService.isSupportedResource(role.getFullResourceName()));

    //
    // Check that the justification looks reasonable.
    //
    checkJustification(justification);

    //
    // Double-check that the (calling) user is really allowed to (JIT/MPA-) activate
    // this role. This is to avoid us from being tricked to grant
    // access to a role that they aren't eligible for.
    //
    checkUserHasRoleBinding(caller, role);

    String bindingDescription;
    if (caller.equals(beneficiary)) {
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
      checkUserHasRoleBinding(beneficiary, role);

      //
      // Both the caller and the beneficiary are eligible, so we're good to proceed.
      //
      bindingDescription = String.format("Approved by %s, justification: %s", caller.getEmail(), justification);
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
      .setMembers(List.of("user:" + beneficiary))
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

  public String createMultiPartyApprovalToken( // TODO: Test
    UserId caller,
    UserId approver,
    RoleBinding role,
    String justification) throws AccessException, AlreadyExistsException, IOException {

    Preconditions.checkNotNull(caller, "userId");
    Preconditions.checkNotNull(approver, "approver");
    Preconditions.checkNotNull(role, "role");
    Preconditions.checkNotNull(justification, "justification");

    //
    // Check that the justification looks reasonable.
    //
    checkJustification(justification); // TODO: Test

    //
    // Check that the (calling) user is really allowed to (JIT/MPA-) activate
    // this role.
    //
    // We're not checking if the approver is allowed, we'll do that when applying
    // the approval token.
    //
    checkUserHasRoleBinding(caller, role); // TODO: Test

    //
    // Issue a token that encodes all relevant information.
    //
    return this.tokenService.createToken(
      new JsonWebToken.Payload()
        .setSubject(approver.getEmail())
        .set("benf", caller)
        .set("just", justification)
        .set("rr", role.getRole())
        .set("rn", role.getResourceName())
        .set("rf", role.getFullResourceName())
        .set("rs", role.getStatus().name()));
  }

  public OffsetDateTime applyMultiPartyApprovalToken( // TODO: Test
    UserId caller,
    String token) throws TokenVerifier.VerificationException, AccessException, IOException, AlreadyExistsException {

    Preconditions.checkNotNull(caller, "userId");
    Preconditions.checkNotNull(token, "token");

    //
    // Verify and decode the token. This fails if the token has been
    // tampered with in any way, or has expired.
    //
    var payload = this.tokenService.verifyToken(token, caller); // TODO: Test
    var beneficiary = new UserId(payload.get("benf").toString());
    var justification = payload.get("just").toString();
    var role = new RoleBinding(
      payload.get("rn").toString(),
      payload.get("rf").toString(),
      payload.get("rr").toString(),
      RoleBinding.RoleBindingStatus.valueOf(payload.get("rs").toString()));

    //
    // Activate the role binding on behalf of the beneficiary.
    //
    // The call also checks if the caller is permitted to approve.
    //
    return activateEligibleRoleBinding(
      caller,
      beneficiary,
      role,
      justification);
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
