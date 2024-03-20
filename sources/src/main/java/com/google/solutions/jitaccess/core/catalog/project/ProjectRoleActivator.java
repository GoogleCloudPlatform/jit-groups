//
// Copyright 2023 Google LLC
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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.cel.TemporaryIamCondition;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import jakarta.enterprise.context.Dependent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Activator for project roles.
 */
@Dependent
public class ProjectRoleActivator extends EntitlementActivator<
  ProjectRole,
  ProjectId,
  MpaProjectRoleCatalog.UserContext> {
  private final @NotNull ResourceManagerClient resourceManagerClient;

  private final Options options;

  public ProjectRoleActivator(
    @NotNull Catalog<ProjectRole, ProjectId, MpaProjectRoleCatalog.UserContext> catalog,
    @NotNull ResourceManagerClient resourceManagerClient,
    @NotNull JustificationPolicy policy,
    @NotNull Options options
  ) {
    super(catalog, policy);

    Preconditions.checkNotNull(resourceManagerClient, "resourceManagerClient");
    this.resourceManagerClient = resourceManagerClient;
    this.options = options;
  }

  private void provisionTemporaryBinding(
    @NotNull String bindingDescription,
    @NotNull ProjectId projectId,
    @NotNull UserId user,
    @NotNull Set<String> roles,
    @NotNull Instant startTime,
    @NotNull Duration duration
  ) throws AccessException, AlreadyExistsException, IOException {

    //
    // Add time-bound IAM binding.
    //
    // Replace existing bindings for same user and role to avoid
    // accumulating junk, and to prevent hitting the binding limit.
    //

    for (var role : roles) {
      var binding = new Binding()
        .setMembers(List.of("user:" + user))
        .setRole(role)
        .setCondition(new com.google.api.services.cloudresourcemanager.v3.model.Expr()
          .setTitle(JitConstraints.ACTIVATION_CONDITION_TITLE)
          .setDescription(bindingDescription)
          .setExpression(new TemporaryIamCondition(startTime, duration).toString()));

      this.resourceManagerClient.addProjectIamBinding(
        projectId,
        binding,
        EnumSet.of(ResourceManagerClient.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS),
        bindingDescription);
    }
  }

  // -------------------------------------------------------------------------
  // Overrides.
  // -------------------------------------------------------------------------

  @Override
  public int maximumNumberOfEntitlementsPerJitRequest() {
    return this.options.maxNumberOfEntitlementsPerJitRequest;
  }

  @Override
  protected Activation provisionAccess(
    @NotNull JitActivationRequest<ProjectRole> request
  ) throws AccessException, AlreadyExistsException, IOException {
    Preconditions.checkNotNull(request, "request");

    var bindingDescription = String.format(
      "Self-approved, justification: %s",
      request.justification());

    provisionTemporaryBinding(
      bindingDescription,
      ProjectActivationRequest.projectId(request),
      request.requestingUser(),
      request.entitlements()
        .stream()
        .map(e -> e.roleBinding().role())
        .collect(Collectors.toSet()),
      request.startTime(),
      request.duration());

    return new Activation(
      request.startTime(),
      request.duration());
  }

  @Override
  protected Activation provisionAccess(
    @NotNull UserId approvingUser,
    @NotNull MpaActivationRequest<ProjectRole> request
  ) throws AccessException, AlreadyExistsException, IOException {

    Preconditions.checkNotNull(request, "request");

    var bindingDescription = String.format(
      "Approved by %s, justification: %s",
      approvingUser.email,
      request.justification());

    //
    // NB. The start/end time for the binding is derived from the approval token. If multiple
    // reviewers try to approve the same token, the resulting condition (and binding) will
    // be the same.
    //

    provisionTemporaryBinding(
      bindingDescription,
      ProjectActivationRequest.projectId(request),
      request.requestingUser(),
      request.entitlements()
        .stream()
        .map(e -> e.roleBinding().role())
        .collect(Collectors.toSet()),
      request.startTime(),
      request.duration());

    return new Activation(
      request.startTime(),
      request.duration());
  }

  @Override
  public @NotNull JsonWebTokenConverter<MpaActivationRequest<ProjectRole>> createTokenConverter() {
    return new JsonWebTokenConverter<>() {
      @Override
      public JsonWebToken.Payload convert(@NotNull MpaActivationRequest<ProjectRole> request) {
        var roleBindings = request.entitlements()
          .stream()
          .map(ent -> ent.roleBinding())
          .toList();

        if (roleBindings.size() != 1) {
          throw new IllegalArgumentException("Request must have exactly one entitlement");
        }

        var roleBinding = roleBindings.get(0);

        return new JsonWebToken.Payload()
          .setJwtId(request.id().toString())
          .set("beneficiary", request.requestingUser().email)
          .set("reviewers", request.reviewers().stream().map(id -> id.email).collect(Collectors.toList()))
          .set("resource", roleBinding.fullResourceName())
          .set("role", roleBinding.role())
          .set("justification", request.justification())
          .set("start", request.startTime().getEpochSecond())
          .set("end", request.endTime().getEpochSecond());
      }

      @SuppressWarnings("unchecked")
      @Override
      public @NotNull MpaActivationRequest<ProjectRole> convert(@NotNull JsonWebToken.Payload payload) {
        var roleBinding = new RoleBinding(
          payload.get("resource").toString(),
          payload.get("role").toString());

        var startTime = ((Number)payload.get("start")).longValue();
        var endTime = ((Number)payload.get("end")).longValue();

        return new MpaRequest<>(
          new ActivationId(payload.getJwtId()),
          new UserId(payload.get("beneficiary").toString()),
          Set.of(new ProjectRole(roleBinding)),
          ((List<String>)payload.get("reviewers"))
            .stream()
            .map(email -> new UserId(email))
            .collect(Collectors.toSet()),
          payload.get("justification").toString(),
          Instant.ofEpochSecond(startTime),
          Duration.ofSeconds(endTime - startTime));
      }
    };
  }

  // -------------------------------------------------------------------------
  // Options.
  // -------------------------------------------------------------------------

  public record Options(int maxNumberOfEntitlementsPerJitRequest) {
    public Options {
      Preconditions.checkArgument(
        maxNumberOfEntitlementsPerJitRequest > 0,
        "The maximum number of entitlements per request must be greater than zero");
    }
  }
}
