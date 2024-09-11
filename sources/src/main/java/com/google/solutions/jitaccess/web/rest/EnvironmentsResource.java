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
import com.google.solutions.jitaccess.catalog.EnvironmentContext;
import com.google.solutions.jitaccess.catalog.JitGroupCompliance;
import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.catalog.policy.PolicyDocument;
import com.google.solutions.jitaccess.catalog.policy.PolicyHeader;
import com.google.solutions.jitaccess.common.Exceptions;
import com.google.solutions.jitaccess.web.EventIds;
import com.google.solutions.jitaccess.web.LogRequest;
import com.google.solutions.jitaccess.web.RequireIapPrincipal;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Dependent
@Path("/api")
@RequireIapPrincipal
@LogRequest
public class EnvironmentsResource {
  private static final AccessDeniedException NOT_FOUND = new AccessDeniedException(
    "The environment does not exist or access is denied");

  @Inject
  Catalog catalog;

  @Inject
  Logger logger;

  /**
   * Get list of environments, limited to basic information.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments")
  public @NotNull EnvironmentsResource.EnvironmentsInfo list() throws Exception {
    try {
      var environments = this.catalog.environments()
        .stream()
        .sorted(Comparator.comparing(env -> env.name()))
        .map(env -> EnvironmentInfo.createSummary(env))
        .collect(Collectors.toList());

      return new EnvironmentsInfo(
        new Link("environments"),
        environments);
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_VIEW_ENVIRONMENTS, e);

      throw (Exception)e.fillInStackTrace();
    }
  }

  /**
   * Get environment details, including the list of systems that
   * the current user is allowed to view.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}")
  public @NotNull EnvironmentInfo get(
    @PathParam("environment") @NotNull String environmentName
  ) throws Exception {
    try {
      return this.catalog
        .environment(environmentName)
        .map(env -> EnvironmentInfo.create(env))
        .orElseThrow(() -> NOT_FOUND);
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_VIEW_ENVIRONMENTS, e);

      throw (Exception)e.fillInStackTrace();
    }
  }

  /**
   * Get policy source.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}/policy")
  public @NotNull PolicyInfo getPolicy(
    @PathParam("environment") @NotNull String environmentName
  ) throws Exception {
    try {
      return this.catalog
        .environment(environmentName)
        .flatMap(EnvironmentContext::export)
        .map(doc -> PolicyInfo.create(doc))
        .orElseThrow(() -> NOT_FOUND);
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_VIEW_ENVIRONMENTS, e);

      throw (Exception)e.fillInStackTrace();
    }
  }

  /**
   * Get compliance information.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}/compliance")
  public @NotNull EnvironmentsResource.ComplianceInfo getCompliance(
    @PathParam("environment") @NotNull String environmentName
  ) throws Exception {
    try {
      var environment =  this.catalog
        .environment(environmentName)
        .orElseThrow(() -> NOT_FOUND);

      return ComplianceInfo.forPendingReconciliation(environment);
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_RECONCILE_ENVIRONMENT, e);

      throw (Exception)e.fillInStackTrace();
    }
  }


  /**
   * Reconcile policy and return compliance issues.
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}/compliance")
  public @NotNull EnvironmentsResource.ComplianceInfo reconcile(
    @PathParam("environment") @NotNull String environmentName
  ) throws Exception {
    try {
      var environment =  this.catalog
        .environment(environmentName)
        .orElseThrow(() -> NOT_FOUND);

      return ComplianceInfo.forReconciliation(
        environment,
        environment
          .reconcile()
          .orElseThrow(() -> NOT_FOUND));
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_RECONCILE_ENVIRONMENT, e);

      throw (Exception)e.fillInStackTrace();
    }
  }

  //---------------------------------------------------------------------------
  // Payload records.
  //---------------------------------------------------------------------------

  public record EnvironmentsInfo(
    @NotNull Link self,
    @NotNull List<EnvironmentInfo> environments
  ) implements MediaInfo {
  }

  public record EnvironmentInfo(
    @NotNull Link self,
    @Nullable Link policy,
    @Nullable Link reconcile,
    @NotNull String name,
    @NotNull String displayName,
    @NotNull String description,
    @Nullable List<SystemsResource.SystemInfo> systems
  ) implements MediaInfo {

    /**
     * Create EnvironmentInfo with summary information only.
     */
    static EnvironmentInfo createSummary(
      @NotNull PolicyHeader policy
    ) {
      return new EnvironmentInfo(
        new Link("environments/%s", policy.name()),
        null,
        null,
        policy.name(),
        policy.name(),
        policy.description(),
        null);
    }

    /**
     * Create EnvironmentInfo with full details.
     */
    static EnvironmentInfo create(@NotNull EnvironmentContext environment) {
      return new EnvironmentInfo(
        new Link("environments/%s", environment.policy().name()),
        environment.canExport()
          ? new Link("environments/%s/policy", environment.policy().name())
          : null,
        environment.canReconcile()
          ? new Link("environments/%s/compliance", environment.policy().name())
          : null,
        environment.policy().name(),
        environment.policy().displayName(),
        environment.policy().description(),
        environment.systems()
          .stream()
          .sorted(Comparator.comparing(sys -> sys.policy().displayName()))
          .map(sys -> SystemsResource.SystemInfo.createSummary(sys.policy()))
          .toList());
    }
  }

  public record PolicyInfo(
    @NotNull Link self,
    @NotNull EnvironmentInfo environment,
    @NotNull String policy,
    @NotNull String source,
    @NotNull Long lastModified
  ) implements MediaInfo {
    static PolicyInfo create(@NotNull PolicyDocument doc) {
      return new PolicyInfo(
        new Link("environments/%s", doc.policy().name()),
        EnvironmentInfo.createSummary(doc.policy()),
        doc.toString(),
        doc.policy().metadata().source(),
        doc.policy().metadata().lastModified().getEpochSecond());
    }
  }

  public enum ReconciliationStatusInfo {
    RECONCILIATION_PENDING,
    RECONCILIATION_COMPLETED
  }

  public record ComplianceInfo(
    @NotNull Link self,
    @NotNull ReconciliationStatusInfo status,
    @NotNull EnvironmentInfo environment,
    @NotNull List<ComplianceIssueInfo> issues
  ) implements MediaInfo {
    static ComplianceInfo forReconciliation(
      @NotNull EnvironmentContext environment,
      @NotNull Collection<JitGroupCompliance> groups
    ) {
      return new ComplianceInfo(
        new Link("environments/%s", environment.policy().name()),
        ReconciliationStatusInfo.RECONCILIATION_COMPLETED,
        EnvironmentInfo.createSummary(environment.policy()),
        groups.stream()
          .filter(g -> !g.isCompliant())
          .map(ComplianceIssueInfo::create)
          .toList());
    }

    static ComplianceInfo forPendingReconciliation(
      @NotNull EnvironmentContext environment
    ) {
      return new ComplianceInfo(
        new Link("environments/%s", environment.policy().name()),
        ReconciliationStatusInfo.RECONCILIATION_PENDING,
        EnvironmentInfo.createSummary(environment.policy()),
        List.of());
    }
  }

  public record ComplianceIssueInfo(
    @NotNull String environment,
    @NotNull String system,
    @NotNull String group,
    @Nullable String cloudIdentityGroupId,
    @NotNull String details
    ) {
    static ComplianceIssueInfo create(JitGroupCompliance compliance) {
      if (compliance.isOrphaned()) {
        return new ComplianceIssueInfo(
          compliance.groupId().environment(),
          compliance.groupId().system(),
          compliance.groupId().name(),
          compliance.cloudIdentityGroupId()
            .map(g -> g.email)
            .orElse(null),
          "The group is orphaned. A group exists in Cloud Identity, but it is not covered by a policy.");

      }
      else if (!compliance.isCompliant()) {
        return new ComplianceIssueInfo(
          compliance.groupId().environment(),
          compliance.groupId().system(),
          compliance.groupId().name(),
          compliance.cloudIdentityGroupId()
            .map(g -> g.email)
            .orElse(null),
          compliance.exception()
            .map(e -> Exceptions.fullMessage(e, false))
            .orElse("Unspecified error"));
      }
      else {
        return new ComplianceIssueInfo(
          compliance.groupId().environment(),
          compliance.groupId().system(),
          compliance.groupId().name(),
          compliance.cloudIdentityGroupId()
            .map(g -> g.email)
            .orElse(null),
          "OK");
      }
    }
  }
}
