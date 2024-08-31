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

package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.catalog.Catalog;
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.catalog.SystemContext;
import com.google.solutions.jitaccess.catalog.policy.SystemPolicy;
import com.google.solutions.jitaccess.web.EventIds;
import com.google.solutions.jitaccess.web.LogRequest;
import com.google.solutions.jitaccess.web.RequireIapPrincipal;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

@Dependent
@Path("/api")
@RequireIapPrincipal
@LogRequest
public class SystemsResource {
  private static final AccessDeniedException NOT_FOUND = new AccessDeniedException(
    "The system does not exist or access is denied");

  @Inject
  Catalog catalog;

  @Inject
  Logger logger;

  /**
   * Get system details, including the list of groups that
   * the current user is allowed to view.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}/systems/{system}")
  public @NotNull SystemInfo get(
    @PathParam("environment") @NotNull String environment,
    @PathParam("system") @NotNull String system
  ) throws Exception {
    try {
      return this.catalog
        .environment(environment)
        .flatMap(env -> env.system(system))
        .map(sys -> SystemInfo.create(sys))
        .orElseThrow(() -> NOT_FOUND);
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_VIEW_SYSTEMS, e);

      throw (Exception)e.fillInStackTrace();
    }
  }

  //---------------------------------------------------------------------------
  // Payload records.
  //---------------------------------------------------------------------------

  public record SystemInfo(
    @NotNull Link self,
    @NotNull String name,
    @NotNull String displayName,
    @NotNull String description,
    @Nullable EnvironmentsResource.EnvironmentInfo environment,
    @Nullable List<GroupsResource.GroupInfo> groups
  ) implements MediaInfo {

    /**
     * Create SystemInfo with summary information only.
     */
    static SystemInfo createSummary(
      @NotNull SystemPolicy policy
    ) {
      return new SystemInfo(
        new Link("environments/%s/systems/%s", policy.environment().name(), policy.name()),
        policy.name(),
        policy.displayName(),
        policy.description(),
        null,
        null);
    }

    /**
     * Create SystemInfo with full details.
     */
    static SystemInfo create(@NotNull SystemContext system) {
      var policy = system.policy();

      return new SystemInfo(
        new Link("environments/%s/systems/%s", policy.environment().name(), policy.name()),
        policy.name(),
        policy.displayName(),
        policy.description(),
        EnvironmentsResource.EnvironmentInfo.createSummary(policy.environment()),
        system.groups()
          .stream()
          .sorted(Comparator.comparing(grp -> grp.policy().displayName()))
          .map(grp -> GroupsResource.GroupInfo.create(
            grp,
            GroupsResource.JoinInfo.forJoinAnalysis(grp)))
          .toList());
    }
  }
}
