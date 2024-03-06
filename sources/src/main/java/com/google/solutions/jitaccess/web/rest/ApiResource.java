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
import com.google.solutions.jitaccess.web.LogEvents;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import com.google.solutions.jitaccess.web.TokenObfuscator;
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

  private @NotNull URL createActivationRequestUrl(
    @NotNull UriInfo uriInfo,
    @NotNull ProjectId projectId,
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
    // NB. We include the project ID to force the approver into
    // the right scope. This isn't strictly necessary, but it
    // improves user experience.
    //
    return this.runtimeEnvironment
      .createAbsoluteUriBuilder(uriInfo)
      .path("/")
      .queryParam("activation", TokenObfuscator.encode(activationToken))
      .queryParam("projectId", projectId.id())
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
  public @NotNull ActivationStatusResponse selfApproveActivation(
    @PathParam("projectId") @Nullable String projectIdString,
    @NotNull SelfActivationRequest request,
    @Context @NotNull SecurityContext securityContext
  ) throws AccessDeniedException {
    Preconditions.checkNotNull(this.mpaCatalog, "iamPolicyCatalog");

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

    var iapPrincipal = (IapPrincipal) securityContext.getUserPrincipal();
    var userContext = this.mpaCatalog.createContext(iapPrincipal.email());

    var projectId = new ProjectId(projectIdString);

    //
    // Create a JIT activation request.
    //
    var requestedRoleBindingDuration = Duration.ofMinutes(request.activationTimeout);
    var activationRequest = this.projectRoleActivator.createJitRequest(
      userContext,
      request.roles
        .stream()
        .map(r -> new com.google.solutions.jitaccess.core.catalog.project.ProjectRole(new RoleBinding(projectId.getFullResourceName(), r)))
        .collect(Collectors.toSet()),
      request.justification,
      Instant.now().truncatedTo(ChronoUnit.SECONDS),
      requestedRoleBindingDuration);

    try {
      //
      // Activate the request.
      //
      var activation = this.projectRoleActivator.activate(
        userContext,
        activationRequest);

      assert activation != null;

      //
      // Notify listeners, if any.
      //
      for (var service : this.notificationServices) {
        service.sendNotification(new ActivationSelfApprovedNotification(projectId, activation));
      }

      //
      // Leave an audit log trail.
      //
      this.logAdapter
        .newInfoEntry(
          LogEvents.API_ACTIVATE_ROLE,
          String.format(
            "User %s activated roles %s on '%s' for themselves for %d minutes",
            iapPrincipal.email(),
            activationRequest.entitlements().stream()
              .map(ent -> String.format("'%s'", ent.roleBinding().role()))
              .collect(Collectors.joining(", ")),
            projectId.getFullResourceName(),
            requestedRoleBindingDuration.toMinutes()))
        .addLabels(le -> addLabels(le, activationRequest))
        .write();

      return new ActivationStatusResponse(
        iapPrincipal.email(),
        activation.request(),
        Entitlement.Status.ACTIVE);
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_ACTIVATE_ROLE,
          String.format(
            "User %s failed to activate roles %s on '%s' for themselves for %d minutes: %s",
            iapPrincipal.email(),
            activationRequest.entitlements().stream()
              .map(ent -> String.format("'%s'", ent.roleBinding().role()))
              .collect(Collectors.joining(", ")),
            projectId.getFullResourceName(),
            requestedRoleBindingDuration.toMinutes(),
            Exceptions.getFullMessage(e)))
        .addLabels(le -> addLabels(le, projectId))
        .addLabels(le -> addLabels(le, activationRequest))
        .addLabels(le -> addLabels(le, e))
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
   * Request approval to activate one or more project roles. Only allowed for MPA-eligible roles.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects/{projectId}/roles/request")
  public @NotNull ActivationStatusResponse requestActivation(
    @PathParam("projectId") @Nullable String projectIdString,
    @NotNull ActivationRequest request,
    @Context @NotNull SecurityContext securityContext,
    @Context @NotNull UriInfo uriInfo
  ) throws AccessDeniedException {
    Preconditions.checkNotNull(this.mpaCatalog, "iamPolicyCatalog");
    assert this.tokenSigner != null;
    assert this.notificationServices != null;

    var minReviewers = this.mpaCatalog.options().minNumberOfReviewersPerActivationRequest();
    var maxReviewers = this.mpaCatalog.options().maxNumberOfReviewersPerActivationRequest();

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

    var iapPrincipal = (IapPrincipal) securityContext.getUserPrincipal();
    var userContext = this.mpaCatalog.createContext(iapPrincipal.email());

    var projectId = new ProjectId(projectIdString);
    var roleBinding = new RoleBinding(projectId, request.role);

    //
    // Create an MPA activation request.
    //
    var requestedRoleBindingDuration = Duration.ofMinutes(request.activationTimeout);
    MpaActivationRequest<com.google.solutions.jitaccess.core.catalog.project.ProjectRole> activationRequest;

    try {
      activationRequest = this.projectRoleActivator.createMpaRequest(
        userContext,
        Set.of(new com.google.solutions.jitaccess.core.catalog.project.ProjectRole(roleBinding)),
        request.peers.stream().map(email -> new UserEmail(email)).collect(Collectors.toSet()),
        request.justification,
        Instant.now().truncatedTo(ChronoUnit.SECONDS),
        requestedRoleBindingDuration);
    }
    catch (AccessException | IOException e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_ACTIVATE_ROLE,
          String.format(
            "Received invalid activation request from user '%s' for role '%s' on '%s': %s",
            iapPrincipal.email(),
            roleBinding,
            projectId.getFullResourceName(),
            Exceptions.getFullMessage(e)))
        .addLabels(le -> addLabels(le, projectId))
        .addLabels(le -> addLabels(le, e))
        .write();

      if (e instanceof AccessDeniedException) {
        throw (AccessDeniedException)e.fillInStackTrace();
      }
      else {
        throw new AccessDeniedException("Invalid request", e);
      }
    }

    try
    {
      //
      // Create an activation token and pass it to reviewers.
      //
      // An activation token is a signed activation request that is passed to reviewers.
      // It contains all information necessary to review (and approve) the activation
      // request.
      //
      // We must ensure that the information that reviewers see (and base their approval
      // on) is authentic. Therefore, activation tokens are signed, using the service account
      // as signing authority.
      //
      // Although activation tokens are JWTs, and might look like credentials, they aren't
      // credentials: They don't grant access to any information, and possession alone is
      // insufficient to approve an activation request.
      //

      var activationToken = this.tokenSigner.sign(
        this.projectRoleActivator.createTokenConverter(),
        activationRequest);

      //
      // Notify reviewers, listeners.
      //
      for (var service : this.notificationServices) {
        var activationRequestUrl = createActivationRequestUrl(
          uriInfo,
          projectId,
          activationToken.token());
        service.sendNotification(new RequestActivationNotification(
          projectId,
          activationRequest,
          activationToken.expiryTime(),
          activationRequestUrl));
      }

      //
      // Leave an audit log trail.
      //
      this.logAdapter
        .newInfoEntry(
          LogEvents.API_REQUEST_ROLE,
          String.format(
            "User %s requested role '%s' on '%s' for %d minutes",
            iapPrincipal.email(),
            roleBinding.role(),
            roleBinding.fullResourceName(),
            requestedRoleBindingDuration.toMinutes()))
        .addLabels(le -> addLabels(le, projectId))
        .addLabels(le -> addLabels(le, activationRequest))
        .write();

      return new ActivationStatusResponse(
        iapPrincipal.email(),
        activationRequest,
        Entitlement.Status.ACTIVATION_PENDING);
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_REQUEST_ROLE,
          String.format(
            "User %s failed to request role '%s' on '%s' for %d minutes: %s",
            iapPrincipal.email(),
            roleBinding.role(),
            roleBinding.fullResourceName(),
            requestedRoleBindingDuration.toMinutes(),
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
  public @NotNull ActivationStatusResponse getActivationRequest(
    @QueryParam("activation") @Nullable String obfuscatedActivationToken,
    @Context @NotNull SecurityContext securityContext
  ) throws AccessException {
    assert this.tokenSigner != null;

    Preconditions.checkArgument(
      obfuscatedActivationToken != null && !obfuscatedActivationToken.trim().isEmpty(),
      "An activation token is required");

    var activationToken = TokenObfuscator.decode(obfuscatedActivationToken);
    var iapPrincipal = (IapPrincipal) securityContext.getUserPrincipal();

    try {
      var activationRequest = this.tokenSigner.verify(
        this.projectRoleActivator.createTokenConverter(),
        activationToken);

      if (!activationRequest.requestingUser().equals(iapPrincipal.email()) &&
          !activationRequest.reviewers().contains(iapPrincipal.email())) {
        throw new AccessDeniedException("The calling user is not authorized to access this approval request");
      }

      return new ActivationStatusResponse(
        iapPrincipal.email(),
        activationRequest,
        Entitlement.Status.ACTIVATION_PENDING);
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
  public @NotNull ActivationStatusResponse approveActivationRequest(
    @QueryParam("activation") @Nullable String obfuscatedActivationToken,
    @Context @NotNull SecurityContext securityContext,
    @Context @NotNull UriInfo uriInfo
  ) throws AccessException {
    assert this.tokenSigner != null;
    assert this.mpaCatalog != null;
    assert this.notificationServices != null;

    Preconditions.checkArgument(
      obfuscatedActivationToken != null && !obfuscatedActivationToken.trim().isEmpty(),
      "An activation token is required");

    var activationToken = TokenObfuscator.decode(obfuscatedActivationToken);
    var iapPrincipal = (IapPrincipal) securityContext.getUserPrincipal();
    var userContext = this.mpaCatalog.createContext(iapPrincipal.email());

    MpaActivationRequest<com.google.solutions.jitaccess.core.catalog.project.ProjectRole> activationRequest;
    try {
      activationRequest = this.tokenSigner.verify(
        this.projectRoleActivator.createTokenConverter(),
        activationToken);
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

    assert activationRequest.entitlements().size() == 1;
    var roleBinding = activationRequest
      .entitlements()
      .stream()
      .findFirst()
      .get()
      .roleBinding();

    try {
      var activation = this.projectRoleActivator.approve(userContext, activationRequest);

      assert activation != null;

      //
      // Notify listeners.
      //
      var projectId = ProjectId.parse(roleBinding.fullResourceName());
      for (var service : this.notificationServices) {
        service.sendNotification(new ActivationApprovedNotification(
          projectId,
          activation,
          iapPrincipal.email(),
          createActivationRequestUrl(uriInfo, projectId, activationToken)));
      }

      //
      // Leave an audit trail.
      //
      this.logAdapter
        .newInfoEntry(
          LogEvents.API_ACTIVATE_ROLE,
          String.format(
            "User %s approved role '%s' on '%s' for %s",
            iapPrincipal.email(),
            roleBinding.role(),
            roleBinding.fullResourceName(),
            activationRequest.requestingUser()))
        .addLabels(le -> addLabels(le, activationRequest))
        .write();

      return new ActivationStatusResponse(
        iapPrincipal.email(),
        activationRequest,
        Entitlement.Status.ACTIVE);
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_ACTIVATE_ROLE,
          String.format(
            "User %s failed to activate role '%s' on '%s' for %s: %s",
            iapPrincipal.email(),
            roleBinding.role(),
            roleBinding.fullResourceName(),
            activationRequest.requestingUser(),
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
  // Request/response classes.
  // -------------------------------------------------------------------------

  public static class SelfActivationRequest {
    public List<String> roles;
    public String justification;
    public int activationTimeout; // in minutes.
  }

  public static class ActivationRequest {
    public @Nullable String role;
    public String justification;
    public List<String> peers;
    public int activationTimeout; // in minutes.
  }

  public static class ActivationStatusResponse {
    public final UserEmail beneficiary;
    public final Collection<UserEmail> reviewers;
    public final boolean isBeneficiary;
    public final boolean isReviewer;
    public final String justification;
    public final @NotNull List<ActivationStatus> items;

    private ActivationStatusResponse(
      UserEmail caller,
      com.google.solutions.jitaccess.core.catalog.@NotNull ActivationRequest<com.google.solutions.jitaccess.core.catalog.project.ProjectRole> request,
      Entitlement.Status status
    ) {
      Preconditions.checkNotNull(request);

      this.beneficiary = request.requestingUser();
      this.isBeneficiary = request.requestingUser().equals(caller);
      this.justification = request.justification();
      this.items = request
        .entitlements()
        .stream()
        .map(ent -> new ActivationStatusResponse.ActivationStatus(
          request.id(),
          ent.roleBinding(),
          status,
          request.startTime(),
          request.endTime()))
        .collect(Collectors.toList());

      if (request instanceof MpaActivationRequest<com.google.solutions.jitaccess.core.catalog.project.ProjectRole> mpaRequest) {
        this.reviewers = mpaRequest.reviewers();
        this.isReviewer = mpaRequest.reviewers().contains(caller);
      }
      else {
        this.reviewers = Set.of();
        this.isReviewer = false;
      }
    }

    public static class ActivationStatus {
      public final String activationId;
      public final String projectId;
      public final @NotNull RoleBinding roleBinding;
      public final Entitlement.Status status;
      public final long startTime;
      public final long endTime;

      private ActivationStatus(
        @NotNull ActivationId activationId,
        @NotNull RoleBinding roleBinding,
        Entitlement.Status status,
        @NotNull Instant startTime,
        @NotNull Instant endTime
      ) {
        assert endTime.isAfter(startTime);

        this.activationId = activationId.toString();
        this.projectId = ProjectId.parse(roleBinding.fullResourceName()).id();
        this.roleBinding = roleBinding;
        this.status = status;
        this.startTime = startTime.getEpochSecond();
        this.endTime = endTime.getEpochSecond();
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
  // TODO: remove Logging helper methods.
  // -------------------------------------------------------------------------

  protected static <T extends EntitlementId> @NotNull LogAdapter.LogEntry addLabels(
    @NotNull LogAdapter.LogEntry entry,
    @NotNull com.google.solutions.jitaccess.core.catalog.ActivationRequest<T> request
  ) {
    entry
      .addLabel("activation_id", request.id().toString())
      .addLabel("activation_start", request.startTime().atOffset(ZoneOffset.UTC).toString())
      .addLabel("activation_end", request.endTime().atOffset(ZoneOffset.UTC).toString())
      .addLabel("justification", request.justification())
      .addLabels(e -> addLabels(e, request.entitlements()));

    if (request instanceof MpaActivationRequest<T> mpaRequest) {
      entry.addLabel("reviewers", mpaRequest
        .reviewers()
        .stream()
        .map(u -> u.email)
        .collect(Collectors.joining(", ")));
    }

    return entry;
  }

  protected static LogAdapter.LogEntry addLabels(
    @NotNull LogAdapter.LogEntry entry,
    @NotNull RoleBinding roleBinding
  ) {
    return entry
      .addLabel("role", roleBinding.role())
      .addLabel("resource", roleBinding.fullResourceName())
      .addLabel("project_id", ProjectId.parse(roleBinding.fullResourceName()).id());
  }

  protected static LogAdapter.LogEntry addLabels(
    @NotNull LogAdapter.LogEntry entry,
    @NotNull Collection<? extends EntitlementId> entitlements
  ) {
    return entry.addLabel(
      "entitlements",
      entitlements.stream().map(s -> s.toString()).collect(Collectors.joining(", ")));
  }

  protected static LogAdapter.LogEntry addLabels(
    @NotNull LogAdapter.LogEntry entry,
    @NotNull Exception exception
  ) {
    return entry.addLabel("error", exception.getClass().getSimpleName());
  }

  protected static LogAdapter.LogEntry addLabels(
    @NotNull LogAdapter.LogEntry entry,
    @NotNull ProjectId project
  ) {
    return entry.addLabel("project", project.id());
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
