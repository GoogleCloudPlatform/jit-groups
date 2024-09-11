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

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.auth.JitGroupId;
import com.google.solutions.jitaccess.auth.Principal;
import com.google.solutions.jitaccess.catalog.Catalog;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.Proposal;
import com.google.solutions.jitaccess.catalog.policy.PolicyAnalysis;
import com.google.solutions.jitaccess.catalog.policy.Property;
import com.google.solutions.jitaccess.web.EventIds;
import com.google.solutions.jitaccess.web.LogRequest;
import com.google.solutions.jitaccess.web.OperationAuditTrail;
import com.google.solutions.jitaccess.web.RequireIapPrincipal;
import com.google.solutions.jitaccess.web.proposal.ProposalHandler;
import com.google.solutions.jitaccess.web.proposal.TokenObfuscator;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

@Dependent
@Path("/api")
@RequireIapPrincipal
@LogRequest
public class ProposalResource {
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

  /**
   * Get information about a proposal.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}/proposal/{token}")
  public @NotNull ProposalInfo get(
    @PathParam("environment") @NotNull String environment,
    @PathParam("token") @NotNull String proposalToken
  ) throws Exception {
    try {
      var proposal = this.proposalHandler.accept(TokenObfuscator.decode(proposalToken));

      Preconditions.checkArgument(
        proposal.group().environment().equals(environment),
        "The token must match the environment");

      return this.catalog
        .group(proposal.group())
        .map(grp -> ProposalInfo.create(
          grp,
          proposal,
          proposalToken,
          ApprovalInfo.forApprovalAnalysis(grp, proposal)))
        .orElseThrow(() -> NOT_FOUND);
    }
    catch (IllegalArgumentException e) {
      this.logger.warn(EventIds.API_APPROVE_JOIN, e);
      throw new ForbiddenException(
        "The URL is no longer valid or access is denied", e);
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_APPROVE_JOIN, e);
      throw (Exception)e.fillInStackTrace();
    }
  }

  /**
   * Attempt to approve a proposal.
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Path("environments/{environment}/proposal/{token}")
  public @NotNull ProposalInfo post(
    @PathParam("environment") @NotNull String environment,
    @PathParam("token") @NotNull String proposalToken,
    @NotNull MultivaluedMap<String, String> inputValues
  ) throws Exception {
    JitGroupId groupId = null;
    try {
      var proposal = this.proposalHandler.accept(TokenObfuscator.decode(proposalToken));
      groupId = proposal.group();

      Preconditions.checkArgument(
        groupId.environment().equals(environment),
        "The token must match the environment");

      //
      // Attempt to approve.
      //
      var group = this.catalog
        .group(proposal.group())
        .orElseThrow(() -> NOT_FOUND);

      var approveOp = group.approve(proposal);
      Inputs.copyValues(inputValues, approveOp.input());

      var principal = approveOp.execute();
      this.auditTrail.joinExecuted(approveOp, principal);

      return ProposalInfo.create(
        group,
        proposal,
        proposalToken,
        ApprovalInfo.forApprovedProposal(
          group,
          principal,
          approveOp.joiningUserInput(),
          approveOp.input()));
    }
    catch (PolicyAnalysis.ConstraintFailedException e) {
      this.auditTrail.constraintFailed(groupId, e);
      throw new AccessDeniedException(e.getMessage(), e);
    }
    catch (Exception e) {
      this.logger.warn(EventIds.API_APPROVE_JOIN, e);
      throw (Exception)e.fillInStackTrace();
    }
  }

  //---------------------------------------------------------------------------
  // Payload records.
  //---------------------------------------------------------------------------

  public record ProposalInfo(
    @NotNull Link self,
    @NotNull String user,
    @Nullable ApprovalInfo approval
  ) implements MediaInfo {
    static ProposalInfo create(
      @NotNull JitGroupContext g,
      @NotNull Proposal proposal,
      @NotNull String proposalToken,
      @NotNull ApprovalInfo approvalInfo
    ) {
      return new ProposalInfo(
        new Link(
          "environments/%s/proposal/%s",
          g.policy().id().environment(),
          proposalToken),
        proposal.user().email,
        approvalInfo);
    }
  }

  public enum ApprovalStatusInfo {
    /**
     * The current user lacks permissions to approve this proposal.
     */
    APPROVAL_DISALLOWED,

    /**
     * The current user is allowed to approve this proposal.
     */
    APPROVAL_ALLOWED,

    /**
     * Proposal approved, the proposing user is now a member of the group.
     */
    APPROVAL_COMPLETED
  }

  public record ApprovalInfo(
    @NotNull ApprovalStatusInfo status,
    @NotNull GroupsResource.GroupInfo group,
    @NotNull List<GroupsResource.ConstraintInfo> satisfiedConstraints,
    @NotNull List<GroupsResource.ConstraintInfo> unsatisfiedConstraints,
    @NotNull List<GroupsResource.InputInfo> input
  ) {
    static @NotNull ApprovalInfo forApprovalAnalysis(
      @NotNull JitGroupContext g,
      @NotNull Proposal proposal
    ) {
      var approvalOp = g.approve(proposal);

      var groupInfo = GroupsResource.GroupInfo.create(
          g,
          GroupsResource.JoinInfo.forProposal(approvalOp.joiningUserInput()));

      var analysis = approvalOp.dryRun();

      ApprovalStatusInfo status;
      if (analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS)) {
        status = ApprovalStatusInfo.APPROVAL_ALLOWED;
      }
      else {
        status = ApprovalStatusInfo.APPROVAL_DISALLOWED;
      }

      return new ApprovalInfo(
        status,
        groupInfo,
        analysis.satisfiedConstraints().stream()
          .map(c -> new GroupsResource.ConstraintInfo(c.name(), c.displayName()))
          .toList(),
        analysis.unsatisfiedConstraints().stream()
          .map(c -> new GroupsResource.ConstraintInfo(c.name(), c.displayName()))
          .toList(),
        analysis.input().stream()
          .sorted(Comparator.comparing(p -> p.name()))
          .map(GroupsResource.InputInfo::fromProperty)
          .toList());
    }

    static @NotNull ApprovalInfo forApprovedProposal(
      @NotNull JitGroupContext g,
      @NotNull Principal principal,
      @NotNull List<Property> joiningUserInput,
      @NotNull List<Property> input
    ) {
      return new ApprovalInfo(
        ApprovalStatusInfo.APPROVAL_COMPLETED,
        GroupsResource.GroupInfo.create(
          g,
          GroupsResource.JoinInfo.forCompletedJoin(principal, joiningUserInput)),
        List.of(), // Don't repeat constraints
        List.of(), // Don't repeat constraints
        input
          .stream()
          .sorted(Comparator.comparing(p -> p.name()))
          .map(GroupsResource.InputInfo::fromProperty)
          .toList());
    }
  }
}
