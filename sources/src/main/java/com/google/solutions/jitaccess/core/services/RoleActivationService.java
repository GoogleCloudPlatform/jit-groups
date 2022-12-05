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
import io.vertx.ext.auth.User;
import org.checkerframework.checker.units.qual.A;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@ApplicationScoped
public class RoleActivationService {
  private final RoleDiscoveryService roleDiscoveryService;
  private final ResourceManagerAdapter resourceManagerAdapter;
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
    ResourceManagerAdapter resourceManagerAdapter,
    Options configuration
  ) {
    Preconditions.checkNotNull(roleDiscoveryService, "roleDiscoveryService");
    Preconditions.checkNotNull(resourceManagerAdapter, "resourceManagerAdapter");
    Preconditions.checkNotNull(configuration, "configuration");

    this.roleDiscoveryService = roleDiscoveryService;
    this.resourceManagerAdapter = resourceManagerAdapter;
    this.options = configuration;
  }

  /**
   * Activate a role binding for the user themselves. This is only
   * allowed for bindings with a JIT-constraint.
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

    var activationTime = Instant.now();
    var expiryTime = activationTime.plus(this.options.activationDuration);
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
      ActivationId.newId(ActivationType.JIT),
      new ProjectRole(roleBinding, ProjectRole.Status.ACTIVATED),
      expiryTime.atOffset(ZoneOffset.UTC)); // TODO: Return instant instead?
  }

  /**
   * Activate a role binding for a peer. This is only possible for bindings with
   * an MPA-constraint.
   */
  public Activation activateProjectRoleForPeer( // TODO: Rename to approveActivationRequest
    UserId caller,
    ActivationRequest request
  ) throws AccessException, AlreadyExistsException, IOException, TokenVerifier.VerificationException {
    Preconditions.checkNotNull(caller, "caller");
    Preconditions.checkNotNull(request, "request");

    if (request.beneficiary.equals(caller)) {
      throw new IllegalArgumentException(
        "MPA activation requires the caller and beneficiary to be the different");
    }

    if (!request.reviewers.contains(caller)) {
      throw new AccessDeniedException(
        String.format("The token does not permit approval by %s", caller));
    }

    //
    // Check that the justification looks reasonable.
    //
    checkJustification(request.justification);

    //
    // Verify that the calling user (reviewer) is allowed to (MPA-) activate
    // this role. This is to avoid us from being tricked to grant
    // access to a role that they aren't eligible for.
    //
    checkUserCanActivateProjectRole(
      caller,
      request.roleBinding,
      ActivationType.MPA);

    //
    // Verify that the beneficiary allowed to (MPA-) activate this role.
    //
    // NB. This check is somewhat redundant to the check we're performing
    // before issuing an approval token. But it's possible that the user
    // was revoked access since the token was issued, so it's better to
    // check again.
    //
    checkUserCanActivateProjectRole(// TODO: Test
      request.beneficiary,
      request.roleBinding,
      ActivationType.MPA);

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

    var activationTime = request.creationTime;
    var expiryTime = activationTime.plus(this.options.activationDuration);
    var bindingDescription = String.format(
      "Approved by %s, justification: %s",
      caller.email,
      request.justification);

    var binding = new Binding()
      .setMembers(List.of("user:" + request.beneficiary.email))
      .setRole(request.roleBinding.role)
      .setCondition(new com.google.api.services.cloudresourcemanager.v3.model.Expr()
        .setTitle(JitConstraints.ACTIVATION_CONDITION_TITLE)
        .setDescription(bindingDescription)
        .setExpression(IamTemporaryAccessConditions.createExpression(activationTime, expiryTime)));

    this.resourceManagerAdapter.addProjectIamBinding(
      ProjectId.fromFullResourceName(request.roleBinding.fullResourceName),
      binding,
      EnumSet.of(
        ResourceManagerAdapter.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS,
        ResourceManagerAdapter.IamBindingOptions.FAIL_IF_BINDING_EXISTS),
      request.justification);

    return new Activation(
      request.id,
      new ProjectRole(request.roleBinding, ProjectRole.Status.ACTIVATED),
      expiryTime.atOffset(ZoneOffset.UTC)); // TODO: Return instant instead?
  }

//  public ActivationTokenService.Payload verifyActivationToken( // TODO: Test
//    String unverifiedActivationToken
//  ) throws TokenVerifier.VerificationException {
//    Preconditions.checkNotNull(unverifiedActivationToken, "unverifiedActivationToken");
//
//    //
//    // Verify and decode the token. This fails if the token has been
//    // tampered with in any way, or has expired.
//    //
//    return this.activationTokenService.verifyToken(unverifiedActivationToken);
//  }

//  public String requestPeerToActivateProjectRole( // TODO: Test
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

  /** Unique ID for an activation */
  public static class ActivationId {
    private static final SecureRandom random = new SecureRandom();

    private final String id;

    private ActivationId(String id) {
      Preconditions.checkNotNull(id);
      this.id = id;
    }

    public static ActivationId newId(ActivationType type) {
      var id = new byte[12];
      random.nextBytes(id);

      return new ActivationId(type.name().toLowerCase() + "-" + Base64.getEncoder().encodeToString(id));
    }

    @Override
    public String toString() {
      return this.id;
    }
  }

  /** Represents a successful activation of a project role */
  public static class Activation { // TODO: Avoid serialization
    public final ProjectRole projectRole;
    public final transient ActivationId id;
    public final transient OffsetDateTime expiry;

    @JsonProperty("expiry")
    protected String getExpiryString() {
      return this.expiry.format(DateTimeFormatter.ISO_INSTANT);
    }

    @JsonProperty("id")
    protected String getId() {
      return this.id.toString();
    }

    @JsonIgnore
    private Activation(ActivationId id, ProjectRole projectRole, OffsetDateTime expiry) {
      this.id = id;
      this.projectRole = projectRole;
      this.expiry = expiry;
    }

    @JsonCreator
    public Activation(String id, ProjectRole projectRole, String expiry) { // TODO: Remove ctor?
      this(new ActivationId(id), projectRole, OffsetDateTime.parse(expiry, DateTimeFormatter.ISO_INSTANT));
    }

    public static Activation createForTestingOnly(ActivationId id, ProjectRole projectRole, OffsetDateTime expiry) {
      return new Activation(id, projectRole, expiry);
    }
  }

  public static class ActivationRequest {
    public final ActivationId id;
    public final UserId beneficiary;
    public final Set<UserId> reviewers;
    public final RoleBinding roleBinding;
    public final String justification;
    public final Instant creationTime;
    public final Duration validity;

    private ActivationRequest(
      ActivationId id,
      UserId beneficiary,
      Set<UserId> reviewers,
      RoleBinding roleBinding,
      String justification,
      Instant creationTime,
      Duration validity
    ) {
      Preconditions.checkNotNull(id);
      Preconditions.checkNotNull(beneficiary);
      Preconditions.checkNotNull(reviewers);
      Preconditions.checkNotNull(roleBinding);
      Preconditions.checkNotNull(justification);
      Preconditions.checkNotNull(creationTime);
      Preconditions.checkNotNull(validity);

      this.id = id;
      this.beneficiary = beneficiary;
      this.reviewers = reviewers;
      this.roleBinding = roleBinding;
      this.justification = justification;
      this.creationTime = creationTime;
      this.validity = validity;
    }

    public static ActivationRequest createForTestingOnly(
      ActivationId id,
      UserId beneficiary,
      Set<UserId> reviewers,
      RoleBinding roleBinding,
      String justification,
      Instant creationTime,
      Duration validity
      ) {
      return new ActivationRequest(id, beneficiary, reviewers, roleBinding, justification, creationTime, validity);
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
