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
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.auth.JitGroupId;
import com.google.solutions.jitaccess.auth.Principal;
import com.google.solutions.jitaccess.catalog.policy.PolicyAnalysis;
import com.google.solutions.jitaccess.catalog.policy.Privilege;
import com.google.solutions.jitaccess.catalog.policy.Property;
import com.google.solutions.jitaccess.common.Coalesce;
import com.google.solutions.jitaccess.web.*;
import com.google.solutions.jitaccess.web.proposal.ProposalHandler;
import com.google.solutions.jitaccess.web.proposal.TokenObfuscator;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Dependent
@Path("/api")
@RequireIapPrincipal
@LogRequest
public class GroupsResource {
  private static final AccessDeniedException NOT_FOUND = new AccessDeniedException(
    "The group does not exist or access is denied");

  @Inject
  Catalog catalog;

  @Inject
  Logger logger;

  @Inject
  OperationAuditTrail auditTrail;

  @Inject
  ProposalHandler proposalHandler;

  @Context
  UriInfo uriInfo;

  @Inject
  LinkBuilder linkBuilder;

  @Inject
  Consoles consoles;

  /**
   * Get group details, including information about requirements
   * to join the group.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}/systems/{system}/groups/{name}")
  public @NotNull GroupInfo get(
    @PathParam("environment") @NotNull String environment,
    @PathParam("system") @NotNull String system,
    @PathParam("name") @NotNull String name
  ) throws Exception {
    try {
      var groupId = new JitGroupId(environment, system, name);

      return this.catalog
        .group(groupId)
        .map(grp -> GroupInfo.create(
          grp,
          JoinInfo.forJoinAnalysis(grp)))
        .orElseThrow(() -> NOT_FOUND);
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_VIEW_GROUPS, e);
      throw (Exception)e.fillInStackTrace();
    }
  }

  /**
   * Attempt to join the group.
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Path("environments/{environment}/systems/{system}/groups/{name}")
  public @NotNull GroupInfo post(
    @PathParam("environment") @NotNull String environment,
    @PathParam("system") @NotNull String system,
    @PathParam("name") @NotNull String name,
    @NotNull MultivaluedMap<String, String> inputValues
  ) throws Exception {

    var groupId = new JitGroupId(environment, system, name);

    try {
      var group = this.catalog
        .group(groupId)
        .orElseThrow(() -> NOT_FOUND);

      //
      // Attempt to join.
      //
      var joinOp = group.join();
      Inputs.copyValues(inputValues, joinOp.input());

      if (joinOp.requiresApproval()) {
        //
        // Approval required, propose to someone else.
        //
        // NB. The action URL needs to point to the frontend, not the
        //     REST API. Also, it can't use a fragment because fragments
        //     might be lost during an authentication redirect.
        //
        //     We therefore pass the API path in a query parameter,
        //     the frontend then translates this into making the right
        //     API call.
        //
        var proposal = this.proposalHandler.propose(
          joinOp,
          token -> this.linkBuilder
            .absoluteUriBuilder(this.uriInfo)
            .path("/")
            .queryParam("f", String.format(
              "/environments/%s/proposal/%s",
              environment, TokenObfuscator.encode(token)))
            .build());

        this.auditTrail.joinProposed(joinOp, proposal);

        return GroupInfo.create(
          group,
          JoinInfo.forProposal(joinOp.input()));
      }
      else {
        //
        // No approval required, execute the join.
        //
        var principal = joinOp.execute();
        this.auditTrail.joinExecuted(joinOp, principal);

        return GroupInfo.create(
          group,
          JoinInfo.forCompletedJoin(principal, joinOp.input()));
      }
    }
    catch (PolicyAnalysis.ConstraintFailedException e) {
      this.auditTrail.constraintFailed(groupId, e);
      throw new AccessDeniedException(e.getMessage(), e);
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_JOIN_GROUP, e);
      throw (Exception)e.fillInStackTrace();
    }
  }

  /**
   * Return a link to one of the consoles.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}/systems/{system}/groups/{name}/link/{target}")
  public @NotNull ExternalLinkInfo linkTo(
    @PathParam("environment") @NotNull String environment,
    @PathParam("system") @NotNull String system,
    @PathParam("name") @NotNull String name,
    @PathParam("target") @NotNull String target
  ) throws Exception {
    try {
      var groupId = new JitGroupId(environment, system, name);

      //
      // Lookup group, implicitly verifying VIEW access.
      //
      var group = this.catalog
        .group(groupId)
        .orElseThrow(() -> NOT_FOUND);

      //
      // Lookup the group key, this may return empty if the group
      // hasn't been created yet.
      //
      var groupKey = group
        .cloudIdentityGroupKey()
        .orElseThrow(() -> new NotFoundException("The group has not been created yet"));

      var targetUri = Optional
        .ofNullable(switch (target) {
          case "cloud-console" -> consoles.cloudConsole().groupDetails(groupKey);
          case "admin-console" -> consoles.adminConsole().groupDetails(groupKey);
          case "groups-console" -> consoles.groupsConsole().groupDetails(group.cloudIdentityGroupId());
          case "cloud-logging" -> consoles.cloudConsole().groupAuditLogs(
            groupId,
            Instant.now().minus(Duration.ofDays(7)));
          default -> null;
        })
        .orElseThrow(() -> new NotFoundException("Unknown console"));

      return new ExternalLinkInfo(
        new Link(
          "environments/%s/systems/%s/groups/%s/link/%s",
          groupId.environment(),
          groupId.system(),
          groupId.name(),
          target),
        new Link(targetUri));
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_VIEW_GROUPS, e);
      throw (Exception)e.fillInStackTrace();
    }
  }

  //---------------------------------------------------------------------------
  // Payload records.
  //---------------------------------------------------------------------------

  public record GroupInfo(
    @NotNull Link self,
    @NotNull String id,
    @NotNull String name,
    @NotNull String displayName,
    @NotNull String description,
    @NotNull String cloudIdentityGroup,
    @NotNull List<PrivilegeInfo> privileges,
    @NotNull EnvironmentsResource.EnvironmentInfo environment,
    @NotNull SystemsResource.SystemInfo system,
    @Nullable JoinInfo join
  ) implements MediaInfo {
    static GroupInfo create(
      @NotNull JitGroupContext g,
      @NotNull JoinInfo joinInfo) {
      return new GroupInfo(
        new Link(
          "environments/%s/systems/%s/groups/%s",
          g.policy().id().environment(),
          g.policy().id().system(),
          g.policy().id().name()),
        g.policy().id().toString(),
        g.policy().name(),
        g.policy().displayName(),
        g.policy().description(),
        g.cloudIdentityGroupId().email,
        g.policy()
          .privileges()
          .stream()
          .map(PrivilegeInfo::fromPrivilege)
          .toList(),
        EnvironmentsResource.EnvironmentInfo.createSummary(g.policy().system().environment()),
        SystemsResource.SystemInfo.createSummary(g.policy().system()), // Don't list nested groups.
        joinInfo);
    }
  }

  public record PrivilegeInfo(
    @NotNull String description
  ) {
    static @NotNull PrivilegeInfo fromPrivilege(@NotNull Privilege p) {
      return new PrivilegeInfo(Coalesce.nonEmpty(p.description(), p.toString()));
    }
  }

  public record JoinInfo(
    @NotNull JoinStatusInfo status,
    @NotNull MembershipInfo membership,
    @NotNull List<ConstraintInfo> satisfiedConstraints,
    @NotNull List<ConstraintInfo> unsatisfiedConstraints,
    @NotNull List<InputInfo> input
  ) {
    static @NotNull GroupsResource.JoinInfo forJoinAnalysis(
      @NotNull JitGroupContext g
    ) {
      var joinOp = g.join();
      var analysis = joinOp.dryRun();

      JoinStatusInfo status;
      if (analysis.activeMembership().isPresent()) {
        status = JoinStatusInfo.JOINED;
      }
      else if (!analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS)) {
        status = JoinStatusInfo.JOIN_DISALLOWED;
      }
      else if (joinOp.requiresApproval()) {
        status = JoinStatusInfo.JOIN_ALLOWED_WITH_APPROVAL;
      }
      else {
        status = JoinStatusInfo.JOIN_ALLOWED_WITHOUT_APPROVAL;
      }

      return new JoinInfo(
        status,
        new MembershipInfo(
          analysis.activeMembership().isPresent(),
          analysis.activeMembership()
            .map(p -> p.expiry() != null ? p.expiry().getEpochSecond(): null)
            .orElse(null)),
        analysis.satisfiedConstraints().stream()
          .map(c -> new ConstraintInfo(c.name(), c.displayName()))
          .toList(),
        analysis.unsatisfiedConstraints().stream()
          .map(c -> new ConstraintInfo(c.name(), c.displayName()))
          .toList(),
        analysis.input().stream()
          .sorted(Comparator.comparing(p -> p.name()))
          .map(InputInfo::fromProperty)
          .toList());
    }

    static @NotNull GroupsResource.JoinInfo forCompletedJoin(
      @NotNull Principal principal,
      @NotNull List<Property> input
    ) {
      return new JoinInfo(
        JoinStatusInfo.JOIN_COMPLETED,
        new MembershipInfo(
          true,
          Optional.ofNullable(principal.expiry())
            .map(Instant::getEpochSecond)
            .get()),
        List.of(), // Don't repeat constraints
        List.of(), // Don't repeat constraints
        input
          .stream()
          .sorted(Comparator.comparing(p -> p.name()))
          .map(InputInfo::fromProperty)
          .toList());
    }

    static @NotNull GroupsResource.JoinInfo forProposal(
      @NotNull List<Property> input
    ) {
      return new JoinInfo(
        JoinStatusInfo.JOIN_PROPOSED,
        new MembershipInfo(false, null),
        List.of(), // Don't repeat constraints
        List.of(), // Don't repeat constraints
        input
          .stream()
          .sorted(Comparator.comparing(p -> p.name()))
          .map(InputInfo::fromProperty)
          .toList());
    }
  }

  public record ExternalLinkInfo(
    @NotNull Link self,
    @NotNull Link location
  ) implements MediaInfo {}

  public enum JoinStatusInfo {
    /**
     * The current user lacks permissions to join this group.
     */
    JOIN_DISALLOWED,

    /**
     * The current user is allowed to join this group and doesn't require
     * approval.
     */
    JOIN_ALLOWED_WITHOUT_APPROVAL,

    /**
     * The current user is allowed to join this group, but requires approval.
     */
    JOIN_ALLOWED_WITH_APPROVAL,

    /**
     * The join has been proposed for approval.
     */
    JOIN_PROPOSED,

    /**
     * Join completed, the current user is now a member of the group.
     */
    JOIN_COMPLETED,

    /**
     * The current user is already a member of this group.
     */
    JOINED
  }

  public record MembershipInfo(
    boolean active,
    @Nullable Long expiry
  ) {}


  public record ConstraintInfo(
    @NotNull String name,
    @NotNull String description
  ) {}

  public record InputInfo(
    @NotNull String name,
    @NotNull String description,
    @NotNull String type,
    boolean isRequired,
    @Nullable String value,
    @Nullable String minInclusive,
    @Nullable String maxInclusive
  ) {
    static InputInfo fromProperty(@NotNull Property i) {
      return new InputInfo(
        i.name(),
        i.displayName(),
        i.type().getSimpleName(),
        i.isRequired(),
        i.get(),
        i.minInclusive().orElse(null),
        i.maxInclusive().orElse(null));
    }
  }
}
