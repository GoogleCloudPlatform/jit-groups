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

import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.solutions.jitaccess.apis.IamRole;
import com.google.solutions.jitaccess.apis.ResourceId;
import com.google.solutions.jitaccess.util.Coalesce;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * IAM role binding for a resource.
 *
 * @param resource resource
 * @param role IAM role
 * @param description description, optional
 * @param condition IAM condition, optional
 */
public record IamRoleBinding(
  @NotNull ResourceId resource,
  @NotNull IamRole role,
  @Nullable String description,
  @Nullable String condition
) implements Privilege {
  @Override
  public String toString() {
    return Coalesce.nonEmpty(
      this.description,
      String.format("%s on %s",
        this.role,
        this.resource));
  }

  public IamRoleBinding(
    @NotNull ResourceId resource,
    @NotNull IamRole role
  ) {
    this(resource, role, null, null);
  }

  /**
   * Create a checksum that doesn't depend on the current
   * JRE version's implementation of String.hashCode().
   */
  public int checksum() {
    var hash = Hashing.crc32().newHasher();
    hash.putString(this.resource.id(), Charset.defaultCharset());
    hash.putString(this.role.name(), Charset.defaultCharset());
    hash.putString(Strings.nullToEmpty(this.condition), Charset.defaultCharset());
    hash.putString(Strings.nullToEmpty(this.description), Charset.defaultCharset());

    return hash.hash().asInt();
  }
}
