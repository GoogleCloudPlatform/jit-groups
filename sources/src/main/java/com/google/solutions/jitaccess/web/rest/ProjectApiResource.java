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

package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.core.catalog.JustificationPolicy;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.TokenSigner;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRoleActivator;
import com.google.solutions.jitaccess.core.notifications.NotificationService;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.RequireIapPrincipal;
import com.google.solutions.jitaccess.web.RequireXsrfHeader;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import com.google.solutions.jitaccess.web.actions.*;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;

/**
 * REST API controller that uses a project-based catalog.
 */
@Dependent
@Path("/api/")
@RequireXsrfHeader
@RequireIapPrincipal
public class ProjectApiResource extends AbstractApiResource<ProjectId> {

  @Inject
  LogAdapter logAdapter;

  @Inject
  MpaProjectRoleCatalog catalog;

  @Inject
  JustificationPolicy justificationPolicy;

  @Inject
  RuntimeEnvironment runtimeEnvironment;

  @Inject
  ProjectRoleActivator activator;

  @Inject
  Instance<NotificationService> notificationServices;

  @Inject
  TokenSigner tokenSigner;

  // -------------------------------------------------------------------------
  // Overrides.
  // -------------------------------------------------------------------------

  public MetadataAction metadataAction() {
    return new MetadataAction(
      this.logAdapter,
      this.catalog,
      this.justificationPolicy);
  }

  public ListScopesAction listScopesAction() {
    return new ListScopesAction(this.logAdapter, this.catalog);
  }

  public ListRolesAction listRolesAction() {
    return new ListRolesAction(this.logAdapter, this.catalog);
  }

  public ListPeersAction listPeersAction() {
    return new ListPeersAction(this.logAdapter, this.catalog);
  }

  public RequestAndSelfApproveAction requestAndSelfApproveAction() {
    return new RequestAndSelfApproveAction(
      this.logAdapter,
      this.runtimeEnvironment,
      this.activator,
      this.notificationServices,
      this.catalog);
  }

  public RequestActivationAction requestActivationAction() {
    return new RequestActivationAction(
      this.logAdapter,
      this.runtimeEnvironment,
      this.activator,
      this.notificationServices,
      this.catalog,
      this.tokenSigner);
  }

  public IntrospectActivationRequestAction introspectActivationRequestAction() {
    return new IntrospectActivationRequestAction(
      this.logAdapter,
      this.runtimeEnvironment,
      this.activator,
      this.notificationServices,
      this.tokenSigner);
  }

  public ApproveActivationRequestAction approveActivationRequestAction() {
    return new ApproveActivationRequestAction(
      this.logAdapter,
      this.runtimeEnvironment,
      this.activator,
      this.notificationServices,
      this.catalog,
      this.tokenSigner);
  }

  @Override
  protected String scopeType() {
    return "projects";
  }
}
