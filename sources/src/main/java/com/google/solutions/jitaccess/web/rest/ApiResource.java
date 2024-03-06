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
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
  public @NotNull MetadataAction.ResponseEntity getMetadata(
    @Context @NotNull SecurityContext securityContext
  ) {
   return this.metadataAction.execute(
     (IapPrincipal)securityContext.getUserPrincipal());
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
    return this.listProjectsAction.execute(
      (IapPrincipal)securityContext.getUserPrincipal());
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
   * Request approval to activate one or more project roles.
   * Only allowed for MPA-eligible roles.
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
