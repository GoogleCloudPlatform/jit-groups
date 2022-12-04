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
import com.google.solutions.jitaccess.core.data.ProjectId;
import com.google.solutions.jitaccess.core.data.ProjectRole;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserPrincipal;
import com.google.solutions.jitaccess.core.services.RoleActivationService;
import com.google.solutions.jitaccess.core.services.RoleDiscoveryService;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
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
  public PolicyResponseEntity getPolicy() {
    return new PolicyResponseEntity(
      this.roleActivationService.getOptions().justificationHint
    );
  }

  /**
   * List projects that the calling user can access.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects")
  public ProjectsResponseEntity listProjects(
    @Context SecurityContext securityContext
  ) throws AccessException {
    Preconditions.checkNotNull(roleDiscoveryService, "roleDiscoveryService");

    var iapPrincipal = (UserPrincipal) securityContext.getUserPrincipal();

    try {
      var projects = this.roleDiscoveryService.listAvailableProjects(iapPrincipal.getId());

      return new ProjectsResponseEntity(
        projects.stream().map(p -> p.id).collect(Collectors.toSet()));
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_LIST_ELIGIBLE_ROLES,
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
  public ProjectRolesResponseEntity listEligibleRoleBindings(
    @PathParam("projectId") String projectId,
    @Context SecurityContext securityContext
  ) throws AccessException {
    Preconditions.checkNotNull(roleDiscoveryService, "roleDiscoveryService");

    Preconditions.checkArgument(
      projectId != null && !projectId.trim().isEmpty(),
      "A projectId is required");
    Preconditions.checkArgument(!projectId.trim().isEmpty(), "projectId must be provided");

    var iapPrincipal = (UserPrincipal) securityContext.getUserPrincipal();

    try {
      var bindings = this.roleDiscoveryService.listEligibleProjectRoles(
        iapPrincipal.getId(),
        new ProjectId(projectId));

      return new ProjectRolesResponseEntity(
        bindings.getItems(),
        bindings.getWarnings());
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_LIST_ELIGIBLE_ROLES,
          String.format("Listing project roles failed: %s", e.getMessage()))
        .write();

      throw new AccessDeniedException("Listing project roles failed, see logs for details");
    }
  }

  /**
   * Self-activate one or more project roles.
   * This is only allowed for JIT-eligible roles.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("projects/{projectId}/roles/self-activate")
  public SelfActivationResponseEntity selfActivateProjectRoles(
    @PathParam("projectId") String projectIdString,
    SelfActivationRequestEntity request,
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
        var activation = this.roleActivationService.activateProjectRole(
          iapPrincipal.getId(),
          iapPrincipal.getId(),
          roleBinding,
          RoleActivationService.ActivationType.JIT,
          request.justification);

        assert activation != null;
        activations.add(activation);

        this.logAdapter
          .newInfoEntry(
            LogEvents.API_ACTIVATE_ROLE,
            String.format(
              "User %s successfully activated role '%s' on '%s' for themselves, justified by '%s'",
              iapPrincipal.getId(),
              roleBinding.role,
              roleBinding.fullResourceName,
              request.justification))
          .addLabel("role", roleBinding.role)
          .addLabel("resource", roleBinding.fullResourceName)
          .addLabel("justification", request.justification)
          .write();
      }
      catch (AccessDeniedException e) {
        this.logAdapter
          .newErrorEntry(
            LogEvents.API_ACTIVATE_ROLE,
            String.format(
              "User %s was denied to activated role '%s' on '%s' for themselves, justified by '%s': %s",
              iapPrincipal.getId(),
              roleBinding.role,
              roleBinding.fullResourceName,
              request.justification,
              e.getMessage()))
          .addLabel("role", roleBinding.role)
          .addLabel("resource", roleBinding.fullResourceName)
          .addLabel("justification", request.justification)
          .write();

        throw e;
      }
      catch (Exception e) {
        this.logAdapter
          .newErrorEntry(
            LogEvents.API_ACTIVATE_ROLE,
            String.format(
              "User %s failed to activate role '%s' on '%s' for themselves, justified by '%s': %s",
              iapPrincipal.getId(),
              roleBinding.role,
              roleBinding.fullResourceName,
              request.justification,
              e.getMessage()))
          .addLabel("role", roleBinding.role)
          .addLabel("resource", roleBinding.fullResourceName)
          .addLabel("justification", request.justification)
          .write();

        throw new AccessDeniedException("Activating role failed", e);
      }
    }

    assert activations.size() == roleBindings.size();

    return new SelfActivationResponseEntity(activations);
  }

  // -------------------------------------------------------------------------
  // Entity classes.
  // -------------------------------------------------------------------------

  public static class PolicyResponseEntity {
    public final String justificationHint;

    public PolicyResponseEntity(String justificationHint) {
      Preconditions.checkNotNull(justificationHint, "justificationHint");
      this.justificationHint = justificationHint;
    }
  }

  public static class ProjectsResponseEntity {
    public final Set<String> projects;

    public ProjectsResponseEntity(Set<String> projects) {
      Preconditions.checkNotNull(projects, "projects");
      this.projects = projects;
    }
  }


  public static class ProjectRolesResponseEntity {
    public final List<String> warnings;
    public final List<ProjectRole> roles;

    public ProjectRolesResponseEntity(
      List<ProjectRole> roleBindings,
      List<String> warnings
    ) {
      Preconditions.checkNotNull(roleBindings, "roleBindings");

      this.warnings = warnings;
      this.roles = roleBindings;
    }
  }

  public static class SelfActivationRequestEntity {
    public List<String> roles;
    public String justification;

    public SelfActivationRequestEntity() {
    }
  }

  public static class SelfActivationResponseEntity {
    public final List<RoleActivationService.Activation> activatedRoles;

    public SelfActivationResponseEntity(List<RoleActivationService.Activation> activatedRoles) {
      Preconditions.checkNotNull(activatedRoles, "activatedRoles");

      this.activatedRoles = activatedRoles;
    }
  }
}
