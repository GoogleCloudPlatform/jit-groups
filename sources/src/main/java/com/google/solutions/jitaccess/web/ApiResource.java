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

package com.google.solutions.jitaccess.web;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.adapters.LogAdapter;
import com.google.solutions.jitaccess.core.data.*;
import com.google.solutions.jitaccess.core.services.ActivationTokenService;
import com.google.solutions.jitaccess.core.services.RoleActivationService;
import com.google.solutions.jitaccess.core.services.RoleDiscoveryService;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
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
  RoleDiscoveryService roleDiscoveryService;

  @Inject
  RoleActivationService roleActivationService;

  @Inject
  ActivationTokenService activationTokenService;

  @Inject
  RuntimeEnvironment runtimeEnvironment;

  @Inject
  LogAdapter logAdapter;

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
   * Get information about configured policies.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("policy")
  public PolicyResponse getPolicy() {
    return new PolicyResponse(
      this.roleActivationService.getOptions().justificationHint
    );
  }

  /**
   * List projects that the calling user can access.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects")
  public ProjectsResponse listProjects(
    @Context SecurityContext securityContext
  ) throws AccessException {
    Preconditions.checkNotNull(this.roleDiscoveryService, "roleDiscoveryService");

    var iapPrincipal = (UserPrincipal) securityContext.getUserPrincipal();

    try {
      var projects = this.roleDiscoveryService.listAvailableProjects(iapPrincipal.getId());

      return new ProjectsResponse(
        projects.stream().map(p -> p.id).collect(Collectors.toSet()));
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_LIST_ROLES,
          String.format("Listing available projects failed: %s", e.getMessage()))
        .write();

      throw new AccessDeniedException("Listing available projects failed, see logs for details");
    }
  }

  /**
   * List eligible roles within a project.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects/{projectId}/roles")
  public ProjectRolesResponse listRoles(
    @PathParam("projectId") String projectIdString,
    @Context SecurityContext securityContext
  ) throws AccessException {
    Preconditions.checkNotNull(this.roleDiscoveryService, "roleDiscoveryService");

    Preconditions.checkArgument(
      projectIdString != null && !projectIdString.trim().isEmpty(),
      "A projectId is required");

    var iapPrincipal = (UserPrincipal) securityContext.getUserPrincipal();
    var projectId = new ProjectId(projectIdString);

    try {
      var bindings = this.roleDiscoveryService.listEligibleProjectRoles(
        iapPrincipal.getId(),
        projectId);

      return new ProjectRolesResponse(
        bindings.getItems(),
        bindings.getWarnings());
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_LIST_ROLES,
          String.format("Listing project roles failed: %s", e.getMessage()))
        .addLabels(le -> addLabels(le, e))
        .addLabels(le -> addLabels(le, projectId))
        .write();

      throw new AccessDeniedException("Listing project roles failed, see logs for details");
    }
  }

  /**
   * List qualified peers for a role.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects/{projectId}/peers")
  public ProjectRolePeersResponse listPeers(
    @PathParam("projectId") String projectIdString,
    @QueryParam("role") String role,
    @Context SecurityContext securityContext
  ) throws AccessException {
    Preconditions.checkNotNull(this.roleDiscoveryService, "roleDiscoveryService");

    Preconditions.checkArgument(
      projectIdString != null && !projectIdString.trim().isEmpty(),
      "A projectId is required");
    Preconditions.checkArgument(
      role != null && !role.trim().isEmpty(),
      "A role is required");

    var iapPrincipal = (UserPrincipal) securityContext.getUserPrincipal();
    var projectId = new ProjectId(projectIdString);
    var roleBinding = new RoleBinding(projectId, role);

    try {
      var peers = this.roleDiscoveryService.listApproversForProjectRole(
        iapPrincipal.getId(),
        roleBinding);

      assert !peers.contains(iapPrincipal.getId());

      return new ProjectRolePeersResponse(peers);
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_LIST_PEERS,
          String.format("Listing peers failed: %s", e.getMessage()))
        .addLabels(le -> addLabels(le, e))
        .addLabels(le -> addLabels(le, roleBinding))
        .addLabels(le -> addLabels(le, projectId))
        .write();

      throw new AccessDeniedException("Listing peers failed, see logs for details");
    }
  }

  /**
   * Self-activate one or more project roles. Only allowed for JIT-eligible roles.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects/{projectId}/roles/self-activate")
  public ActivationStatusResponse selfApproveActivation(
    @PathParam("projectId") String projectIdString,
    SelfActivationRequest request,
    @Context SecurityContext securityContext
  ) throws AccessDeniedException {
    Preconditions.checkNotNull(this.roleDiscoveryService, "roleDiscoveryService");

    Preconditions.checkArgument(
      projectIdString != null && !projectIdString.trim().isEmpty(),
      "A projectId is required");
    Preconditions.checkArgument(
      request != null && request.roles != null && request.roles.size() > 0 && request.roles.size() <= 10,
      "At least one role is required");
    Preconditions.checkArgument(
      request.justification != null && request.justification.length() > 0 && request.justification.length() < 100,
      "A justification must be provided");

    var iapPrincipal = (UserPrincipal) securityContext.getUserPrincipal();
    var projectId = new ProjectId(projectIdString);

    //
    // NB. The input list of roles might contain duplicates, therefore reduce to a set.
    //
    var roleBindings = request.roles
      .stream()
      .map(r -> new RoleBinding(projectId.getFullResourceName(), r))
      .collect(Collectors.toSet());

    var activations = new ArrayList<RoleActivationService.Activation>();
    for (var roleBinding : roleBindings) {
      try {
        var activation = this.roleActivationService.activateProjectRoleForSelf(
          iapPrincipal.getId(),
          roleBinding,
          request.justification);

        assert activation != null;
        activations.add(activation);

        this.logAdapter
          .newInfoEntry(
            LogEvents.API_ACTIVATE_ROLE,
            String.format(
              "User %s activated role '%s' on '%s' for themselves",
              iapPrincipal.getId(),
              roleBinding.role,
              roleBinding.fullResourceName))
          .addLabels(le -> addLabels(le, activation))
          .addLabel("justification", request.justification)
          .write();
      }
      catch (Exception e) {
        this.logAdapter
          .newErrorEntry(
            LogEvents.API_ACTIVATE_ROLE,
            String.format(
              "User %s failed to activate role '%s' on '%s' for themselves: %s",
              iapPrincipal.getId(),
              roleBinding.role,
              roleBinding.fullResourceName,
              e.getMessage()))
          .addLabels(le -> addLabels(le, projectId))
          .addLabels(le -> addLabels(le, roleBinding))
          .addLabels(le -> addLabels(le, e))
          .addLabel("justification", request.justification)
          .write();

        if (e instanceof AccessDeniedException) {
          throw (AccessDeniedException)e.fillInStackTrace();
        }
        else {
          throw new AccessDeniedException("Activating role failed", e);
        }
      }
    }

    assert activations.size() == roleBindings.size();

    return new ActivationStatusResponse(
      iapPrincipal.getId(),
      Set.of(),
      true,
      false,
      request.justification,
      activations
        .stream()
        .map(a -> new ActivationStatusResponse.ActivationStatus(a))
        .collect(Collectors.toList()));
  }

  /**
   * Request approval to activate one or more project roles. Only allowed for MPA-eligible roles.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects/{projectId}/roles/request")
  public ActivationStatusResponse requestActivation(
    @PathParam("projectId") String projectIdString,
    ActivationRequest request,
    @Context SecurityContext securityContext,
    @Context UriInfo uriInfo
  ) throws AccessDeniedException {
    Preconditions.checkNotNull(this.roleDiscoveryService, "roleDiscoveryService");
    Preconditions.checkNotNull(this.activationTokenService, "activationTokenService");

    Preconditions.checkArgument(
      projectIdString != null && !projectIdString.trim().isEmpty(),
      "A projectId is required");
    Preconditions.checkArgument(request != null);
    Preconditions.checkArgument(
      request.role != null && !request.role.isEmpty(),
      "A role is required");
    Preconditions.checkArgument(
      request.peers != null && request.peers.size() > 0 && request.peers.size() <= 10,
      "At least one peer is required");
    Preconditions.checkArgument(
      request.justification != null && request.justification.length() > 0 && request.justification.length() < 100,
      "A justification must be provided");

    var iapPrincipal = (UserPrincipal) securityContext.getUserPrincipal();
    var projectId = new ProjectId(projectIdString);
    var roleBinding = new RoleBinding(projectId, request.role);

    try
    {
      //
      // Validate request.
      //
      var activationRequest = this.roleActivationService.createActivationRequestForPeer(
        iapPrincipal.getId(),
        request.peers.stream().map(email -> new UserId(email)).collect(Collectors.toSet()),
        roleBinding,
        request.justification);

      //
      // Create an approval token.
      //
      var activationToken = this.activationTokenService.createToken(activationRequest);
      var uri = uriInfo.getBaseUriBuilder()
        .scheme(this.runtimeEnvironment.getScheme())
        .path("/")
        .queryParam("activation", activationToken)
        .build();

      // TODO: Send notification, token to peers.
      // use    @Context UriInfo uriInfo
      System.out.println("APPROVAL-URL: " + uri);

      this.logAdapter
        .newInfoEntry(
          LogEvents.API_REQUEST_ROLE,
          String.format(
            "User %s requested role '%s' on '%s'",
            iapPrincipal.getId(),
            roleBinding.role,
            roleBinding.fullResourceName))
        .addLabels(le -> addLabels(le, projectId))
        .addLabels(le -> addLabels(le, activationRequest))
        .write();

      return new ActivationStatusResponse(
        iapPrincipal.getId(),
        activationRequest,
        ProjectRole.Status.ACTIVATION_PENDING);
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_REQUEST_ROLE,
          String.format(
            "User %s failed to request role '%s' on '%s': %s",
            iapPrincipal.getId(),
            roleBinding.role,
            roleBinding.fullResourceName,
            e.getMessage()))
        .addLabels(le -> addLabels(le, projectId))
        .addLabels(le -> addLabels(le, roleBinding))
        .addLabels(le -> addLabels(le, e))
        .addLabel("justification", request.justification)
        .write();

      if (e instanceof AccessDeniedException) {
        throw (AccessDeniedException)e.fillInStackTrace();
      }
      else {
        throw new AccessDeniedException("Activating role failed", e);
      }
    }
  }

  /**
   * Get details of an activation request.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("activation-request")
  public ActivationStatusResponse getActivationRequest(
    @QueryParam("activation") String activationToken,
    @Context SecurityContext securityContext
  ) throws AccessException {
    Preconditions.checkNotNull(this.activationTokenService, "activationTokenService");

    Preconditions.checkArgument(
      activationToken != null && !activationToken.trim().isEmpty(),
      "An activation token is required");

    var iapPrincipal = (UserPrincipal) securityContext.getUserPrincipal();

    try {
      var activationRequest = this.activationTokenService.verifyToken(activationToken);

      if (!activationRequest.beneficiary.equals(iapPrincipal.getId()) &&
          !activationRequest.reviewers.contains(iapPrincipal.getId())) {
        throw new AccessDeniedException("The calling user is not authorized to access this approval request");
      }

      return new ActivationStatusResponse(
        iapPrincipal.getId(),
        activationRequest,
        ProjectRole.Status.ACTIVATION_PENDING); // TODO: Check if activated already.
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_GET_REQUEST,
          String.format("Accessing the activation request failed: %s", e.getMessage()))
        .addLabels(le -> addLabels(le, e))
        .write();

      throw new AccessDeniedException("Accessing the activation request failed");
    }
  }

  /**
   * Approve an activation request from a peer.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("activation-request")
  public ActivationStatusResponse approveActivationRequest(
    @QueryParam("activation") String activationToken,
    @Context SecurityContext securityContext
  ) throws AccessException {
    Preconditions.checkNotNull(this.activationTokenService, "activationTokenService");
    Preconditions.checkNotNull(this.roleActivationService, "roleActivationService");

    Preconditions.checkArgument(
      activationToken != null && !activationToken.trim().isEmpty(),
      "An activation token is required");

    var iapPrincipal = (UserPrincipal) securityContext.getUserPrincipal();

    RoleActivationService.ActivationRequest activationRequest;
    try {
      activationRequest = this.activationTokenService.verifyToken(activationToken);
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_ACTIVATE_ROLE,
          String.format("Accessing the activation request failed: %s", e.getMessage()))
        .addLabels(le -> addLabels(le, e))
        .write();

      throw new AccessDeniedException("Accessing the activation request failed");
    }

    try {
      var activation = this.roleActivationService.activateProjectRoleForPeer(
        iapPrincipal.getId(),
        activationRequest);

      assert activation != null;

      // TODO: Send notification

      this.logAdapter
        .newInfoEntry(
          LogEvents.API_ACTIVATE_ROLE,
          String.format(
            "User %s approved role '%s' on '%s' for %s",
            iapPrincipal.getId(),
            activationRequest.roleBinding.role,
            activationRequest.roleBinding.fullResourceName,
            activationRequest.beneficiary))
        .addLabels(le -> addLabels(le, activationRequest))
        .write();

      return new ActivationStatusResponse(
        iapPrincipal.getId(),
        activationRequest.reviewers,
        activationRequest.beneficiary.equals(iapPrincipal.getId()),
        activationRequest.reviewers.contains(iapPrincipal.getId()),
        activationRequest.justification,
        List.of(new ActivationStatusResponse.ActivationStatus(activation)));
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_ACTIVATE_ROLE,
          String.format(
            "User %s failed to activate role '%s' on '%s' for %s: %s",
            iapPrincipal.getId(),
            activationRequest.roleBinding.role,
            activationRequest.roleBinding.fullResourceName,
            activationRequest.beneficiary,
            e.getMessage()))
        .addLabels(le -> addLabels(le, activationRequest))
        .addLabels(le -> addLabels(le, e))
        .write();

      if (e instanceof AccessDeniedException) {
        throw (AccessDeniedException)e.fillInStackTrace();
      }
      else {
        throw new AccessDeniedException("Approving the activation request failed", e);
      }
    }
  }

  // -------------------------------------------------------------------------
  // Logging helper methods.
  // -------------------------------------------------------------------------

  private static LogAdapter.LogEntry addLabels(
    LogAdapter.LogEntry entry,
    RoleActivationService.Activation activation
  ) {
    return entry
      .addLabel("activation_id", activation.id.toString())
      .addLabel("activation_start", activation.startTime.atOffset(ZoneOffset.UTC).toString())
      .addLabel("activation_end", activation.endTime.atOffset(ZoneOffset.UTC).toString())
      .addLabels(e -> addLabels(e, activation.projectRole.roleBinding));
  }

  private static LogAdapter.LogEntry addLabels(
    LogAdapter.LogEntry entry,
    RoleActivationService.ActivationRequest request
  ) {
    return entry
      .addLabel("activation_id", request.id.toString())
      .addLabel("activation_start", request.startTime.atOffset(ZoneOffset.UTC).toString())
      .addLabel("activation_end", request.endTime.atOffset(ZoneOffset.UTC).toString())
      .addLabel("justification", request.justification)
      .addLabel("reviewers", request
        .reviewers
        .stream()
        .map(u -> u.email)
        .collect(Collectors.joining(", ")))
      .addLabels(e -> addLabels(e, request.roleBinding));
  }

  private static LogAdapter.LogEntry addLabels(
    LogAdapter.LogEntry entry,
    RoleBinding roleBinding
  ) {
    return entry
      .addLabel("role", roleBinding.role)
      .addLabel("resource", roleBinding.fullResourceName)
      .addLabel("project_id", ProjectId.fromFullResourceName(roleBinding.fullResourceName).id);
  }

  private static LogAdapter.LogEntry addLabels(
    LogAdapter.LogEntry entry,
    Exception exception
  ) {
    return entry.addLabel("error", exception.getClass().getSimpleName());
  }

  private static LogAdapter.LogEntry addLabels(
    LogAdapter.LogEntry entry,
    ProjectId project
  ) {
    return entry.addLabel("project", project.id);
  }

  // -------------------------------------------------------------------------
  // Request/response classes.
  // -------------------------------------------------------------------------

  public static class PolicyResponse {
    public final String justificationHint;

    private PolicyResponse(String justificationHint) {
      Preconditions.checkNotNull(justificationHint, "justificationHint");
      this.justificationHint = justificationHint;
    }
  }

  public static class ProjectsResponse {
    public final Set<String> projects;

    private ProjectsResponse(Set<String> projects) {
      Preconditions.checkNotNull(projects, "projects");
      this.projects = projects;
    }
  }

  public static class ProjectRolesResponse {
    public final List<String> warnings;
    public final List<ProjectRole> roles;

    private ProjectRolesResponse(
      List<ProjectRole> roleBindings,
      List<String> warnings
    ) {
      Preconditions.checkNotNull(roleBindings, "roleBindings");

      this.warnings = warnings;
      this.roles = roleBindings;
    }
  }

  public static class ProjectRolePeersResponse {
    public final Set<UserId> peers;

    private ProjectRolePeersResponse(Set<UserId> peers) {
      Preconditions.checkNotNull(peers);
      this.peers = peers;
    }
  }

  public static class SelfActivationRequest {
    public List<String> roles;
    public String justification;
  }

  public static class ActivationRequest {
    public String role;
    public String justification;
    public List<String> peers;
  }

  public static class ActivationStatusResponse {
    public final UserId beneficiary;
    public final Set<UserId> reviewers;
    public final boolean isBeneficiary;
    public final boolean isReviewer;
    public final String justification;
    public final List<ActivationStatus> items;

    private ActivationStatusResponse(
      UserId beneficiary,
      Set<UserId> reviewers,
      boolean isBeneficiary,
      boolean isReviewer,
      String justification,
      List<ActivationStatus> items
    ) {
      Preconditions.checkNotNull(beneficiary);
      Preconditions.checkNotNull(reviewers);
      Preconditions.checkNotNull(justification);
      Preconditions.checkNotNull(items);
      Preconditions.checkArgument(items.size() > 0);

      this.beneficiary = beneficiary;
      this.reviewers = reviewers;
      this.isBeneficiary = isBeneficiary;
      this.isReviewer = isReviewer;
      this.justification = justification;
      this.items = items;
    }

    private ActivationStatusResponse(
      UserId caller,
      RoleActivationService.ActivationRequest request,
      ProjectRole.Status status
    ) {
      this(
        request.beneficiary,
        request.reviewers,
        request.beneficiary.equals(caller),
        request.reviewers.contains(caller),
        request.justification,
        List.of(new ActivationStatus(
          request.id,
          request.roleBinding,
          status,
          request.startTime.getEpochSecond(),
          request.endTime.getEpochSecond())));
    }

    public static class ActivationStatus {
      public final String activationId;
      public final String projectId;
      public final RoleBinding roleBinding;
      public final ProjectRole.Status status;
      public final long startTime;
      public final long endTime;

      private ActivationStatus(
        RoleActivationService.ActivationId activationId,
        RoleBinding roleBinding,
        ProjectRole.Status status,
        long startTime,
        long endTime
      ) {
        assert startTime < endTime;

        this.activationId = activationId.toString();
        this.projectId = ProjectId.fromFullResourceName(roleBinding.fullResourceName).id;
        this.roleBinding = roleBinding;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
      }

      private ActivationStatus(RoleActivationService.Activation activation) {
        this(
          activation.id,
          activation.projectRole.roleBinding,
          activation.projectRole.status,
          activation.startTime.getEpochSecond(),
          activation.endTime.getEpochSecond());
      }
    }
  }
}
