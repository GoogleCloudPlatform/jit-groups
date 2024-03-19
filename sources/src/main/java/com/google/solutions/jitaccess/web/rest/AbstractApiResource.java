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

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.ResourceNotFoundException;
import com.google.solutions.jitaccess.core.catalog.ResourceId;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import com.google.solutions.jitaccess.web.actions.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.UUID;

/**
 * REST API controller.
 */
public abstract class AbstractApiResource<TScope extends ResourceId> {
  protected abstract MetadataAction metadataAction();

  protected abstract ListScopesAction listScopesAction();

  protected abstract ListRolesAction listRolesAction();

  protected abstract ListPeersAction listPeersAction();

  protected abstract RequestAndSelfApproveAction requestAndSelfApproveAction();

  protected abstract RequestActivationAction requestActivationAction();

  protected abstract IntrospectActivationRequestAction introspectActivationRequestAction();

  protected abstract ApproveActivationRequestAction approveActivationRequestAction();

  protected abstract String scopeType();

  private void checkScopeType(
    @Nullable String scopeType
  ) throws ResourceNotFoundException {
    if (!scopeType.equals(this.scopeType())) {
      throw new ResourceNotFoundException(scopeType);
    }
  }

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
   return this.metadataAction().execute(
     (IapPrincipal)securityContext.getUserPrincipal());
  }

  /**
   * List scopes that the calling user can access.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("{scopeType}")
  public @NotNull ListScopesAction.ResponseEntity listScopes(
    @PathParam("scopeType") @Nullable String scopeType,
    @Context @NotNull SecurityContext securityContext
  ) throws AccessException, IOException {
    checkScopeType(scopeType);

    return this.listScopesAction().execute(
      (IapPrincipal)securityContext.getUserPrincipal());
  }

  /**
   * List roles (within a scope) that the user can activate.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("{scopeType}/{scope}/roles")
  public @NotNull ListRolesAction.ResponseEntity listRoles(
    @PathParam("scopeType") @Nullable String scopeType,
    @PathParam("scope") @Nullable String scope,
    @Context @NotNull SecurityContext securityContext
  ) throws AccessException {
    checkScopeType(scopeType);

    return this.listRolesAction().execute(
      (IapPrincipal)securityContext.getUserPrincipal(),
      scope);
  }

  /**
   * List peers that are qualified to approve the activation of a role.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("{scopeType}/{scope}/peers")
  public @NotNull ListPeersAction.ResponseEntity listPeers(
    @PathParam("scopeType") @Nullable String scopeType,
    @PathParam("scope") @Nullable String scope,
    @QueryParam("role") @Nullable String role,
    @Context @NotNull SecurityContext securityContext
  ) throws AccessException {
    checkScopeType(scopeType);

    return this.listPeersAction().execute(
      (IapPrincipal)securityContext.getUserPrincipal(),
      scope,
      role);
  }

  /**
   * Self-activate one or more roles. Only allowed for JIT-eligible roles.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("{scopeType}/{scope}/roles/self-activate")
  public @NotNull AbstractActivationAction.ResponseEntity requestAndSelfApprove(
    @PathParam("scopeType") @Nullable String scopeType,
    @PathParam("scope") @Nullable String scope,
    @NotNull RequestAndSelfApproveAction.RequestEntity request,
    @Context @NotNull SecurityContext securityContext
  ) throws AccessException {
    checkScopeType(scopeType);

    return this.requestAndSelfApproveAction().execute(
      (IapPrincipal)securityContext.getUserPrincipal(),
      scope,
      request);
  }

  /**
   * Request approval to activate one or more roles.
   * Only allowed for MPA-eligible roles.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("{scopeType}/{scope}/roles/request")
  public @NotNull AbstractActivationAction.ResponseEntity requestActivation(
    @PathParam("scopeType") @Nullable String scopeType,
    @PathParam("scope") @Nullable String scope,
    @NotNull RequestActivationAction.RequestEntity request,
    @Context @NotNull SecurityContext securityContext,
    @Context @NotNull UriInfo uriInfo
  ) throws AccessException {
    checkScopeType(scopeType);

    return requestActivationAction().execute(
      (IapPrincipal)securityContext.getUserPrincipal(),
      scope,
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
    return introspectActivationRequestAction().execute(
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
    return approveActivationRequestAction().execute(
      (IapPrincipal)securityContext.getUserPrincipal(),
      obfuscatedActivationToken,
      uriInfo);
  }
}
