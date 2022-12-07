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
import java.time.Instant;
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
    Preconditions.checkNotNull(roleDiscoveryService, "roleDiscoveryService");

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
    Preconditions.checkNotNull(roleDiscoveryService, "roleDiscoveryService");

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
    Preconditions.checkNotNull(roleDiscoveryService, "roleDiscoveryService");

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
  public ActivationStatusResponse selfActivateRoles(
    @PathParam("projectId") String projectIdString,
    SelfActivationRequest request,
    @Context SecurityContext securityContext
  ) throws AccessDeniedException {
    Preconditions.checkNotNull(roleDiscoveryService, "roleDiscoveryService");

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
            LogEvents.API_SELF_ACTIVATE_ROLE,
            String.format(
              "User %s activated role '%s' on '%s' for themselves",
              iapPrincipal.getId(),
              roleBinding.role,
              roleBinding.fullResourceName))
          .addLabel("activation_id", activation.id.toString())
          .addLabels(le -> addLabels(le, projectId))
          .addLabels(le -> addLabels(le, roleBinding))
          .addLabel("justification", request.justification)
          .write();
      }
      catch (Exception e) {
        this.logAdapter
          .newErrorEntry(
            LogEvents.API_SELF_ACTIVATE_ROLE,
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

        throw new AccessDeniedException("Activating role failed", e);
      }
    }

    assert activations.size() == roleBindings.size();

    //
    // All activations have been requested at once, so use the request time of the first.
    //
    var requestTime = activations
      .stream()
      .map(a -> a.requestTime)
      .findFirst()
      .get();

    return new ActivationStatusResponse(
      projectId,
      iapPrincipal.getId(),
      requestTime,
      true,
      false,
      request.justification,
      activations
        .stream()
        .map(a -> new ActivationStatusResponse.ActivationStatus(
          a.id.toString(),
          a.projectRole.roleBinding,
          a.projectRole.status,
          a.expiry.getEpochSecond()
        ))
        .collect(Collectors.toList()));
  }

  /**
   * Request approval to activate one or more project roles. Only allowed for MPA-eligible roles.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects/{projectId}/roles/request")
  public ActivationStatusResponse requestRole(
    @PathParam("projectId") String projectIdString,
    ActivationRequest request,
    @Context SecurityContext securityContext
  ) throws AccessDeniedException {
    Preconditions.checkNotNull(roleDiscoveryService, "roleDiscoveryService");
    Preconditions.checkNotNull(activationTokenService, "activationTokenService");

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
      request.justification.length() > 0 && request.justification.length() < 100,
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
      var approvalToken = this.activationTokenService.createToken(activationRequest);

      // TODO: Send token to peers.
      System.out.println("TOKEN: " + approvalToken);


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

      throw new AccessDeniedException("Activating role failed", e);
    }
  }

  // -------------------------------------------------------------------------
  // Logging helper methods.
  // -------------------------------------------------------------------------

  private static LogAdapter.LogEntry addLabels(
    LogAdapter.LogEntry entry,
    RoleActivationService.ActivationRequest request
  ) {
    return entry
      .addLabel("activation_id", request.id.toString())
      .addLabel("justification", request.justification)
      .addLabel("reviewers",  String.join(", ", request.reviewers.toString()))
      .addLabels(e -> addLabels(e, request.roleBinding)); // TODO: add start/end
  }

  private static LogAdapter.LogEntry addLabels(
    LogAdapter.LogEntry entry,
    RoleBinding roleBinding
  ) {
    return entry
      .addLabel("role", roleBinding.role)
      .addLabel("resource", roleBinding.fullResourceName);
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
    public final String projectId;
    public final String beneficiary;
    public final long requestTime;
    public final boolean isBeneficiary;
    public final boolean isReviewer;
    public final String justification;
    public final List<ActivationStatus> items;

    private ActivationStatusResponse(
      ProjectId projectId,
      UserId beneficiary,
      Instant requestTime,
      boolean isBeneficiary,
      boolean isReviewer,
      String justification,
      List<ActivationStatus> items
    ) {
      Preconditions.checkNotNull(projectId);
      Preconditions.checkNotNull(beneficiary);
      Preconditions.checkNotNull(requestTime);
      Preconditions.checkNotNull(justification);
      Preconditions.checkNotNull(items);
      Preconditions.checkArgument(items.size() > 0);

      this.projectId = projectId.id;
      this.beneficiary = beneficiary.email;
      this.requestTime = requestTime.getEpochSecond();
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
        ProjectId.fromFullResourceName(request.roleBinding.fullResourceName),
        request.beneficiary,
        request.creationTime,
        request.beneficiary.equals(caller),
        request.reviewers.contains(caller),
        request.justification,
        List.of(new ActivationStatus(
          request.id.toString(),
          request.roleBinding,
          status,
          0 // TODO: pass expiry
        )));
    }

    public static class ActivationStatus {
      public final String activationId;
      public final RoleBinding roleBinding;
      public final ProjectRole.Status status;
      public final long expiry;

      public ActivationStatus(String activationId, RoleBinding roleBinding, ProjectRole.Status status, long expiry) {
        this.activationId = activationId;
        this.roleBinding = roleBinding;
        this.status = status;
        this.expiry = expiry;
      }
    }
  }
}
