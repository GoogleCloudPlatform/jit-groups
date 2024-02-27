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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserEmail;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilegeCatalog;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilegeSet;

import java.io.IOException;
import java.util.SortedSet;

/**
 * Catalog for project-level role bindings.
 */
public abstract class ProjectRoleCatalog implements RequesterPrivilegeCatalog<ProjectRoleBinding> {
  /**
   * List projects that the user has any requester privileges for.
   */
  public abstract SortedSet<ProjectId> listProjects(
      UserEmail user) throws AccessException, IOException;

  /**
   * List available requester privileges.
   */

  public abstract RequesterPrivilegeSet<ProjectRoleBinding> listRequesterPrivileges(
      UserEmail user,
      ProjectId projectId) throws AccessException, IOException;

  /**
   * List available reviewers for (MPA-) activating a privilege.
   */
  public abstract SortedSet<UserEmail> listReviewers(
      UserEmail requestingUser,
      RequesterPrivilege<ProjectRoleBinding> privilege) throws AccessException, IOException;
}
