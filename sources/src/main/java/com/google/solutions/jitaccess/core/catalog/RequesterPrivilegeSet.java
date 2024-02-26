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
import com.google.solutions.jitaccess.cel.TimeSpan;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Set of requester privileges
 *
 * @param availableRequesterPrivileges available and active requester privileges
 * @param expiredRequesterPrivileges   previously active requester privileges
 * @param warnings                     encountered warnings, if any.
 */
public record RequesterPrivilegeSet<TPrivilegeId extends PrivilegeId>(
    SortedSet<RequesterPrivilege<TPrivilegeId>> availableRequesterPrivileges,
    SortedSet<RequesterPrivilege<TPrivilegeId>> expiredRequesterPrivileges,
    Set<String> warnings) {

  public RequesterPrivilegeSet

  {
    Preconditions.checkNotNull(availableRequesterPrivileges, "availableRequesterPrivileges");
    Preconditions.checkNotNull(warnings, "warnings");

    assert availableRequesterPrivileges.stream().allMatch(e -> e.status() != RequesterPrivilege.Status.EXPIRED);
    assert expiredRequesterPrivileges.stream().allMatch(e -> e.status() == RequesterPrivilege.Status.EXPIRED);
  }

  public static <T extends PrivilegeId> RequesterPrivilegeSet<T> build(
      Set<RequesterPrivilege<T>> availableRequesterPrivileges,
      Set<ActivatedRequesterPrivilege<T>> validActivations,
      Set<ActivatedRequesterPrivilege<T>> expiredActivations,
      Set<String> warnings) {
    assert availableRequesterPrivileges.stream().allMatch(e -> e.status() == RequesterPrivilege.Status.INACTIVE);
    assert validActivations.stream().noneMatch(id -> expiredActivations.contains(id));
    assert expiredActivations.stream().noneMatch(id -> validActivations.contains(id));

    //
    // Return a set containing:
    //
    // 1. Available privileges
    // 2. Active privileges
    //
    // where (1) and (2) don't overlap.
    //
    // Expired privileges are ignored.
    //
    var availableAndInactive = availableRequesterPrivileges
        .stream()
        .filter(privilege -> !validActivations
            .stream()
            .anyMatch(active -> active.privilegeId().equals(privilege.id())))
        .collect(Collectors.toCollection(TreeSet::new));

    assert availableAndInactive.stream().noneMatch(
        p -> validActivations.stream().map(active -> active.privilegeId.id()).collect(Collectors.toList())
            .contains(p.id().id()));

    var current = new TreeSet<RequesterPrivilege<T>>(availableAndInactive);
    for (var validActivation : validActivations) {
      //
      // Find the corresponding privilege to determine
      // whether this is eligible.
      //
      var correspondingRequesterPrivilege = availableRequesterPrivileges
          .stream()
          .filter(privilege -> privilege.id().id().equals(validActivation.privilegeId().id()))
          .findFirst();
      if (correspondingRequesterPrivilege.isPresent()) {
        current.add(new RequesterPrivilege<>(
            validActivation.privilegeId(),
            correspondingRequesterPrivilege.get().name(),
            correspondingRequesterPrivilege.get().activationType(),
            RequesterPrivilege.Status.ACTIVE,
            validActivation.validity));
      } else {
        //
        // Active, but no longer available for activation.
        //
        current.add(new RequesterPrivilege<>(
            validActivation.privilegeId(),
            validActivation.privilegeId().id(),
            new NoActivation(),
            RequesterPrivilege.Status.ACTIVE,
            validActivation.validity));
      }
    }

    var expired = new TreeSet<RequesterPrivilege<T>>();
    for (var expiredActivation : expiredActivations) {
      //
      // Find the corresponding privilege to determine
      // whether this is currently eligible.
      //
      // it might have changed in the meantime, but that's ok.
      //
      var correspondingRequesterPrivilege = availableRequesterPrivileges
          .stream()
          .filter(privilege -> privilege.id().id().equals(expiredActivation.privilegeId().id()))
          .findFirst();
      if (correspondingRequesterPrivilege.isPresent()) {
        expired.add(new RequesterPrivilege<>(
            expiredActivation.privilegeId(),
            correspondingRequesterPrivilege.get().name(),
            correspondingRequesterPrivilege.get().activationType(),
            RequesterPrivilege.Status.EXPIRED,
            expiredActivation.validity));
      } else {
        expired.add(new RequesterPrivilege<>(
            expiredActivation.privilegeId(),
            expiredActivation.privilegeId().id(),
            new NoActivation(),
            RequesterPrivilege.Status.EXPIRED,
            expiredActivation.validity));
      }
    }

    return new RequesterPrivilegeSet<>(current, expired, warnings);
  }

  public static <TPrivilegeId extends PrivilegeId> RequesterPrivilegeSet<TPrivilegeId> empty() {
    return new RequesterPrivilegeSet<TPrivilegeId>(new TreeSet<>(), new TreeSet<>(), Set.of());
  }

  public record ActivatedRequesterPrivilege<TId>(TId privilegeId, TimeSpan validity) {
    public ActivatedRequesterPrivilege {
      Preconditions.checkNotNull(privilegeId, "privilegeId");
      Preconditions.checkNotNull(validity, "validity");
    }
  }
}
