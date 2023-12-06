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
import com.google.solutions.jitaccess.core.ApplicationVersion;
import com.google.solutions.jitaccess.core.Exceptions;
import com.google.solutions.jitaccess.core.adapters.LogAdapter;
import com.google.solutions.jitaccess.core.data.UserId;
import com.google.solutions.jitaccess.core.data.ProjectRole;
import com.google.solutions.jitaccess.core.data.ProjectId;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserPrincipal;
import com.google.solutions.jitaccess.core.services.ActivationTokenService;
import com.google.solutions.jitaccess.core.services.NotificationService;
import com.google.solutions.jitaccess.core.services.RoleActivationService;
import com.google.solutions.jitaccess.core.services.RoleDiscoveryService;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
  Instance<NotificationService> notificationServices;

  @Inject
  RuntimeEnvironment runtimeEnvironment;

  @Inject
  LogAdapter logAdapter;

  @Inject
  Options options;

  private URL createActivationRequestUrl(
    UriInfo uriInfo,
    String activationToken
  ) throws MalformedURLException {
    Preconditions.checkNotNull(uriInfo);
    Preconditions.checkNotNull(activationToken);

    //
    // NB. Embedding the token verbatim can trigger overzealous phishing filters
    // to assume that we're embedding an access token (or some other form of
    // credential in the URL). But activation tokens aren't credentials, they
    // don't grant access to anything and the embedded information isn't
    // confidential.
    //
    // Obfuscate the token to avoid such false-flagging.
    //
    return this.runtimeEnvironment
      .createAbsoluteUriBuilder(uriInfo)
      .path("/")
      .queryParam("activation", TokenObfuscator.encode(activationToken))
      .build()
      .toURL();
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
   * Get information about configured policies.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("policy")
  public PolicyResponse getPolicy(
    @Context SecurityContext securityContext
  ) {
    var iapPrincipal = (UserPrincipal) securityContext.getUserPrincipal();

    var options = this.roleActivationService.getOptions();
    return new PolicyResponse(
      options.justificationHint,
      iapPrincipal.getId(),
      ApplicationVersion.VERSION_STRING,
      (int)options.maxActivationTimeout.toMinutes(),
      Math.min(60, (int)options.maxActivationTimeout.toMinutes()));
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

      return new ProjectsResponse(projects
        .stream().map(p -> p.id)
        .collect(Collectors.toSet()));
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_LIST_ROLES,
          String.format("Listing available projects failed: %s", Exceptions.getFullMessage(e)))
        .write();

      throw new AccessDeniedException("Listing available projects failed, see logs for details");
    }
  }

  /**
   * List roles (within a project) that the user can activate.
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
          String.format("Listing project roles failed: %s", Exceptions.getFullMessage(e)))
        .addLabels(le -> addLabels(le, e))
        .addLabels(le -> addLabels(le, projectId))
        .write();

      throw new AccessDeniedException("Listing project roles failed, see logs for details");
    }
  }

  /**
   * List peers that are qualified to approve the activation of a role.
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
      var peers = this.roleDiscoveryService.listEligibleUsersForProjectRole(
        iapPrincipal.getId(),
        roleBinding);

      assert !peers.contains(iapPrincipal.getId());

      return new ProjectRolePeersResponse(peers);
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_LIST_PEERS,
          String.format("Listing peers failed: %s", Exceptions.getFullMessage(e)))
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
      "You must provide a projectId");
    Preconditions.checkArgument(
      request != null && request.roles != null && request.roles.size() > 0,
      "Specify one or more roles to activate");
    Preconditions.checkArgument(
      request != null && request.roles != null && request.roles.size() <= this.options.maxNumberOfJitRolesPerSelfApproval,
      String.format(
        "The number of roles exceeds the allowed maximum of %d",
        this.options.maxNumberOfJitRolesPerSelfApproval));
    Preconditions.checkArgument(
      request.justification != null && request.justification.trim().length() > 0,
      "Provide a justification");
    Preconditions.checkArgument(
      request.justification != null && request.justification.length() < 100,
      "The justification is too long");

    var iapPrincipal = (UserPrincipal) securityContext.getUserPrincipal();
    var projectId = new ProjectId(projectIdString);

    //
    // NB. The input list of roles might contain duplicates, therefore reduce to a set.
    //
    var roleBindings = request.roles
      .stream()
      .map(r -> new RoleBinding(projectId.getFullResourceName(), r))
      .collect(Collectors.toSet());

    // Get the requested role binding duration in minutes
    var requestedRoleBindingDuration = Duration.ofMinutes(request.activationTimeout).toMinutes();

    var activations = new ArrayList<RoleActivationService.Activation>();
    for (var roleBinding : roleBindings) {
      try {
        var activation = this.roleActivationService.activateProjectRoleForSelf(
          iapPrincipal.getId(),
          roleBinding,
          request.justification,
          Duration.ofMinutes(request.activationTimeout));

        assert activation != null;
        activations.add(activation);

        for (var service : this.notificationServices) {
          service.sendNotification(new ActivationSelfApprovedNotification(
            activation,
            iapPrincipal.getId(),
            request.justification));
        }

        this.logAdapter
          .newInfoEntry(
            LogEvents.API_ACTIVATE_ROLE,
            String.format(
              "User %s activated role '%s' on '%s' for themselves for %d minutes",
              iapPrincipal.getId(),
              roleBinding.role,
              roleBinding.fullResourceName,
              requestedRoleBindingDuration))
          .addLabels(le -> addLabels(le, activation))
          .addLabel("justification", request.justification)
          .write();
      }
      catch (Exception e) {
        this.logAdapter
          .newErrorEntry(
            LogEvents.API_ACTIVATE_ROLE,
            String.format(
              "User %s failed to activate role '%s' on '%s' for themselves for %d minutes: %s",
              iapPrincipal.getId(),
              roleBinding.role,
              roleBinding.fullResourceName,
              requestedRoleBindingDuration,
              Exceptions.getFullMessage(e)))
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
    assert this.activationTokenService != null;
    assert this.notificationServices != null;

    var minReviewers = this.roleActivationService.getOptions().minNumberOfReviewersPerActivationRequest;
    var maxReviewers = this.roleActivationService.getOptions().maxNumberOfReviewersPerActivationRequest;

    Preconditions.checkArgument(
      projectIdString != null && !projectIdString.trim().isEmpty(),
      "You must provide a projectId");
    Preconditions.checkArgument(request != null);
    Preconditions.checkArgument(
      request.role != null && !request.role.isEmpty(),
      "Specify a role to activate");
    Preconditions.checkArgument(
      request.peers != null && request.peers.size() >= minReviewers,
      String.format("You must select at least %d reviewers", minReviewers));
    Preconditions.checkArgument(
      request.peers.size() <= maxReviewers,
      String.format("The number of reviewers exceeds the allowed maximum of %d", maxReviewers));
    Preconditions.checkArgument(
      request.justification != null && request.justification.trim().length() > 0,
      "Provide a justification");
    Preconditions.checkArgument(
      request.justification != null && request.justification.length() < 100,
      "The justification is too long");

    //
    // For MPA to work, we need at least one functional notification service.
    //
    Preconditions.checkState(
      this.notificationServices
        .stream()
        .anyMatch(s -> s.canSendNotifications()) ||
        this.runtimeEnvironment.isDebugModeEnabled(),
      "The multi-party approval feature is not available because the server-side configuration is incomplete");

    var iapPrincipal = (UserPrincipal) securityContext.getUserPrincipal();
    var projectId = new ProjectId(projectIdString);
    var roleBinding = new RoleBinding(projectId, request.role);

    // Get the requested role binding duration in minutes
    var requestedRoleBindingDuration = Duration.ofMinutes(request.activationTimeout).toMinutes();

    try
    {
      //
      // Validate request.
      //
      var activationRequest = this.roleActivationService.createActivationRequestForPeer(
        iapPrincipal.getId(),
        request.peers.stream().map(email -> new UserId(email)).collect(Collectors.toSet()),
        roleBinding,
        request.justification,
        Duration.ofMinutes(request.activationTimeout));

      //
      // Create an approval token and pass it to reviewers.
      //
      var activationToken = this.activationTokenService.createToken(activationRequest);

      for (var service : this.notificationServices) {
        var activationRequestUrl = createActivationRequestUrl(uriInfo, activationToken.token);
        service.sendNotification(new RequestActivationNotification(
          activationRequest,
          activationToken.expiryTime,
          activationRequestUrl));
      }

      this.logAdapter
        .newInfoEntry(
          LogEvents.API_REQUEST_ROLE,
          String.format(
            "User %s requested role '%s' on '%s' for %d minutes",
            iapPrincipal.getId(),
            roleBinding.role,
            roleBinding.fullResourceName,
            requestedRoleBindingDuration
            ))
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
            "User %s failed to request role '%s' on '%s' for %d minutes: %s",
            iapPrincipal.getId(),
            roleBinding.role,
            roleBinding.fullResourceName,
            requestedRoleBindingDuration,
            Exceptions.getFullMessage(e)))
        .addLabels(le -> addLabels(le, projectId))
        .addLabels(le -> addLabels(le, roleBinding))
        .addLabels(le -> addLabels(le, e))
        .addLabel("justification", request.justification)
        .write();

      if (e instanceof AccessDeniedException) {
        throw (AccessDeniedException)e.fillInStackTrace();
      }
      else {
        throw new AccessDeniedException("Requesting access failed", e);
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
    @QueryParam("activation") String obfuscatedActivationToken,
    @Context SecurityContext securityContext
  ) throws AccessException {
    assert this.activationTokenService != null;

    Preconditions.checkArgument(
      obfuscatedActivationToken != null && !obfuscatedActivationToken.trim().isEmpty(),
      "An activation token is required");

    var activationToken = TokenObfuscator.decode(obfuscatedActivationToken);
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
        ProjectRole.Status.ACTIVATION_PENDING); // TODO(later): Could check if's been activated already.
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_GET_REQUEST,
          String.format("Accessing the activation request failed: %s", Exceptions.getFullMessage(e)))
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
    @QueryParam("activation") String obfuscatedActivationToken,
    @Context SecurityContext securityContext,
    @Context UriInfo uriInfo
  ) throws AccessException {
    assert this.activationTokenService != null;
    assert this.roleActivationService != null;
    assert this.notificationServices != null;

    Preconditions.checkArgument(
      obfuscatedActivationToken != null && !obfuscatedActivationToken.trim().isEmpty(),
      "An activation token is required");

    var activationToken = TokenObfuscator.decode(obfuscatedActivationToken);
    var iapPrincipal = (UserPrincipal) securityContext.getUserPrincipal();

    RoleActivationService.ActivationRequest activationRequest;
    try {
      activationRequest = this.activationTokenService.verifyToken(activationToken);
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_ACTIVATE_ROLE,
          String.format("Accessing the activation request failed: %s", Exceptions.getFullMessage(e)))
        .addLabels(le -> addLabels(le, e))
        .write();

      throw new AccessDeniedException("Accessing the activation request failed");
    }

    try {
      var activation = this.roleActivationService.activateProjectRoleForPeer(
        iapPrincipal.getId(),
        activationRequest);

      assert activation != null;

      for (var service : this.notificationServices) {
        service.sendNotification(new ActivationApprovedNotification(
          activationRequest,
          iapPrincipal.getId(),
          createActivationRequestUrl(uriInfo, activationToken)));
      }

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
        activationRequest.beneficiary,
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
            Exceptions.getFullMessage(e)))
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
    public final UserId signedInUser;
    public String applicationVersion;
    public final int defaultActivationTimeout; // in minutes.
    public final int maxActivationTimeout;     // in minutes.

    private PolicyResponse(
      String justificationHint,
      UserId signedInUser,
      String applicationVersion,
      int maxActivationTimeoutInMinutes,
      int defaultActivationTimeoutInMinutes
    ) {
      Preconditions.checkNotNull(justificationHint, "justificationHint");
      Preconditions.checkNotNull(signedInUser, "signedInUser");
      Preconditions.checkArgument(defaultActivationTimeoutInMinutes > 0, "defaultActivationTimeoutInMinutes");
      Preconditions.checkArgument(maxActivationTimeoutInMinutes > 0, "maxActivationTimeoutInMinutes");
      Preconditions.checkArgument(maxActivationTimeoutInMinutes >= defaultActivationTimeoutInMinutes, "maxActivationTimeoutInMinutes");

      this.justificationHint = justificationHint;
      this.signedInUser = signedInUser;
      this.applicationVersion = applicationVersion;
      this.defaultActivationTimeout = defaultActivationTimeoutInMinutes;
      this.maxActivationTimeout = maxActivationTimeoutInMinutes;
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
    public final Set<String> warnings;
    public final List<ProjectRole> roles;

    private ProjectRolesResponse(
      List<ProjectRole> roleBindings,
      Set<String> warnings
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
    public int activationTimeout; // in minutes.
  }

  public static class ActivationRequest {
    public String role;
    public String justification;
    public List<String> peers;
    public int activationTimeout; // in minutes.
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

  // -------------------------------------------------------------------------
  // Notifications.
  // -------------------------------------------------------------------------

  /**
   * Notification indicating that a multi-party approval request has been made
   * and is pending approval.
   */
  public class RequestActivationNotification extends NotificationService.Notification
  {
    protected RequestActivationNotification(
      RoleActivationService.ActivationRequest request,
      Instant requestExpiryTime,
      URL activationRequestUrl) throws MalformedURLException
    {
      super(
        request.reviewers,
        List.of(request.beneficiary),
        String.format(
          "%s requests access to project %s",
          request.beneficiary,
          ProjectId.fromFullResourceName(request.roleBinding.fullResourceName).id));

      this.properties.put("BENEFICIARY", request.beneficiary);
      this.properties.put("REVIEWERS", request.reviewers);
      this.properties.put("PROJECT_ID", ProjectId.fromFullResourceName(request.roleBinding.fullResourceName));
      this.properties.put("ROLE", request.roleBinding.role);
      this.properties.put("START_TIME", request.startTime);
      this.properties.put("END_TIME", request.endTime);
      this.properties.put("REQUEST_EXPIRY_TIME", requestExpiryTime);
      this.properties.put("JUSTIFICATION", request.justification);
      this.properties.put("BASE_URL", new URL(activationRequestUrl, "/").toString());
      this.properties.put("ACTION_URL", activationRequestUrl.toString());
    }

    @Override
    public String getType() {
      return "RequestActivation";
    }
  }

  /**
   * Notification indicating that a multi-party approval was granted.
   */
  public class ActivationApprovedNotification extends NotificationService.Notification {
    protected ActivationApprovedNotification(
      RoleActivationService.ActivationRequest request,
      UserId approver,
      URL activationRequestUrl) throws MalformedURLException
    {
      super(
        List.of(request.beneficiary),
        request.reviewers, // Move reviewers to CC.
        String.format(
          "%s requests access to project %s",
          request.beneficiary,
          ProjectId.fromFullResourceName(request.roleBinding.fullResourceName).id));

      this.properties.put("APPROVER", approver.email);
      this.properties.put("BENEFICIARY", request.beneficiary);
      this.properties.put("REVIEWERS", request.reviewers);
      this.properties.put("PROJECT_ID", ProjectId.fromFullResourceName(request.roleBinding.fullResourceName));
      this.properties.put("ROLE", request.roleBinding.role);
      this.properties.put("START_TIME", request.startTime);
      this.properties.put("END_TIME", request.endTime);
      this.properties.put("JUSTIFICATION", request.justification);
      this.properties.put("BASE_URL", new URL(activationRequestUrl, "/").toString());
    }

    @Override
    protected boolean isReply() {
      return true;
    }

    @Override
    public String getType() {
      return "ActivationApproved";
    }
  }

  /**
   * Notification indicating that a self-approval was performed.
   */
  public class ActivationSelfApprovedNotification extends NotificationService.Notification {
    protected ActivationSelfApprovedNotification(
      RoleActivationService.Activation activation,
      UserId beneficiary,
      String justification)
    {
      super(
        List.of(beneficiary),
        List.of(),
        String.format(
          "Activated role '%s' on '%s'",
          activation.projectRole.roleBinding,
          activation.projectRole.getProjectId()));

      this.properties.put("BENEFICIARY", beneficiary);
      this.properties.put("PROJECT_ID", activation.projectRole.getProjectId());
      this.properties.put("ROLE", activation.projectRole.roleBinding.role);
      this.properties.put("START_TIME", activation.startTime);
      this.properties.put("END_TIME", activation.endTime);
      this.properties.put("JUSTIFICATION", justification);
    }

    @Override
    protected boolean isReply() {
      return true;
    }

    @Override
    public String getType() {
      return "ActivationSelfApproved";
    }
  }

  // -------------------------------------------------------------------------
  // Options.
  // -------------------------------------------------------------------------

  public static class Options {
    public final int maxNumberOfJitRolesPerSelfApproval;

    public Options(
      int maxNumberOfJitRolesPerSelfApproval
    ) {
      Preconditions.checkArgument(
        maxNumberOfJitRolesPerSelfApproval > 0,
        "The maximum number of JIT roles per self-approval must exceed 1");

      this.maxNumberOfJitRolesPerSelfApproval = maxNumberOfJitRolesPerSelfApproval;
    }
  }
}
