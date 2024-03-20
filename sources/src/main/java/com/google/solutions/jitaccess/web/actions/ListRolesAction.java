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
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.Entitlement;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.LogEvents;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import jakarta.enterprise.context.Dependent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * List roles (within a project) that the user can activate.
 */
public class ListRolesAction extends AbstractAction {
  private final @NotNull MpaProjectRoleCatalog catalog;

  public ListRolesAction(
    @NotNull LogAdapter logAdapter,
    @NotNull MpaProjectRoleCatalog catalog
  ) {
    super(logAdapter);
    this.catalog = catalog;
  }

  public @NotNull ResponseEntity execute(
    @NotNull IapPrincipal iapPrincipal,
    @Nullable String projectIdString
  ) throws AccessException {
    Preconditions.checkArgument(
      projectIdString != null && !projectIdString.trim().isEmpty(),
      "A projectId is required");

    var userContext = this.catalog.createContext(iapPrincipal.email());
    var projectId = new ProjectId(projectIdString);

    try {
      var entitlements = this.catalog.listEntitlements(userContext, projectId);

      return new ResponseEntity(
        entitlements.available()
          .stream()
          .map(ent -> {
            var currentActivation = entitlements.currentActivations().get(ent.id());
            if (currentActivation != null) {
              return new ResponseEntity.Item(
                ent.id().roleBinding(),
                ent.activationType(),
                ActivationStatus.ACTIVE,
                currentActivation.validity().end().getEpochSecond());
            }
            else {
              return new ResponseEntity.Item(
                ent.id().roleBinding(),
                ent.activationType(),
                ActivationStatus.INACTIVE,
                null);
            }
          })
          .collect(Collectors.toList()),
        entitlements.warnings());
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

  public static class ResponseEntity {
    public final Set<String> warnings;
    public final @NotNull List<Item> roles;

    private ResponseEntity(
      @NotNull List<Item> roles,
      Set<String> warnings
    ) {
      Preconditions.checkNotNull(roles, "roles");

      this.warnings = warnings;
      this.roles = roles;
    }

    public static class Item {
      public final @NotNull RoleBinding roleBinding;
      public final @NotNull ActivationType activationType;
      public final @NotNull ActivationStatus status;
      public final Long /* optional */ validUntil;

      public Item(
        @NotNull RoleBinding roleBinding,
        @NotNull ActivationType activationType,
        @NotNull ActivationStatus status,
        Long validUntil) {

        Preconditions.checkNotNull(roleBinding, "roleBinding");

        this.roleBinding = roleBinding;
        this.activationType = activationType;
        this.status = status;
        this.validUntil = validUntil;
      }
    }
  }
}
