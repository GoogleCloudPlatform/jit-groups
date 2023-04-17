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
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AlreadyExistsException;
import com.google.solutions.jitaccess.core.adapters.IamTemporaryAccessConditions;
import com.google.solutions.jitaccess.core.adapters.ResourceManagerAdapter;
import com.google.solutions.jitaccess.core.data.ProjectId;
import com.google.solutions.jitaccess.core.data.ProjectRole;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserId;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for creating, verifying, and approving activation requests.
 *
 * An activation request is a request from a user to "activate" an eligible role
 * on a project.
 *
 * NB. Activations always occur on the level of a project, even if the IAM binding
 * that made the user eligible has been inherited from a folder.
 */
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
    //
    // Check if the given role is among the roles that the
    // user is eligible to JIT-/MPA-activate.
    //
    // NB. It doesn't matter whether the user has already
    // activated the role.
    //
    if (this.roleDiscoveryService.listEligibleProjectRoles(
        user,
        ProjectId.fromFullResourceName(roleBinding.fullResourceName),
        EnumSet.of(
          ProjectRole.Status.ELIGIBLE_FOR_JIT,
          ProjectRole.Status.ELIGIBLE_FOR_MPA))
      .getItems()
      .stream()
      .filter(pr -> pr.roleBinding.equals(roleBinding))
      .filter(pr -> canActivateProjectRole(pr, activationType))
      .findAny()
      .isEmpty()) {
      throw new AccessDeniedException(
        String.format(
          "The user %s is not allowed to activate the role %s",
          user,
          roleBinding.role));
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
   * Activate a role binding on behalf of the calling user. This is only
   * allowed for bindings with a JIT-constraint.
   */
  public Activation activateProjectRoleForSelf(
    UserId caller,
    RoleBinding roleBinding,
    String justification,
    Duration activationTimeout
  ) throws AccessException, AlreadyExistsException, IOException {
    Preconditions.checkNotNull(caller, "caller");
    Preconditions.checkNotNull(roleBinding, "roleBinding");
    Preconditions.checkNotNull(justification, "justification");
    Preconditions.checkArgument(ProjectId.isProjectFullResourceName(roleBinding.fullResourceName));
    Preconditions.checkArgument(activationTimeout.toMinutes() >= Options.MIN_ACTIVATION_TIMEOUT_MINUTES,
      "The activation timeout is too short");
    Preconditions.checkArgument(activationTimeout.toMinutes() <= this.options.maxActivationTimeout.toMinutes(),
      "The requested activation timeout exceeds the maximum permitted timeout");

    //
    // Check that the justification looks reasonable.
    //
    checkJustification(justification);

    //
    // Verify that the user is allowed to (JIT-) activate
    // this role.
    //
    // NB. This check might seem redundant to the checks done during role discovery but is
    // necessary to (a) prevent TOCTOU situations and (b) a situation where a user requests
    // activation without performing discovery first.
    //
    checkUserCanActivateProjectRole(caller, roleBinding, ActivationType.JIT);

    //
    // The caller is eligible.
    //

    //
    // Add time-bound IAM binding.
    //
    // Replace existing bindings for same user and role to avoid
    // accumulating junk, and to prevent hitting the binding limit.
    //

    var activationTime = Instant.now();
    var expiryTime = activationTime.plus(activationTimeout);
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
      activationTime,
      expiryTime);
  }

  /**
   * Activate a role binding for a different user (beneficiary). This is only allowed
   * for bindings with an MPA-constraint.
   */
  public Activation activateProjectRoleForPeer(
    UserId caller,
    ActivationRequest request
  ) throws AccessException, AlreadyExistsException, IOException {
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
    // Verify that both, the calling user and beneficiary are allowed to MPA-activate
    // this role. If they are, then that makes them "peers", and the calling user is
    // qualified to act as a reviewer.
    //

    checkUserCanActivateProjectRole(
      caller,
      request.roleBinding,
      ActivationType.MPA);

    checkUserCanActivateProjectRole(
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
        .setExpression(IamTemporaryAccessConditions.createExpression(
          request.startTime,
          request.endTime)));

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
      request.startTime,
      request.endTime);
  }

  /**
   * Create an activation request that can be passed to reviewers.
   */
  public ActivationRequest createActivationRequestForPeer(
    UserId callerAndBeneficiary,
    Set<UserId> reviewers,
    RoleBinding roleBinding,
    String justification,
    Duration activationTimeout
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(callerAndBeneficiary, "callerAndBeneficiary");
    Preconditions.checkNotNull(reviewers, "reviewers");
    Preconditions.checkNotNull(roleBinding, "roleBinding");
    Preconditions.checkNotNull(justification, "justification");

    Preconditions.checkArgument(ProjectId.isProjectFullResourceName(roleBinding.fullResourceName));
    Preconditions.checkArgument(reviewers != null && reviewers.size() >= this.options.minNumberOfReviewersPerActivationRequest,
      "At least " + this.options.minNumberOfReviewersPerActivationRequest + " reviewers must be specified");
    Preconditions.checkArgument(reviewers.size() <= this.options.maxNumberOfReviewersPerActivationRequest,
      "The number of reviewers must not exceed " + this.options.maxNumberOfReviewersPerActivationRequest);
    Preconditions.checkArgument(!reviewers.contains(callerAndBeneficiary), "The beneficiary cannot be a reviewer");
    Preconditions.checkArgument(activationTimeout.toMinutes() >= Options.MIN_ACTIVATION_TIMEOUT_MINUTES,
      "The activation timeout is too short");
    Preconditions.checkArgument(activationTimeout.toMinutes() <= this.options.maxActivationTimeout.toMinutes(),
      "The requested activation timeout exceeds the maximum permitted timeout");

    //
    // Check that the justification looks reasonable.
    //
    checkJustification(justification);

    //
    // Check that the calling user (who is the beneficiary) is  allowed to MPA-activate
    // this role.
    //
    // NB. We're not checking if the reviewers have the necessary permissions. It's sufficient
    // to do that on activation.
    //
    checkUserCanActivateProjectRole(callerAndBeneficiary, roleBinding, ActivationType.MPA);

    //
    // Issue an activation request.
    //
    var startTime = Instant.now();
    var endTime = startTime.plus(activationTimeout);

    return new ActivationRequest(
      ActivationId.newId(ActivationType.MPA),
      callerAndBeneficiary,
      reviewers,
      roleBinding,
      justification,
      startTime,
      endTime);
  }

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

    protected ActivationId(String id) {
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
  public static class Activation {
    public final ActivationId id;
    public final ProjectRole projectRole;
    public final Instant startTime;
    public final Instant endTime;

    private Activation(
      ActivationId id,
      ProjectRole projectRole,
      Instant startTime,
      Instant endTime
    ) {
      Preconditions.checkNotNull(startTime);
      Preconditions.checkNotNull(endTime);

      assert startTime.isBefore(endTime);

      this.id = id;
      this.projectRole = projectRole;
      this.startTime = startTime;
      this.endTime = endTime;
    }

    public static Activation createForTestingOnly(
      ActivationId id,
      ProjectRole projectRole,
      Instant startTime,
      Instant endTime
    ) {
      return new Activation(id, projectRole, startTime, endTime);
    }
  }

  /** Represents a pre-validated activation request */
  public static class ActivationRequest {
    public final ActivationId id;
    public final UserId beneficiary;
    public final Set<UserId> reviewers;
    public final RoleBinding roleBinding;
    public final String justification;
    public final Instant startTime;
    public final Instant endTime;

    private ActivationRequest(
      ActivationId id,
      UserId beneficiary,
      Set<UserId> reviewers,
      RoleBinding roleBinding,
      String justification,
      Instant startTime,
      Instant endTime
    ) {
      Preconditions.checkNotNull(id);
      Preconditions.checkNotNull(beneficiary);
      Preconditions.checkNotNull(reviewers);
      Preconditions.checkNotNull(roleBinding);
      Preconditions.checkNotNull(justification);
      Preconditions.checkNotNull(startTime);
      Preconditions.checkNotNull(endTime);

      assert startTime.isBefore(endTime);

      this.id = id;
      this.beneficiary = beneficiary;
      this.reviewers = reviewers;
      this.roleBinding = roleBinding;
      this.justification = justification;
      this.startTime = startTime;
      this.endTime = endTime;
    }

    public static ActivationRequest createForTestingOnly(
      ActivationId id,
      UserId beneficiary,
      Set<UserId> reviewers,
      RoleBinding roleBinding,
      String justification,
      Instant startTime,
      Instant endTime
      ) {
      return new ActivationRequest(id, beneficiary, reviewers, roleBinding, justification, startTime, endTime);
    }

    protected static ActivationRequest fromJsonWebTokenPayload(JsonWebToken.Payload payload) {
      //noinspection unchecked
      return new RoleActivationService.ActivationRequest(
        new RoleActivationService.ActivationId(payload.getJwtId()),
        new UserId(payload.get("beneficiary").toString()),
        ((List<String>)payload.get("reviewers"))
          .stream()
          .map(email -> new UserId(email))
          .collect(Collectors.toSet()),
        new RoleBinding(
          payload.get("resource").toString(),
          payload.get("role").toString()),
        payload.get("justification").toString(),
        Instant.ofEpochSecond(((Number)payload.get("start")).longValue()),
        Instant.ofEpochSecond(((Number)payload.get("end")).longValue()));
    }

    protected JsonWebToken.Payload toJsonWebTokenPayload() {
      return new JsonWebToken.Payload()
        .setJwtId(this.id.toString())
        .set("beneficiary", this.beneficiary.email)
        .set("reviewers", this.reviewers.stream().map(id -> id.email).collect(Collectors.toList()))
        .set("resource", this.roleBinding.fullResourceName)
        .set("role", this.roleBinding.role)
        .set("justification", this.justification)
        .set("start", this.startTime.getEpochSecond())
        .set("end", this.endTime.getEpochSecond());
    }
  }

  public static class Options {
    public static final int MIN_ACTIVATION_TIMEOUT_MINUTES = 5;

    public final Duration maxActivationTimeout;
    public final String justificationHint;
    public final Pattern justificationPattern;
    public final int minNumberOfReviewersPerActivationRequest;
    public final int maxNumberOfReviewersPerActivationRequest;

    public Options(
      String justificationHint,
      Pattern justificationPattern,
      Duration maxActivationTimeout,
      int minNumberOfReviewersPerActivationRequest,
      int maxNumberOfReviewersPerActivationRequest)
    {
      Preconditions.checkArgument(
        maxActivationTimeout.toMinutes() >= MIN_ACTIVATION_TIMEOUT_MINUTES,
        "Activation timeout must be at least 5 minutes");
      Preconditions.checkArgument(
        minNumberOfReviewersPerActivationRequest > 0,
        "The minimum number of reviewers cannot be 0");
      Preconditions.checkArgument(
        minNumberOfReviewersPerActivationRequest <= maxNumberOfReviewersPerActivationRequest,
        "The minimum number of reviewers must not exceed the maximum");

      this.maxActivationTimeout = maxActivationTimeout;
      this.justificationHint = justificationHint;
      this.justificationPattern = justificationPattern;
      this.minNumberOfReviewersPerActivationRequest = minNumberOfReviewersPerActivationRequest;
      this.maxNumberOfReviewersPerActivationRequest = maxNumberOfReviewersPerActivationRequest;
    }
  }
}
