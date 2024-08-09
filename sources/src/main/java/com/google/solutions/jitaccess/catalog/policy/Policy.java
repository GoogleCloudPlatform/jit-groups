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

import com.google.solutions.jitaccess.catalog.auth.Subject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;

public interface Policy extends PolicyHeader {
  /**
   * Name of policy.
   */
  @NotNull String name();

  /**
   * Display name of policy.
   */
  @NotNull String displayName();

  /**
   * Description of policy.
   */
  @NotNull String description();

  /**
   * Parent policy, if any.
   */
  @NotNull Optional<Policy> parent();

  /**
   * ACL, if any. A policy without an ACL grants access to all principals.
   */
  @NotNull Optional<AccessControlList> accessControlList();

  /**
   * Constraints, if any.
   */
  @NotNull Collection<Constraint> constraints(@NotNull ConstraintClass action);

  enum ConstraintClass {
    JOIN,
    APPROVE
  }

  /**
   * Effective ACL based on the policy's ancestry.
   */
  default AccessControlList effectiveAccessControlList() {
    //
    // Find all ACLs in ancestry and order them so that the
    // root policy's ACL comes first, and this policy's ACL
    // is last.
    //
    var aclAncestry = new LinkedList<AccessControlList>();
    for (var policy = Optional.of(this);
         policy.isPresent();
         policy = policy.get().parent()) {
      var acl = policy.get().accessControlList();
      acl.ifPresent(aclAncestry::addFirst);
    }

    //
    // Create a consolidated ACL that contains all entries.
    //
    return new AccessControlList(
      aclAncestry
        .stream()
        .flatMap(acl -> acl.entries().stream())
        .toList());
  }

  /**
   * Effective constraints based on the policy's ancestry.
   */
  default @NotNull Collection<Constraint> effectiveConstraints(
    @NotNull Policy.ConstraintClass constraintClass
  ) {
    var allConstraints = new HashMap<String, Constraint>();
    for (var policy = Optional.of(this); policy.isPresent(); policy = policy.get().parent()) {
      for (var constraint : policy.get().constraints(constraintClass)) {
        if (!allConstraints.containsKey(constraint.name())) {
          //
          // If a parent and child policy contain a constraint with the same name,
          // we use the child's constraint.
          //
          allConstraints.put(constraint.name(), constraint);
        }
      }
    }

    return allConstraints.values();
  }

  /**
   * Check access based on this policy's ACL, and it's ancestry's ACLs.
   */
  default boolean isAllowedByAccessControlList(
    @NotNull Subject subject,
    @NotNull EnumSet<PolicyPermission> requiredRights
  ) {
    return effectiveAccessControlList().isAllowed(
      subject,
      PolicyPermission.toMask(requiredRights));
  }

  /**
   * Metadata for an environment policy.
   *
   * @param source source the policy has been loaded from, can be a file name or full path
   * @param lastModified date the policy was last changed
   * @param version version number identifying the policy revision
   */
  record Metadata(
    @NotNull String source,
    @NotNull Instant lastModified,
    @Nullable String version
  ) {
    public Metadata(@NotNull String source, @NotNull Instant lastModified) {
      this(source, lastModified, null);
    }
  }
}
