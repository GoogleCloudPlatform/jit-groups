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

package com.google.solutions.jitaccess.catalog;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.apis.clients.GroupKey;
import com.google.solutions.jitaccess.apis.clients.ResourceNotFoundException;
import com.google.solutions.jitaccess.catalog.auth.*;
import com.google.solutions.jitaccess.catalog.policy.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JIT Group in the context of a specific subject.
 */
public class JitGroupContext {
  private final @NotNull JitGroupPolicy policy;
  private final @NotNull Subject subject;
  private final @NotNull Provisioner provisioner;

  JitGroupContext(
    @NotNull JitGroupPolicy policy,
    @NotNull Subject subject,
    @NotNull Provisioner provisioner
  ) {
    this.provisioner = provisioner;
    this.policy = policy;
    this.subject = subject;
  }

  /**
   * Get group policy.
   */
  public @NotNull JitGroupPolicy policy() {
    return this.policy;
  }

  /**
   * Get ID of the Cloud Identity group that corresponds to this JIT group. The
   * Cloud Identity group may or may not exist yet.
   */
  public @NotNull GroupId cloudIdentityGroupId() {
    return this.provisioner.cloudIdentityGroupId(this.policy().id());
  }

  /**
   * Lookup the Cloud Identity group key for this JIT group.
   *
   * @return GroupKey or empty of the group hasn't been created yet.
   */
  @NotNull Optional<GroupKey> cloudIdentityGroupKey(
    @NotNull GroupId groupId
  ) throws AccessException, IOException {
    return this.provisioner.cloudIdentityGroupKey(this.policy().id());
  }

  /**
   * Prepare a join operation.
   */
  public @NotNull JoinOperation join() {
    //
    // Check if the current subject can self-approve. If so, initiate a
    // join-operation with self-approval.
    //
    // NB. Self-approval requires that the subject also satisfies approval constraints.
    //
    var joinWithSelfApprovalAnalysis = this.policy
      .analyze(this.subject, EnumSet.of(PolicyPermission.JOIN, PolicyPermission.APPROVE_SELF))
      .applyConstraints(Policy.ConstraintClass.JOIN)
      .applyConstraints(Policy.ConstraintClass.APPROVE);
    if (joinWithSelfApprovalAnalysis
      .execute()
      .isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS)) {
      //
      // Continue with self-approval.
      //
      return new JoinOperation(
        false,
        joinWithSelfApprovalAnalysis);
    }

    //
    // The current subject can't self-approve, but they might be allowed
    // to join with approval.
    //
    return new JoinOperation(
      true,
      this.policy
        .analyze(this.subject, EnumSet.of(PolicyPermission.JOIN))
        .applyConstraints(Policy.ConstraintClass.JOIN));
  }

  /**
   * Prepare an approval operation.
   */
  public @NotNull ApprovalOperation approve(
    @NotNull Proposal proposal
  ) {
    Preconditions.checkArgument(
      proposal.group().equals(this.policy.id()),
      "Proposal must match group");
    Preconditions.checkArgument(
      proposal.expiry().isAfter(Instant.now()),
      "Proposal must not be expired");

    //
    // NB. We're not checking if the user is among the recipients
    // of the proposal. That's for 2 reasons:
    //
    // (1) The current user might be part of a group. To verify if the
    //     user is among the recipients, we'd have to expand groups.
    // (2) What matters is if the user is allowed to approve based on
    //     the ACL, not the list of recipients.
    //

    //
    // Recreate the list of inputs that the user provided initially.
    // This requires creating a PolicyAnalysis again, but we're not
    // executing it (because the current user isn't the one that's
    // trying to join).
    //
    var inputFromJoiningUser = this.policy
      .analyze(this.subject, EnumSet.of(PolicyPermission.JOIN))
      .applyConstraints(Policy.ConstraintClass.JOIN)
      .input();
    for (var input : inputFromJoiningUser) {
      if (!proposal.input().containsKey(input.name())) {
        throw new IllegalArgumentException(
          "The proposal is missing a required input for " + input.name());
      }

      input.set(proposal.input().get(input.name()));
    }

    EnumSet<PolicyPermission> requiredPermissions;
    if (proposal.user().equals(this.subject.user())) {
      //
      // The current user is trying to approve or view their own proposal.
      // Approving one's own proposal is typically not allowed (unless they
      // have the APPROVE_SELF permission, in which case creating a proposal
      // was unnecessary), but viewing is usually okay.
      //
      requiredPermissions = EnumSet.of(PolicyPermission.APPROVE_SELF);
    }
    else {
      //
      // The current user is approving someone else's proposal.
      //
      requiredPermissions = EnumSet.of(PolicyPermission.APPROVE_OTHERS);
    }

    //
    // Check if the current subject has the required permission and
    // satisfies the approval constraints (if any).
    //
    return new ApprovalOperation(
      proposal,
      this.policy
        .analyze(this.subject, requiredPermissions)
        .applyConstraints(Policy.ConstraintClass.APPROVE),
      inputFromJoiningUser);
  }

  // -------------------------------------------------------------------------
  // Operations.
  // -------------------------------------------------------------------------

  abstract class AbstractOperation {
    protected final @NotNull PolicyAnalysis analysis;

    protected AbstractOperation(@NotNull PolicyAnalysis analysis) {
      this.analysis = analysis;
    }

    /**
     * Get user that is performing the operation.
     */
    public @NotNull EndUserId user() {
      return JitGroupContext.this.subject.user();
    }

    /**
     * Get group that the user is trying to join.
     */
    public @NotNull JitGroupId group() {
      return JitGroupContext.this.policy.id();
    }

    /**
     * Input provided by the current subject.
     */
    public @NotNull List<Property> input() {
      return this.analysis.input();
    }

    /**
     * Perform a "dry run" to check if the join would succeed
     * given the current input.
     */
    public @NotNull PolicyAnalysis.Result dryRun() {
      //
      // Re-run analysis using the latest inputs.
      //
      return this.analysis.execute();
    }

    /**
     * Get user that's asking to join (in case of a proposal,
     * this user is not the current subject).
     */
    protected abstract @NotNull EndUserId joiningUser();

    /**
     * Input provided by the joining user.
     */
    protected abstract @NotNull List<Property> joiningUserInput();

    /**
     * Execute the join and provision a group membership.
     */
    public @NotNull Principal execute() throws AccessException, IOException {
      //
      // Verify that access is granted and all constraints
      // are satisfied.
      //
      var analysisResult = this.analysis.execute();
      analysisResult.verifyAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT);

      //
      // Determine expiry from the corresponding JOIN-constraint.
      //
      // The expiry  could be fixed or user-provided.
      //
      // NB. We verified all constraints, so if expiry is empty, then
      // there is no expiry constraint.
      //
      var expiry = JitGroupContext.this.policy
        .effectiveConstraints(Policy.ConstraintClass.JOIN)
        .stream()
        .filter(c -> c instanceof ExpiryConstraint)
        .map(c -> (ExpiryConstraint)c)
        .flatMap(c -> c.extractExpiry(this.joiningUserInput()).stream())
        .map(d -> Instant.now().plus(d))
        .findFirst()
        .orElseThrow(() -> new UnsupportedOperationException(
          String.format(
            "The group %s doesn't specify an expiry constraint",
            JitGroupContext.this.policy().id())));

      //
      // Provision group membership.
      //
      var group = JitGroupContext.this;
      group.provisioner.provisionMembership(
        group.policy,
        joiningUser(),
        expiry);

      return new Principal(
        JitGroupContext.this.policy.id(),
        expiry);
    }
  }

  public class JoinOperation extends AbstractOperation {
    private final boolean requiresApproval;

    private JoinOperation(
      boolean requiresApproval,
      @NotNull PolicyAnalysis analysis
    ) {
      super(analysis);
      this.requiresApproval = requiresApproval;
    }

    /**
     * Indicates whether the operation requires approval.
     */
    public boolean requiresApproval() {
      return this.requiresApproval;
    }

    /**
     * Get current user, i.e, the user wants to join.
     */
    public @NotNull EndUserId joiningUser() {
      return JitGroupContext.this.subject.user();
    }

    /**
     * Get input provided by user.
     */
    @Override
    protected @NotNull List<Property> joiningUserInput() {
      //
      // The current user is the joining user, so just return the inputs.
      //
      return this.input();
    }

    /**
     * Propose this operation for someone else to approve.
     */
    public @NotNull Proposal propose(
      @NotNull Instant expiry
    ) throws AccessException {
      if (!this.requiresApproval) {
        throw new IllegalStateException(
          "The join operation does not require approval and cannot be proposed");
      }

      //
      // Verify that access is granted and all constraints
      // are satisfied.
      //
      this.analysis
        .execute()
        .verifyAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT);

      var user = JitGroupContext.this.subject.user();

      //
      // Find all principals that can approve other user's requests,
      // excluding the current user.
      //
      // NB. At this point, we don't know yet whether these principals
      //      would satisfy approval constraints.
      //
      var approvers = JitGroupContext.this.policy.effectiveAccessControlList()
        .allowedPrincipals(PolicyPermission.APPROVE_OTHERS.toMask())
        .stream()
        .filter(p -> !p.equals(user))
        .filter(p -> p instanceof IamPrincipalId)
        .map(p -> (IamPrincipalId)p)
        .collect(Collectors.toSet());

      if (approvers.isEmpty()) {
        throw new AccessDeniedException(
          "There are no principals that could approve the request to join this group");
      }

      return new Proposal() {
        @Override
        public @NotNull EndUserId user() {
          return user;
        }

        @Override
        public @NotNull JitGroupId group() {
          return JoinOperation.this.group();
        }

        @Override
        public @NotNull Set<IamPrincipalId> recipients() {
          return approvers;
        }

        @Override
        public @NotNull Instant expiry() {
          return expiry;
        }

        @Override
        public @NotNull Map<String, String> input() {
          return JoinOperation.this.input()
            .stream()
            .collect(Collectors.toMap(p -> p.name(), p -> p.get()));
        }

        @Override
        public void onCompleted(@NotNull ApprovalOperation op) {
        }
      };
    }

    /**
     * Perform the join.
     */
    @Override
    public @NotNull Principal execute() throws AccessException, IOException {
      if (this.requiresApproval) {
        throw new AccessDeniedException("The join operation requires approval");
      }

      return super.execute();
    }
  }

  public class ApprovalOperation extends AbstractOperation {
    private final @NotNull Proposal proposal;
    private final @NotNull List<Property> inputFromJoiningUser;

    private ApprovalOperation(
      @NotNull Proposal proposal,
      @NotNull PolicyAnalysis analysis,
      @NotNull List<Property> inputFromJoiningUser
    ) {
      super(analysis);
      this.proposal = proposal;
      this.inputFromJoiningUser = inputFromJoiningUser;
    }

    /**
     * Get user that wants to join.
     */
    public @NotNull EndUserId joiningUser() {
      return this.proposal.user();
    }

    /**
     * Get input that the joining user provided before
     * proposing the operation.
     */
    @Override
    public @NotNull List<Property> joiningUserInput() {
      return this.inputFromJoiningUser;
    }

    /**
     * Execute the approval and provision a group membership.
     */
    @Override
    public @NotNull Principal execute() throws AccessException, IOException {
      var principal = super.execute();

      //
      // Complete proposal.
      //
      this.proposal.onCompleted(this);

      return principal;
    }
  }
}
