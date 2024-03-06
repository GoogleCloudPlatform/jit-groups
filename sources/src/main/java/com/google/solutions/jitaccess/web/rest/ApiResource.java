//
// Copyright 2021 Google LLC
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

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRoleActivator;
import com.google.solutions.jitaccess.core.notifications.NotificationService;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API controller.
 */
@Dependent
@Path("/api/")
public class ApiResource {

  @Inject
  MetadataAction metadataAction;

  @Inject
  ListProjectsAction listProjectsAction;

  @Inject
  ListRolesAction listRolesAction;

  @Inject
  ListPeersAction listPeersAction;

  @Inject
  RequestAndSelfApproveAction requestAndSelfApproveAction;

  @Inject
  RequestActivationAction requestActivationAction;

  @Inject
  IntrospectActivationRequestAction introspectActivationRequestAction;

  @Inject
  ApproveActivationRequestAction approveActivationRequestAction;


  // TODO: remove below.

  @Inject
  MpaProjectRoleCatalog mpaCatalog;

  @Inject
  ProjectRoleActivator projectRoleActivator;

  @Inject
  Instance<NotificationService> notificationServices;

  @Inject
  RuntimeEnvironment runtimeEnvironment;

  @Inject
  JustificationPolicy justificationPolicy;

  @Inject
  TokenSigner tokenSigner;

  @Inject
  LogAdapter logAdapter;

  @Inject
  Options options;


  // -------------------------------------------------------------------------
  // REST resources.
  // -------------------------------------------------------------------------

  /**
   * Return friendly error to browsers that still have the 1.0 frontend cached.
   */
  @GET
  public void getRoot() {
    //
    // Version 1.0 allowed static assets (including index.html) to be cached aggressively.
    // After an upgrade, it's therefore likely that browsers still load the outdated
    // frontend. Let the old frontend show a warning with a cache-busting link.
    //
    throw new NotFoundException(
      "You're viewing an outdated version of the application, " +
      String.format(
        "<a href='/?_=%s'>please refresh your browser</a>",
        UUID.randomUUID()));
  }

  /**
   * Get information about this instance of JIT Access.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("policy")
  public @NotNull MetadataAction.ResponseEntity getPolicy( //TODO: rename to metadata
    @Context @NotNull SecurityContext securityContext
  ) {
   return this.metadataAction.execute((IapPrincipal)securityContext.getUserPrincipal());
  }


  /**
   * List projects that the calling user can access.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects")
  public @NotNull ListProjectsAction.ResponseEntity listProjects(
    @Context @NotNull SecurityContext securityContext
  ) throws AccessException {
    return this.listProjectsAction.execute((IapPrincipal)securityContext.getUserPrincipal());
  }

  /**
   * List roles (within a project) that the user can activate.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects/{projectId}/roles")
  public @NotNull ListRolesAction.ResponseEntity listRoles(
    @PathParam("projectId") @Nullable String projectIdString,
    @Context @NotNull SecurityContext securityContext
  ) throws AccessException {
    return this.listRolesAction.execute(
      (IapPrincipal)securityContext.getUserPrincipal(),
      projectIdString);
  }

  /**
   * List peers that are qualified to approve the activation of a role.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects/{projectId}/peers")
  public @NotNull ListPeersAction.ResponseEntity listPeers(
    @PathParam("projectId") @Nullable String projectIdString,
    @QueryParam("role") @Nullable String role,
    @Context @NotNull SecurityContext securityContext
  ) throws AccessException {
    return this.listPeersAction.execute(
      (IapPrincipal)securityContext.getUserPrincipal(),
      projectIdString,
      role);
  }

  /**
   * Self-activate one or more project roles. Only allowed for JIT-eligible roles.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects/{projectId}/roles/self-activate")
  public @NotNull AbstractActivationAction.ResponseEntity requestAndSelfApprove(
    @PathParam("projectId") @Nullable String projectIdString,
    @NotNull RequestAndSelfApproveAction.RequestEntity request,
    @Context @NotNull SecurityContext securityContext
  ) throws AccessDeniedException {
    return this.requestAndSelfApproveAction.execute(
    (IapPrincipal)securityContext.getUserPrincipal(),
      projectIdString,
      request);
  }


  /**
   * Request approval to activate one or more project roles. Only allowed for MPA-eligible roles.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects/{projectId}/roles/request")
  public @NotNull AbstractActivationAction.ResponseEntity requestActivation(
    @PathParam("projectId") @Nullable String projectIdString,
    @NotNull RequestActivationAction.RequestEntity request,
    @Context @NotNull SecurityContext securityContext,
    @Context @NotNull UriInfo uriInfo
  ) throws AccessDeniedException {
    return requestActivationAction.execute(
      (IapPrincipal)securityContext.getUserPrincipal(),
      projectIdString,
      request,
      uriInfo);
  }

  /**
   * Get details of an activation request.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("activation-request")
  public @NotNull AbstractActivationAction.ResponseEntity getActivationRequest(
    @QueryParam("activation") @Nullable String obfuscatedActivationToken,
    @Context @NotNull SecurityContext securityContext
  ) throws AccessException {
    return introspectActivationRequestAction.execute(
      (IapPrincipal)securityContext.getUserPrincipal(),
      obfuscatedActivationToken);
  }

  /**
   * Approve an activation request from a peer.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("activation-request")
  public @NotNull AbstractActivationAction.ResponseEntity approveActivationRequest(
    @QueryParam("activation") @Nullable String obfuscatedActivationToken,
    @Context @NotNull SecurityContext securityContext,
    @Context @NotNull UriInfo uriInfo
  ) throws AccessException {
    return approveActivationRequestAction.execute(
      (IapPrincipal)securityContext.getUserPrincipal(),
      obfuscatedActivationToken,
      uriInfo);
  }

  // -------------------------------------------------------------------------
  // Notifications.
  // -------------------------------------------------------------------------

  /**
   * Notification indicating that a multi-party approval request has been made
   * and is pending approval.
   */
  public static class RequestActivationNotification extends NotificationService.Notification
  {
    protected RequestActivationNotification(
      @NotNull ProjectId projectId,
      @NotNull MpaActivationRequest<com.google.solutions.jitaccess.core.catalog.project.ProjectRole> request,
      Instant requestExpiryTime,
      @NotNull URL activationRequestUrl) throws MalformedURLException
    {
      super(
        request.reviewers(),
        List.of(request.requestingUser()),
        String.format(
          "%s requests access to project %s",
          request.requestingUser(),
          projectId.id()));

      assert request.entitlements().size() == 1;

      this.properties.put("BENEFICIARY", request.requestingUser());
      this.properties.put("REVIEWERS", request.reviewers());
      this.properties.put("PROJECT_ID", projectId);
      this.properties.put("ROLE", request
        .entitlements()
        .stream()
        .findFirst()
        .get()
        .roleBinding()
        .role());
      this.properties.put("START_TIME", request.startTime());
      this.properties.put("END_TIME", request.endTime());
      this.properties.put("REQUEST_EXPIRY_TIME", requestExpiryTime);
      this.properties.put("JUSTIFICATION", request.justification());
      this.properties.put("BASE_URL", new URL(activationRequestUrl, "/").toString());
      this.properties.put("ACTION_URL", activationRequestUrl.toString());
    }

    @Override
    public @NotNull String getType() {
      return "RequestActivation";
    }
  }

  /**
   * Notification indicating that a multi-party approval was granted.
   */
  public static class ActivationApprovedNotification extends NotificationService.Notification {
    protected ActivationApprovedNotification(
      ProjectId projectId,
      @NotNull Activation<com.google.solutions.jitaccess.core.catalog.project.ProjectRole> activation,
      @NotNull UserEmail approver,
      URL activationRequestUrl) throws MalformedURLException
    {
      super(
        List.of(activation.request().requestingUser()),
        ((MpaActivationRequest<com.google.solutions.jitaccess.core.catalog.project.ProjectRole>)activation.request()).reviewers(), // Move reviewers to CC.
        String.format(
          "%s requests access to project %s",
          activation.request().requestingUser(),
          projectId));

      var request = (MpaActivationRequest<com.google.solutions.jitaccess.core.catalog.project.ProjectRole>)activation.request();
      assert request.entitlements().size() == 1;

      this.properties.put("APPROVER", approver.email);
      this.properties.put("BENEFICIARY", request.requestingUser());
      this.properties.put("REVIEWERS", request.reviewers());
      this.properties.put("PROJECT_ID", projectId);
      this.properties.put("ROLE", activation.request()
        .entitlements()
        .stream()
        .findFirst()
        .get()
        .roleBinding()
        .role());
      this.properties.put("START_TIME", request.startTime());
      this.properties.put("END_TIME", request.endTime());
      this.properties.put("JUSTIFICATION", request.justification());
      this.properties.put("BASE_URL", new URL(activationRequestUrl, "/").toString());
    }

    @Override
    protected boolean isReply() {
      return true;
    }

    @Override
    public @NotNull String getType() {
      return "ActivationApproved";
    }
  }

  /**
   * Notification indicating that a self-approval was performed.
   */
  public static class ActivationSelfApprovedNotification extends NotificationService.Notification {
    protected ActivationSelfApprovedNotification(
      ProjectId projectId,
      @NotNull Activation<com.google.solutions.jitaccess.core.catalog.project.ProjectRole> activation)
    {
      super(
        List.of(activation.request().requestingUser()),
        List.of(),
        String.format(
          "Activated roles %s on '%s'",
          activation.request().entitlements().stream()
            .map(ent -> String.format("'%s'", ent.roleBinding().role()))
            .collect(Collectors.joining(", ")),
          projectId));

      this.properties.put("BENEFICIARY", activation.request().requestingUser());
      this.properties.put("PROJECT_ID", projectId);
      this.properties.put("ROLE", activation.request()
        .entitlements()
        .stream()
        .map(ent -> ent.roleBinding().role())
        .collect(Collectors.joining(", ")));
      this.properties.put("START_TIME", activation.request().startTime());
      this.properties.put("END_TIME", activation.request().endTime());
      this.properties.put("JUSTIFICATION", activation.request().justification());
    }

    @Override
    protected boolean isReply() {
      return true;
    }

    @Override
    public @NotNull String getType() {
      return "ActivationSelfApproved";
    }
  }

  // -------------------------------------------------------------------------
  // Options.
  // -------------------------------------------------------------------------

  public record Options(int maxNumberOfJitRolesPerSelfApproval) {
    public Options {
      Preconditions.checkArgument(
        maxNumberOfJitRolesPerSelfApproval > 0,
        "The maximum number of JIT roles per self-approval must exceed 1");
    }
  }
}
