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

package com.google.solutions.jitaccess.catalog.auth;

import com.google.solutions.jitaccess.catalog.policy.AccessControlList;
import com.google.solutions.jitaccess.catalog.policy.PolicyPermission;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Optional;

/**
 * Abstract implementation of a securable object that supports
 * ACL inheritance.
 */
public abstract class AbstractSecurableComponent implements Securable {
  /**
   * Return the parent object, if present.
   */
  protected abstract @NotNull Optional<? extends AbstractSecurableComponent> parent();

  /**
   * ACL, if any. If the ACL is null or empty, all subjects are granted access.
   */
  protected abstract @NotNull Optional<AccessControlList> accessControlList();

  /**
   * Effective ACL based on the policy's ancestry.
   */
  protected AccessControlList effectiveAccessControlList() {
    //
    // Find all ACLs in ancestry and order them so that the
    // root policy's ACL comes first, and this policy's ACL
    // is last.
    //
    var aclAncestry = new LinkedList<AccessControlList>();
    for (var policy = Optional.of(this);
         policy.isPresent();
         policy = policy.get().parent().map(p -> (AbstractSecurableComponent)p)) {
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
   * Check access based on this object's ACL, and it's ancestry's ACLs.
   */
  public final boolean isAccessAllowed(
    @NotNull Subject subject,
    int requiredAccessRights
  ) {
    return effectiveAccessControlList().isAllowed(
      subject,
      requiredAccessRights);
  }
}
