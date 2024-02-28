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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.cel.TimeSpan;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilegeSet;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.NoActivation;
import com.google.solutions.jitaccess.core.catalog.PrivilegeId;
import com.google.solutions.jitaccess.core.catalog.ProjectId;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Repository for ProjectRoleBinding-based privileges.
 */
public abstract class ProjectRoleRepository {

  /**
   * Find projects that a user has standing, requester privileges in.
   */
  abstract SortedSet<ProjectId> findProjectsWithRequesterPrivileges(
      UserEmail user) throws AccessException, IOException;

  /**
   * List requester privileges for the given user.
   */
  abstract RequesterPrivilegeSet<ProjectRoleBinding> findRequesterPrivileges(
      UserEmail user,
      ProjectId projectId,
      Set<ActivationType> typesToInclude,
      EnumSet<RequesterPrivilege.Status> statusesToInclude) throws AccessException, IOException;

  /**
   * List users that hold an eligible reviewer privilege for a role binding.
   */
  abstract Set<UserEmail> findReviewerPrivelegeHolders(
      ProjectRoleBinding roleBinding,
      ActivationType activationType) throws AccessException, IOException;

  static <T extends PrivilegeId> @NotNull RequesterPrivilegeSet<T> buildRequesterPrivilegeSet(
      @NotNull Set<RequesterPrivilege<T>> availableRequesterPrivileges,
      @NotNull Set<ActivatedRequesterPrivilege<T>> validActivations, // TODO(later): rename to active
      @NotNull Set<ActivatedRequesterPrivilege<T>> expiredActivations,
      @NotNull Set<String> warnings) {
    assert availableRequesterPrivileges.stream().allMatch(e -> e.status() == RequesterPrivilege.Status.INACTIVE);
    assert validActivations.stream().noneMatch(id -> expiredActivations.contains(id));
    assert expiredActivations.stream().noneMatch(id -> validActivations.contains(id));

    //
    // Return a set containing:
    //
    // 1. Available privilegges
    // 2. Active privileges
    //
    // where (1) and (2) don't overlap.
    //
    // Expired privileges are ignored.
    //
    var availableAndInactive = availableRequesterPrivileges
        .stream()
        .filter(privilege -> validActivations
            .stream()
            .noneMatch(active -> active.privilegeId().equals(privilege.id())))
        .collect(Collectors.toCollection(TreeSet::new));

    assert availableAndInactive.stream().noneMatch(e -> validActivations.contains(e.id()));

    var current = new TreeSet<RequesterPrivilege<T>>(availableAndInactive);
    for (var validActivation : validActivations) {
      //
      // Find the corresponding privilege to determine
      // whether this is JIT or MPA-eligible.
      //
      var correspondingPrivilege = availableRequesterPrivileges
          .stream()
          .filter(privilege -> privilege.id().equals(validActivation.privilegeId()))
          .findFirst();
      if (correspondingPrivilege.isPresent()) {
        current.add(new RequesterPrivilege<>(
            validActivation.privilegeId(),
            correspondingPrivilege.get().name(),
            correspondingPrivilege.get().activationType(),
            RequesterPrivilege.Status.ACTIVE,
            validActivation.validity()));
      } else {
        //
        // Active, but no longer available for activation.
        //
        current.add(new RequesterPrivilege<>(
            validActivation.privilegeId(),
            validActivation.privilegeId().id(),
            new NoActivation(),
            RequesterPrivilege.Status.ACTIVE,
            validActivation.validity()));
      }
    }

    var expired = new TreeSet<RequesterPrivilege<T>>();
    for (var expiredActivation : expiredActivations) {
      //
      // Find the corresponding privilege to determine
      // whether this is currently (*) JIT or MPA-eligible.
      //
      // (*) it might have changed in the meantime, but that's ok.
      //
      var correspondingPrivilege = availableRequesterPrivileges
          .stream()
          .filter(privilege -> privilege.id().equals(expiredActivation.privilegeId()))
          .findFirst();
      if (correspondingPrivilege.isPresent()) {
        expired.add(new RequesterPrivilege<>(
            expiredActivation.privilegeId(),
            correspondingPrivilege.get().name(),
            correspondingPrivilege.get().activationType(),
            RequesterPrivilege.Status.EXPIRED,
            expiredActivation.validity()));
      } else {
        //
        // Active, but no longer available for activation.
        //
        expired.add(new RequesterPrivilege<>(
            expiredActivation.privilegeId(),
            expiredActivation.privilegeId().id(),
            new NoActivation(),
            RequesterPrivilege.Status.EXPIRED,
            expiredActivation.validity()));
      }
    }

    return new RequesterPrivilegeSet<>(current, expired, warnings);
  }

  record ActivatedRequesterPrivilege<TId>(TId privilegeId, TimeSpan validity) { // TODO: rename to
                                                                                // ActiveRequesterPrivilege, id()
    public ActivatedRequesterPrivilege {
      Preconditions.checkNotNull(privilegeId, "privilegeId");
      Preconditions.checkNotNull(validity, "validity");
    }
  }
}
