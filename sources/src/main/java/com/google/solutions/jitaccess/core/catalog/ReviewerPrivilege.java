//
// Copyright 2023 Google LLC
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

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a reviewer privilege.
 */

public record ReviewerPrivilege<TPrivilegeId extends PrivilegeId>(
    TPrivilegeId id,
    String name,
    Set<ActivationType> reviewableTypes)
    implements Comparable<ReviewerPrivilege<TPrivilegeId>> {

  public ReviewerPrivilege

  {
    Preconditions.checkNotNull(id, "id");
    Preconditions.checkNotNull(name, "name");
    Preconditions.checkArgument(!reviewableTypes.isEmpty(),
        "reviewableTypes must contain at least one activation type.");
  }

  @Override
  public String toString() {
    return this.name;
  }

  @Override
  public int compareTo(ReviewerPrivilege<TPrivilegeId> o) {
    return Comparator
        .comparing((ReviewerPrivilege<TPrivilegeId> e) -> e.reviewableTypes.stream()
            .map(s -> s.name())
            .sorted()
            .collect(Collectors.joining("-")))
        .thenComparing(e -> e.id)
        .compare(this, o);
  }
}
