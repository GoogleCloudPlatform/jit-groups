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
import com.google.solutions.jitaccess.catalog.auth.PrincipalId;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Allows access to a defined set of principals.
 *
 * @param entries list of access control list entries.
 */
public record AccessControlList(
  @NotNull Collection<Entry> entries
){
  /**
   * Empty ACL, neither allows nor denies access.
   */
  public static final @NotNull AccessControlList EMPTY = new AccessControlList(List.of());

  /**
   * Check whether access is allowed access for any of the provided
   * principals.
   *
   * Access control list entries are evaluated in order. Access is
   * denied as soon as a matching deny-entry is encountered.
   */
  private static boolean isAllowed(
    @NotNull Iterable<PrincipalId> principals,
    @NotNull Iterable<Entry> accessEntries,
    int requiredAccessRights
  ) {
    Preconditions.checkArgument(requiredAccessRights != 0, "requiredAccessMask");

    //
    // NB. It's possible that no single principal is granted all the requested
    // access. Therefore, we can't check each principal individually, but
    // instead have to calculate the effective access mask across all principals.
    //

    int effectiveAccessMaskForSubject = 0;

    for (var principal : principals) {
      for (var entry : accessEntries) {
        if (entry.principal.equals(principal)) {
          if (entry instanceof AllowedEntry allowedEntry) {
            //
            // Accumulate access granted by this entry.
            //
            effectiveAccessMaskForSubject |= allowedEntry.accessRights;
          }
          else if (entry instanceof DeniedEntry deniedEntry &&
            (deniedEntry.accessRights & requiredAccessRights) != 0) {
            //
            // At least one of the required access bits is denied.
            //
            return false;
          }
        }
      }
    }

    return (effectiveAccessMaskForSubject & requiredAccessRights) == requiredAccessRights;
  }

  /**
   * @return principals that have granted the requested level of access.
   */
  public Set<PrincipalId> allowedPrincipals(int requiredAccessRights) {
    //
    // Segment the list of entries by principal.
    //
    var entriesByPrincipals = this.entries
      .stream()
      .collect(Collectors.groupingBy(e -> e.principal));

    //
    // Filter down the list of principals to those that have been
    // granted the requested level of access.
    //
    return entriesByPrincipals
      .entrySet()
      .stream()
      .filter(e -> isAllowed(
        Collections.singleton(e.getKey()), // Principal
        e.getValue(), // List of access control list entries
        requiredAccessRights))
      .map(e -> e.getKey())
      .collect(Collectors.toSet());
  }

  /**
   * Check whether a subject is allowed access.
   *
   * Access control list entries are evaluated in order. Access is
   * denied as soon as a matching deny-entry is encountered.
   */
  public boolean isAllowed(@NotNull Subject subject, int requiredAccessRights) {
    Preconditions.checkArgument(requiredAccessRights != 0, "requiredAccessRights");

    return isAllowed(
      subject.principals()
        .stream()
        .filter(p -> p.isValid())
        .map(p -> p.id())
        .collect(Collectors.toList()),
      this.entries, requiredAccessRights);
  }

  //---------------------------------------------------------------------------
  // Inner classes.
  //---------------------------------------------------------------------------

  /**
   * An access control list entry.
   */
  public static abstract class Entry {
    /**
     * Kind of access granted, where each bit represents a
     * particular kind of access.
     */
    public final int accessRights;

    /**
     * Principal that is being granted access.
     */
    public final @NotNull PrincipalId principal;

    protected Entry(@NotNull PrincipalId principal, int accessRights) {
      this.principal = principal;
      this.accessRights = accessRights;
    }
  }

  /**
   * Entry that grants certain access.
   */
  public static class AllowedEntry extends Entry {
    public AllowedEntry(@NotNull PrincipalId principal, int accessMask) {
      super(principal, accessMask);
    }

    @Override
    public String toString() {
      return String.format("Allow %s: %08X", this.principal, this.accessRights);
    }
  }

  /**
   * Entry that denies certain access.
   */
  public static class DeniedEntry extends Entry {
    public DeniedEntry(@NotNull PrincipalId principal, int accessMask) {
      super(principal, accessMask);
    }

    @Override
    public String toString() {
      return String.format("Deny %s: %08X", this.principal, this.accessRights);
    }
  }

  public static class Builder {
    private final List<Entry> entries = new LinkedList<>();

    public Builder allow(PrincipalId principal, int accessMask) {
      this.entries.add(new AllowedEntry(principal, accessMask));
      return this;
    }

    public Builder deny(PrincipalId principal, int accessMask) {
      this.entries.add(new DeniedEntry(principal, accessMask));
      return this;
    }

    public AccessControlList build() {
      return new AccessControlList(Collections.unmodifiableList(this.entries));
    }
  }
}
