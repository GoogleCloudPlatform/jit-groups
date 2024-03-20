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

package com.google.solutions.jitaccess.web.actions;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.util.Exceptions;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.MpaActivationRequest;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.TokenSigner;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRole;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRoleActivator;
import com.google.solutions.jitaccess.core.notifications.NotificationService;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.LogEvents;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.core.UriInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RequestActivationAction extends AbstractActivationAction {
  private final @NotNull MpaProjectRoleCatalog catalog;
  private final @NotNull TokenSigner tokenSigner;

  public RequestActivationAction(
    @NotNull LogAdapter logAdapter,
    @NotNull RuntimeEnvironment runtimeEnvironment,
    @NotNull ProjectRoleActivator activator,
    @NotNull Instance<NotificationService> notificationServices,
    @NotNull MpaProjectRoleCatalog catalog,
    @NotNull TokenSigner tokenSigner
  ) {
    super(logAdapter, runtimeEnvironment, activator, notificationServices);
    this.catalog = catalog;
    this.tokenSigner = tokenSigner;
  }

  public @NotNull ResponseEntity execute(
    @NotNull IapPrincipal iapPrincipal,
    @NotNull String projectIdString,
    @NotNull RequestEntity request,
    @NotNull UriInfo uriInfo
  ) throws AccessDeniedException {
    var minReviewers = this.catalog.options().minNumberOfReviewersPerActivationRequest();
    var maxReviewers = this.catalog.options().maxNumberOfReviewersPerActivationRequest();

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

    var userContext = this.catalog.createContext(iapPrincipal.email());

    var projectId = new ProjectId(projectIdString);
    var roleBinding = new RoleBinding(projectId, request.role);

    //
    // Create an MPA activation request.
    //
    var requestedRoleBindingDuration = Duration.ofMinutes(request.activationTimeout);
    MpaActivationRequest<ProjectRole> activationRequest;

    try {
      activationRequest = this.activator.createMpaRequest(
        userContext,
        Set.of(new ProjectRole(roleBinding)),
        request.peers.stream().map(email -> new UserId(email)).collect(Collectors.toSet()),
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
        this.activator.createTokenConverter(),
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

      return new ResponseEntity(
        iapPrincipal.email(),
        activationRequest,
        ActivationStatus.ACTIVATION_PENDING);
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
        throw new AccessDeniedException("Requesting access failed, see logs for details", e);
      }
    }
  }


  public static class RequestEntity {
    public @Nullable String role;
    public String justification;
    public List<String> peers;
    public int activationTimeout; // in minutes.
  }

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
}
