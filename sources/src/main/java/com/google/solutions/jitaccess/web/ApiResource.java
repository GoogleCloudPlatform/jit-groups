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
import com.google.solutions.jitaccess.core.Exceptions;
import com.google.solutions.jitaccess.core.adapters.LogAdapter;
import com.google.solutions.jitaccess.core.data.*;
import com.google.solutions.jitaccess.core.services.ActivationTokenService;
import com.google.solutions.jitaccess.core.services.NotificationService;
import com.google.solutions.jitaccess.core.services.RoleActivationService;
import com.google.solutions.jitaccess.core.services.RoleDiscoveryService;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
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
  NotificationService notificationService;

  @Inject
  RuntimeEnvironment runtimeEnvironment;

  @Inject
  LogAdapter logAdapter;

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
          request.justification,
          Duration.ofMinutes(request.activationTimeout));

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
    assert this.notificationService != null;

    var minReviewers = this.roleActivationService.getOptions().minNumberOfReviewersPerActivationRequest;
    var maxReviewers = this.roleActivationService.getOptions().maxNumberOfReviewersPerActivationRequest;

    Preconditions.checkArgument(
      projectIdString != null && !projectIdString.trim().isEmpty(),
      "A projectId is required");
    Preconditions.checkArgument(request != null);
    Preconditions.checkArgument(
      request.role != null && !request.role.isEmpty(),
      "A role is required");
    Preconditions.checkArgument(
      request.peers != null && request.peers.size() >= minReviewers,
      "At least " + minReviewers + " reviewers are required");
    Preconditions.checkArgument(
      request.peers.size() <= maxReviewers,
      "The number of reviewers must not exceed " + maxReviewers);
    Preconditions.checkArgument(
      request.justification != null && request.justification.length() > 0 && request.justification.length() < 100,
      "A justification must be provided");

    Preconditions.checkState(
      this.notificationService.canSendNotifications() || this.runtimeEnvironment.isDebugModeEnabled(),
      "The multi-party approval feature is not available because the server-side configuration is incomplete");

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
        request.justification,
        Duration.ofMinutes(request.activationTimeout));

      //
      // Create an approval token and pass it to reviewers.
      //
      var activationToken = this.activationTokenService.createToken(activationRequest);
      this.notificationService.sendNotification(new RequestActivationNotification(
        activationRequest,
        activationToken.expiryTime,
        createActivationRequestUrl(uriInfo, activationToken.token)));

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
    assert this.notificationService != null;

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

      this.notificationService.sendNotification(new ActivationApprovedNotification(
        activationRequest,
        iapPrincipal.getId(),
        createActivationRequestUrl(uriInfo, activationToken)));

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
    public final int defaultActivationTimeout; // in minutes.
    public final int maxActivationTimeout;     // in minutes.

    private PolicyResponse(
      String justificationHint,
      UserId signedInUser,
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
   * Email to reviewers, requesting their approval.
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

      this.properties.put("BENEFICIARY", request.beneficiary.email);
      this.properties.put("PROJECT_ID", ProjectId.fromFullResourceName(request.roleBinding.fullResourceName).id);
      this.properties.put("ROLE", request.roleBinding.role);
      this.properties.put("START_TIME", request.startTime);
      this.properties.put("END_TIME", request.endTime);
      this.properties.put("REQUEST_EXPIRY_TIME", requestExpiryTime);
      this.properties.put("JUSTIFICATION", request.justification);
      this.properties.put("BASE_URL", new URL(activationRequestUrl, "/").toString());
      this.properties.put("ACTION_URL", activationRequestUrl.toString());
    }

    @Override
    public String getTemplateId() {
      return "RequestActivation";
    }
  }

  /**
   * Email to the beneficiary, confirming an approval.
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
      this.properties.put("BENEFICIARY", request.beneficiary.email);
      this.properties.put("PROJECT_ID", ProjectId.fromFullResourceName(request.roleBinding.fullResourceName).id);
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
    public String getTemplateId() {
      return "ActivationApproved";
    }
  }
}
