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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AlreadyExistsException;
import com.google.solutions.jitaccess.core.adapters.IamTemporaryAccessConditions;
import com.google.solutions.jitaccess.core.adapters.ResourceManagerAdapter;
import com.google.solutions.jitaccess.core.data.*;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

@ApplicationScoped
public class RoleActivationService {
  private final RoleDiscoveryService roleDiscoveryService;
  private final ResourceManagerAdapter resourceManagerAdapter;
  private final TokenService tokenService;
  private final Options options;

  private void checkJustification(String justification) throws AccessDeniedException{
    if (!this.options.justificationPattern.matcher(justification).matches()) {
      throw new AccessDeniedException(
        String.format("Justification does not meet criteria: %s", this.options.justificationHint));
    }
  }

  private static boolean canActivateProjectRole(
    ProjectRole projectRole,
    ActivationType activationType
  ) {
    switch (activationType) {
      case JIT: return projectRole.status == ProjectRole.Status.ELIGIBLE_FOR_JIT;
      case MPA: return projectRole.status == ProjectRole.Status.ELIGIBLE_FOR_MPA;
      default: return false;
    }
  }

  private void checkUserCanActivateProjectRole(
    UserId user,
    RoleBinding roleBinding,
    ActivationType activationType
  ) throws AccessException, IOException {
    if (!this.roleDiscoveryService.listEligibleProjectRoles(
        user,
        ProjectId.fromFullResourceName(roleBinding.fullResourceName))
      .getItems()
      .stream()
      .filter(pr -> pr.roleBinding.equals(roleBinding))
      .filter(pr -> canActivateProjectRole(pr, activationType))
      .findAny()
      .isPresent()) {
      throw new AccessDeniedException(
        String.format(
          "The user %s does not have a suitable project role on %s to activate",
          user,
          roleBinding.fullResourceName));
    }
  }

  public RoleActivationService(
    RoleDiscoveryService roleDiscoveryService,
    TokenService tokenService,
    ResourceManagerAdapter resourceManagerAdapter,
    Options configuration
  ) {
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
   * Activate a role binding for the user themselves.
   */
  public Activation activateProjectRoleForSelf(
    UserId caller,
    RoleBinding roleBinding,
    String justification
  ) throws AccessException, AlreadyExistsException, IOException {
    Preconditions.checkNotNull(caller, "caller");
    Preconditions.checkNotNull(roleBinding, "roleBinding");
    Preconditions.checkNotNull(justification, "justification");
    Preconditions.checkArgument(ProjectId.isProjectFullResourceName(roleBinding.fullResourceName));

    //
    // Check that the justification looks reasonable.
    //
    checkJustification(justification);

    //
    // Verify that the user is really allowed to (JIT-) activate
    // this role. This is to avoid us from being tricked to grant
    // access to a role that they aren't eligible for.
    //
    checkUserCanActivateProjectRole(caller, roleBinding, ActivationType.JIT);

    //
    // Add time-bound IAM binding.
    //
    // Replace existing bindings for same user and role to avoid
    // accumulating junk, and to prevent hitting the binding limit.
    //

    var activationTime = OffsetDateTime.now();
    var expiryTime = activationTime.plus(this.options.activationDuration); // TODO: Use Instant.
    var bindingDescription = String.format(
      "Self-approved, justification: %s",
      justification);

    var binding = new Binding()
      .setMembers(List.of("user:" + caller))
      .setRole(roleBinding.role)
      .setCondition(new com.google.api.services.cloudresourcemanager.v3.model.Expr()
        .setTitle(JitConstraints.ACTIVATION_CONDITION_TITLE)
        .setDescription(bindingDescription)
        .setExpression(IamTemporaryAccessConditions.createExpression(activationTime, expiryTime)));

    this.resourceManagerAdapter.addProjectIamBinding(
      ProjectId.fromFullResourceName(roleBinding.fullResourceName),
      binding,
      EnumSet.of(ResourceManagerAdapter.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS),
      justification);

    return new Activation(
      new ProjectRole(roleBinding, ProjectRole.Status.ACTIVATED),
      expiryTime);
  }

  /**
   * Activate a role binding for a peer (MPA).
   */
  public Activation activateProjectRoleForPeer(
    UserId caller,
    String approvalToken
  ) throws AccessException, AlreadyExistsException, IOException, TokenVerifier.VerificationException {
    Preconditions.checkNotNull(caller, "caller");
    Preconditions.checkNotNull(approvalToken, "approvalToken");

    //
    // Verify and decode the token. This fails if the token has been
    // tampered with in any way, or has expired.
    //
    var payload = new ApprovalTokenPayload(this.tokenService.verifyToken(approvalToken, caller)); // TODO: Test

    if (payload.getBeneficiary().equals(caller)) {
      throw new IllegalArgumentException("MPA activation requires the caller and beneficiary to be the different");
    }

    //
    // Check that the justification looks reasonable.
    //
    checkJustification(payload.getJustification());

    //
    // Verify that the (calling) user is allowed to (MPA-) activate
    // this role. This is to avoid us from being tricked to grant
    // access to a role that they aren't eligible for.
    //
    checkUserCanActivateProjectRole(caller, payload.getRoleBinding(), ActivationType.MPA);

    //
    // Verify that the beneficiary allowed to (MPA-) activate this role.
    //
    checkUserCanActivateProjectRole(payload.getBeneficiary(), payload.getRoleBinding(), ActivationType.MPA);

    //
    // Add time-bound IAM binding for the beneficiary.
    //
    // NB. The start/end time for the binding is derived from the approval token. If multiple
    // reviewers try to approve the same token, the resulting condition (and binding) will
    // be the same. This is important so that we can use the FAIL_IF_BINDING_EXISTS flag.
    //
    // Replace existing bindings for same user and role to avoid
    // accumulating junk, and to prevent hitting the binding limit.
    //

    var activationTime = OffsetDateTime.ofInstant(payload.getRequestTime(), ZoneId.systemDefault()); // TODO: Use Instant
    var expiryTime = activationTime.plus(this.options.activationDuration);
    var bindingDescription = String.format(
      "Approved by %s, justification: %s",
      caller.email,
      payload.getJustification());

    var binding = new Binding()
      .setMembers(List.of("user:" + payload.getBeneficiary().email))
      .setRole(payload.getRoleBinding().role)
      .setCondition(new com.google.api.services.cloudresourcemanager.v3.model.Expr()
        .setTitle(JitConstraints.ACTIVATION_CONDITION_TITLE)
        .setDescription(bindingDescription)
        .setExpression(IamTemporaryAccessConditions.createExpression(activationTime, expiryTime)));

    this.resourceManagerAdapter.addProjectIamBinding(
      ProjectId.fromFullResourceName(payload.getRoleBinding().fullResourceName),
      binding,
      EnumSet.of(
        ResourceManagerAdapter.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS,
        ResourceManagerAdapter.IamBindingOptions.FAIL_IF_BINDING_EXISTS),
      payload.getJustification());

    return new Activation(
      new ProjectRole(payload.getRoleBinding(), ProjectRole.Status.ACTIVATED),
      expiryTime);
  }

//  public String createMultiPartyApprovalToken( // TODO: Test
//    UserId caller,
//    Collection<UserId> reviewers,
//    RoleBinding roleBinding,
//    String justification
//  ) throws AccessException, IOException {
//    Preconditions.checkNotNull(caller, "userId");
//    Preconditions.checkNotNull(reviewers, "reviewers");
//    Preconditions.checkNotNull(roleBinding, "roleBinding");
//    Preconditions.checkNotNull(justification, "justification");
//
//    Preconditions.checkArgument(ProjectId.isProjectFullResourceName(roleBinding.fullResourceName));
//    Preconditions.checkArgument(!reviewers.isEmpty(), "At least one reviewer must be provided");
//
//    //
//    // Check that the justification looks reasonable.
//    //
//    checkJustification(justification); // TODO: Test
//
//    //
//    // Check that the (calling) user is really allowed to (JIT/MPA-) activate
//    // this role.
//    //
//    // We're not checking if the reviewer has the necessary permissions, we
//    // do that when applying the approval token.
//    //
//    checkUserCanActivateProjectRole(caller, roleBinding, ActivationType.MPA); // TODO: Test
//
//    //
//    // Issue a token that encodes all relevant information.
//    //
//    return this.tokenService.createToken(
//      new JsonWebToken.Payload()
//        .set("user", caller)
//        .set("reviewers", reviewers.stream().map(id -> id.email).collect(Collectors.toList()))
//        .set("justification", justification)
//        .set("role", roleBinding.role)
//        .set("project", roleBinding.fullResourceName));
//    // TODO: Add request ID: mpa-RANDOM
//  }
//
//  public Activation applyMultiPartyApprovalToken( // TODO: Test
//    UserId caller,
//    String token
//  ) throws TokenVerifier.VerificationException, AccessException, IOException, AlreadyExistsException {
//    Preconditions.checkNotNull(caller, "userId");
//    Preconditions.checkNotNull(token, "token");
//
//    //
//    // Verify and decode the token. This fails if the token has been
//    // tampered with in any way, or has expired.
//    //
//    var payload = this.tokenService.verifyToken(token, caller); // TODO: Test
//    var beneficiary = new UserId(payload.get("benf").toString());
//    var justification = payload.get("just").toString();
//    var roleBinding = new RoleBinding(
//        payload.get("rsrc").toString(),
//        payload.get("role").toString());
//
//    //
//    // Activate the role binding on behalf of the beneficiary.
//    //
//    // The call also checks if the caller is permitted to approve.
//    //
//    return activateProjectRole(
//      caller,
//      beneficiary,
//      roleBinding,
//      ActivationType.MPA,
//      justification);
//  }

  public Options getOptions() {
    return options;
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public enum ActivationType {
    /** Just-in-time self-approval */
    JIT,

    /** Multi-party approval involving a qualified peer */
    MPA
  }

  /** Represents a successful activation of a project role */
  public static class Activation {
    public final ProjectRole projectRole;

    public final transient OffsetDateTime expiry;

    @JsonProperty("expiry")
    protected String getExpiryString() {
      return this.expiry.format(DateTimeFormatter.ISO_INSTANT);
    }

    @JsonIgnore
    public Activation(ProjectRole projectRole, OffsetDateTime expiry) {
      this.projectRole = projectRole;
      this.expiry = expiry;
    }

    @JsonCreator
    public Activation(ProjectRole projectRole, String expiry) {
      this(projectRole, OffsetDateTime.parse(expiry, DateTimeFormatter.ISO_INSTANT));
    }
  }

  public static class Options {
    public final Duration activationDuration;
    public final String justificationHint;
    public final Pattern justificationPattern;

    public Options(
      String justificationHint,
      Pattern justificationPattern,
      Duration activationDuration) {
      this.activationDuration = activationDuration;
      this.justificationHint = justificationHint;
      this.justificationPattern = justificationPattern;
    }
  }
}
