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
import com.google.solutions.jitaccess.core.AlreadyExistsException;
import com.google.solutions.jitaccess.core.adapters.LogAdapter;
import com.google.solutions.jitaccess.core.adapters.UserPrincipal;
import com.google.solutions.jitaccess.core.services.ElevationService;
import com.google.solutions.jitaccess.core.services.RoleBinding;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API controller.
 */
@Dependent
@Path("/api/")
public class ApiResource {
  private static final String EVENT_LIST_ELIGIBLE_ROLES = "api.listEligibleRoles";
  private static final String EVENT_ACTIVATE_ROLE = "api.activateRole";

  @Inject
  ElevationService elevationService;

  @Inject
  LogAdapter logAdapter;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public ResultEntity listEligibleRoleBindings(@Context SecurityContext securityContext)
    throws AccessException, IOException {
    Preconditions.checkNotNull(elevationService, "elevationService");
    UserPrincipal iapPricipal = (UserPrincipal) securityContext.getUserPrincipal();

    try {
      var bindings = this.elevationService.listEligibleRoleBindings(iapPricipal.getId());

      return new ResultEntity(
        bindings.getRoleBindings(),
        bindings.getWarnings(),
        this.elevationService.getOptions().getJustificationHint(),
        (int) this.elevationService.getOptions().getActivationDuration().toMinutes());
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          EVENT_LIST_ELIGIBLE_ROLES,
          String.format("Failed to list eligible roles: %s", e.getMessage()))
        .write();

      throw new AccessDeniedException("Failed to list eligible roles", e);
    }
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  public ResultEntity activateRoleBindings(
    @FormParam("roles") List<String> roles,
    @FormParam("justification") String justification,
    @Context SecurityContext securityContext)
    throws AccessException, AlreadyExistsException, IOException {
    Preconditions.checkNotNull(elevationService, "elevationService");
    UserPrincipal iapPricipal = (UserPrincipal) securityContext.getUserPrincipal();

    var roleBindings =
      roles.stream()
        .map(
          encoded -> {
            var roleParts = encoded.split("\\|");
            if (roleParts.length != 3) {
              throw new IllegalArgumentException("Malformed role");
            }

            return new RoleBinding(
              roleParts[0],
              roleParts[1],
              roleParts[2],
              RoleBinding.RoleBindingStatus.ELIGIBLE);
          })
        .collect(Collectors.toList());

    for (var roleBinding : roleBindings) {
      try {
        this.elevationService.activateEligibleRoleBinding(
          iapPricipal.getId(), roleBinding, justification);

        this.logAdapter
          .newInfoEntry(
            EVENT_ACTIVATE_ROLE,
            String.format(
              "Activated '%s' for '%s' on '%s', justified by '%s'",
              roleBinding.getRole(),
              iapPricipal.getId(),
              roleBinding.getFullResourceName(),
              justification))
          .addLabel("role", roleBinding.getRole())
          .addLabel("resource", roleBinding.getFullResourceName())
          .addLabel("justification", justification)
          .write();
      }
      catch (AccessDeniedException e) {
        this.logAdapter
          .newErrorEntry(
            EVENT_ACTIVATE_ROLE,
            String.format(
              "Denied to activate '%s' for '%s' on '%s', justified by '%s': %s",
              roleBinding.getRole(),
              iapPricipal.getId(),
              roleBinding.getFullResourceName(),
              justification,
              e.getMessage()))
          .addLabel("role", roleBinding.getRole())
          .addLabel("resource", roleBinding.getFullResourceName())
          .addLabel("justification", justification)
          .write();

        throw e;
      }
      catch (Exception e) {
        this.logAdapter
          .newErrorEntry(
            EVENT_ACTIVATE_ROLE,
            String.format(
              "Failed to activate '%s' for '%s' on '%s', justified by '%s': %s",
              roleBinding.getRole(),
              iapPricipal.getId(),
              roleBinding.getFullResourceName(),
              justification,
              e.getMessage()))
          .addLabel("role", roleBinding.getRole())
          .addLabel("resource", roleBinding.getFullResourceName())
          .addLabel("justification", justification)
          .write();

        throw new AccessDeniedException("Activating role failed", e);
      }
    }

    return new ResultEntity(
      roleBindings.stream()
        .map(
          b ->
            new RoleBinding(
              b.getResourceName(),
              b.getFullResourceName(),
              b.getRole(),
              RoleBinding.RoleBindingStatus.ACTIVATED))
        .collect(Collectors.toList()),
      List.of(),
      this.elevationService.getOptions().getJustificationHint(),
      (int) this.elevationService.getOptions().getActivationDuration().toMinutes());
  }

  public class ResultEntity {
    private final List<String> warnings;
    private final List<RoleBinding> roleBindings;
    private final String justificationHint;
    private final int activationDuration;

    public ResultEntity(
      List<RoleBinding> roleBindings,
      List<String> warnings,
      String justificationHint,
      int activationDuration) {
      this.warnings = warnings;
      this.roleBindings = roleBindings;
      this.justificationHint = justificationHint;
      this.activationDuration = activationDuration;
    }

    public List<String> getWarnings() {
      return warnings;
    }

    public List<RoleBinding> getRoleBindings() {
      return roleBindings;
    }

    public String getJustificationHint() {
      return justificationHint;
    }

    public int getActivationDuration() {
      return activationDuration;
    }
  }
}
