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

package com.google.solutions.jitaccess.catalog.policy;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.catalog.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.auth.Principal;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Analyze level of access that a subject based on a policy.
 */
public class PolicyAnalysis {
  private final @NotNull Subject subject;
  private final @NotNull JitGroupId groupId;
  private final @NotNull Policy policy;
  private final @NotNull EnumSet<PolicyPermission> requestedAccess;
  private final @NotNull LinkedList<Constraint.Check> constraintChecks = new LinkedList<>();

  PolicyAnalysis(
    @NotNull Policy policy,
    @NotNull Subject subject,
    @NotNull JitGroupId groupId,
    @NotNull EnumSet<PolicyPermission> requestedAccess
  ) {
    Preconditions.checkArgument(
      !requestedAccess.isEmpty(),
      "At least one right must be specified");

    this.subject = subject;
    this.policy = policy;
    this.groupId = groupId;
    this.requestedAccess = requestedAccess;
  }

  private void evaluateConstraintCheck(
    @NotNull Constraint.Check check,
    @NotNull Result resultAccumulator
  ) {
    //
    // Copy request properties.
    //
    var subject = check.addContext("subject");
    subject.set("email", this.subject.user().email);
    subject.set("principals", this.subject.principals()
      .stream()
      .map(p -> p.id().value())
      .toList());

    var group = check.addContext("group");
    group.set("environment", this.groupId.environment());
    group.set("system", this.groupId.system());
    group.set("name", this.groupId.name());

    try {
      if (check.evaluate()) {
        resultAccumulator.satisfiedConstraints.add(check.constraint());
      }
      else {
        resultAccumulator.unsatisfiedConstraints.add(check.constraint());
      }
    } catch (Exception e) {
      resultAccumulator.unsatisfiedConstraints.add(check.constraint());
      resultAccumulator.failedConstraints.put(check.constraint(), e);
    }
  }

  /**
   * Add a set of constraints to be considered.
   */
  public @NotNull PolicyAnalysis applyConstraints(
    @NotNull Policy.ConstraintClass constraintClass
  ) {
    this.constraintChecks.addAll(
      this.policy.effectiveConstraints(constraintClass)
        .stream()
        .map(c -> c.createCheck())
        .toList());

    return this;
  }

  /**
   * @return input required to evaluate constraints.
   */
  public @NotNull List<Property> input() {
    return this.constraintChecks.stream()
      .flatMap(c -> c.input().stream())
      .toList();
  }

  /**
   * Execute analysis.
   *
   * It's safe to execute the analysis multiple times.
   */
  public Result execute() {
    //
    // Evaluate ACLs of this policy and its parents.
    //
    var result = new Result(
      policy.isAllowedByAccessControlList(this.subject, this.requestedAccess));

    for (var constraintCheck : this.constraintChecks) {
      evaluateConstraintCheck(constraintCheck, result);
    }

    //
    // Check if the current user has the principal, i.e.,
    // has joined this group before. We only need to do this
    // once as it doesn't depend on the policy.
    //
    result.activeMembership = this.subject
      .principals()
      .stream()
      .filter(p -> p.isValid() && p.id().equals(this.groupId))
      .findFirst()
      .orElse(null);

    assert result.unsatisfiedConstraints.containsAll(result.failedConstraints
      .keySet());

    return result;
  }

  public class Result {
    private final boolean accessAllowed;
    private @Nullable Principal activeMembership;
    private final @NotNull LinkedList<Constraint> satisfiedConstraints;
    private final @NotNull LinkedList<Constraint> unsatisfiedConstraints;
    private final @NotNull Map<Constraint, Exception> failedConstraints;

    private Result(boolean accessAllowed) {
      this.accessAllowed = accessAllowed;
      this.activeMembership = null;
      this.satisfiedConstraints = new LinkedList<>();
      this.unsatisfiedConstraints = new LinkedList<>();
      this.failedConstraints = new HashMap<>();
    }

    /**
     * Input used to perform check.
     */
    public @NotNull List<Property> input() {
      return PolicyAnalysis.this.input();
    }

    /**
     * @return satisfied constraints.
     */
    public @NotNull Collection<Constraint> satisfiedConstraints() {
      return satisfiedConstraints;
    }

    /**
     * @return unsatisfied and failed constraints.
     */
    public @NotNull Collection<Constraint> unsatisfiedConstraints() {
      return unsatisfiedConstraints;
    }

    /**
     * @return failed constraints and the exception they encountered.
     *
     * Failed constraints are always unsatisfied too.
     */
    public @NotNull Map<Constraint, Exception> failedConstraints() {
      return failedConstraints;
    }

    /**
     * @return information about the subject's existing membership, if any.
     */
    public @NotNull Optional<Principal> activeMembership() {
      return Optional.ofNullable(activeMembership);
    }

    /**
     * Check if access is allowed.
     */
    public boolean isAccessAllowed(@NotNull AccessOptions options) {
      if (options == AccessOptions.IGNORE_CONSTRAINTS)
      {
        //
        // Only consider ACL.
        //
        return accessAllowed;
      }
      else {
        //
        // Check if access is allowed based on the ACL and constraints.
        //
        // NB. We must ignore active memberships, otherwise a member
        // could perpetuate their access even if the ACL no longer grants
        // them access.
        //
        return (this.accessAllowed && this.unsatisfiedConstraints.isEmpty());
      }
    }

    /**
     * Verify that access is allowed.
     *
     * @throws AccessDeniedException if access is denied
     * @throws ConstraintFailedException if one or more constraints failed to execute
     */
    public void verifyAccessAllowed(
      @NotNull AccessOptions options
    ) throws AccessDeniedException, ConstraintFailedException {
      if (isAccessAllowed(options)) {
        return;
      }

      if (!failedConstraints().isEmpty()) {
        throw new ConstraintFailedException(failedConstraints().values());
      }

      throw unsatisfiedConstraints()
        .stream()
        .findFirst()
        .map(c -> (AccessDeniedException) new ConstraintUnsatisfiedException(c))
        .orElse(new AccessDeniedException("Access is denied"));
    }
  }

  public static class ConstraintUnsatisfiedException extends AccessDeniedException {
    ConstraintUnsatisfiedException(Constraint c) {
      super(c.displayName());
    }
  }

  public static class ConstraintFailedException extends AccessException {
    private final @NotNull Collection<Exception> exceptions;

    public ConstraintFailedException(
      @NotNull Collection<Exception> exceptions
    ) {
      super("One ore more constraints failed to execute");
      this.exceptions = exceptions;
    }

    public Collection<Exception> exceptions() {
      return exceptions;
    }
  }

  public enum AccessOptions {
    /** Full access check, includes constraints */
    DEFAULT,

    /** Shallow access check, ignores constraints */
    IGNORE_CONSTRAINTS
  }
}
