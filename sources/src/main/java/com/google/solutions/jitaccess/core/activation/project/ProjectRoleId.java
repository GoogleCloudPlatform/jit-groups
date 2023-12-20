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

package com.google.solutions.jitaccess.core.activation.project;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.activation.EntitlementId;
import com.google.solutions.jitaccess.core.entitlements.RoleBinding;

public class ProjectRoleId extends EntitlementId {
  static final String CATALOG = "iam";

  private final RoleBinding roleBinding;

  public ProjectRoleId(RoleBinding roleBinding) {
    Preconditions.checkNotNull(roleBinding, "roleBinding");
    this.roleBinding = roleBinding;
  }

  public RoleBinding roleBinding() {
    return this.roleBinding;
  }

  @Override
  public String catalog() {
    return CATALOG;
  }

  @Override
  public String id() {
    return this.roleBinding.toString();
  }

  public ProjectId projectId() {
    return ProjectId.fromFullResourceName(this.roleBinding.fullResourceName());
  }
}
