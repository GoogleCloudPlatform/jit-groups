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

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRoleBinding;

import java.io.IOException;
import java.util.SortedSet;

/**
 * A catalog of requester privileges that can be browsed by the user.
 */
public interface RequesterPrivilegeCatalog<TPrivilegeId extends PrivilegeId, TScopeId extends ResourceId> {
  /**
   * Verify if a user is allowed to make the given request.
   */
  void verifyUserCanRequest(
      ActivationRequest<TPrivilegeId> request) throws AccessException, IOException;

  /**
   * Verify if a user is allowed to approve a given request.
   */
  void verifyUserCanApprove(
      UserEmail approvingUser,
      ActivationRequest<TPrivilegeId> request) throws AccessException, IOException;

  /**
   * List scopes that the user has any privileges for.
   */
  SortedSet<TScopeId> listScopes(
      UserEmail user) throws AccessException, IOException;

  /**
   * List available reviewers for (MPA-) activating a privilege.
   */
  SortedSet<UserEmail> listReviewers(
      UserEmail requestingUser,
      RequesterPrivilege<TPrivilegeId> privilege) throws AccessException, IOException;

  /**
   * List available privileges.
   */
  RequesterPrivilegeSet<TPrivilegeId> listRequesterPrivileges(
      UserEmail user,
      TScopeId scope) throws AccessException, IOException;
}
