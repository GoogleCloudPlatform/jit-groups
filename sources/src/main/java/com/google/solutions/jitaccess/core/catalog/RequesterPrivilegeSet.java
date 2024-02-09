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

package com.google.solutions.jitaccess.core.catalog;

import com.google.common.base.Preconditions;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Set of requester privileges
 *
 * @param availableRequesterPrivileges available requester privileges,
 *                                     regardless of status
 * @param activeRequesterPrivilegeIds  IDs of active requester privileges.
 * @param warnings                     encountered warnings, if any.
 */
public record RequesterPrivilegeSet<TPrivilegeId extends PrivilegeId>(
    Set<RequesterPrivilege<TPrivilegeId>> availableRequesterPrivileges,
    Set<TPrivilegeId> activeRequesterPrivilegeIds,
    Set<String> warnings) {
  public RequesterPrivilegeSet {
    Preconditions.checkNotNull(availableRequesterPrivileges, "availableRequesterPrivileges");
    Preconditions.checkNotNull(activeRequesterPrivilegeIds, "activeRequesterPrivilegeIds");
    Preconditions.checkNotNull(warnings, "warnings");

    assert availableRequesterPrivileges.stream().allMatch(e -> e.status() == RequesterPrivilege.Status.AVAILABLE);
  }

  /**
   * @return consolidated set of requester privileges including available and
   *         active ones.
   */
  public SortedSet<RequesterPrivilege<TPrivilegeId>> allRequesterPrivileges() {
    //
    // Return a set containing:
    //
    // 1. Available privileges
    // 2. Active privileges
    //
    // where (1) and (2) don't overlap.
    //
    var availableAndInactive = this.availableRequesterPrivileges
        .stream()
        .filter(privilege -> !this.activeRequesterPrivilegeIds.contains(privilege.id()))
        .collect(Collectors.toCollection(TreeSet::new));

    assert availableAndInactive.stream().noneMatch(e -> this.activeRequesterPrivilegeIds.contains(e.id()));

    var consolidatedSet = new TreeSet<RequesterPrivilege<TPrivilegeId>>(availableAndInactive);
    for (TPrivilegeId activeRequesterPrivilegeId : this.activeRequesterPrivilegeIds) {
      //
      // Find the corresponding privilege to determine
      // whether this is eligible.
      //
      var correspondingRequesterPrivilege = this.availableRequesterPrivileges
          .stream()
          .filter(privilege -> privilege.id().equals(activeRequesterPrivilegeId))
          .findFirst();
      if (correspondingRequesterPrivilege.isPresent()) {
        consolidatedSet.add(new RequesterPrivilege<>(
            activeRequesterPrivilegeId,
            correspondingRequesterPrivilege.get().name(),
            correspondingRequesterPrivilege.get().activationType(),
            RequesterPrivilege.Status.ACTIVE));
      } else {
        //
        // Active, but no longer available for activation.
        //
        consolidatedSet.add(new RequesterPrivilege<>(
            activeRequesterPrivilegeId,
            activeRequesterPrivilegeId.id(),
            new NoActivation(),
            RequesterPrivilege.Status.ACTIVE));
      }
    }

    return consolidatedSet;
  }

  public static <TPrivilegeId extends PrivilegeId> RequesterPrivilegeSet<TPrivilegeId> empty() {
    return new RequesterPrivilegeSet<TPrivilegeId>(new TreeSet<>(), Set.of(), Set.of());
  }
}
