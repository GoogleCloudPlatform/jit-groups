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

import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.catalog.auth.IamPrincipalId;
import com.google.solutions.jitaccess.catalog.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.auth.Principal;
import com.google.solutions.jitaccess.catalog.policy.PolicyAnalysis;
import com.google.solutions.jitaccess.util.Exceptions;
import com.google.solutions.jitaccess.web.EventIds;
import com.google.solutions.jitaccess.web.proposal.ProposalHandler;
import jakarta.enterprise.context.Dependent;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

/**
 * Audit trail for group operations.
 */
@Dependent
class OperationAuditTrail {
  private @NotNull Logger logger;

  private static String LABEL_GROUP_ID = "group/id";
  private static String LABEL_GROUP_EXPIRY = "group/expiry";
  private static String LABEL_PREFIX_JOIN_INPUT = "join/input/";
  private static String LABEL_PREFIX_PROPOSAL_INPUT = "proposal/input/";
  private static String LABEL_PREFIX_PROPOSAL_RECIPIENTS = "proposal/recipients/";
  private static String LABEL_EVENT_TYPE = "event/type";

  public OperationAuditTrail(@NotNull Logger logger) {
    this.logger = logger;
  }

  void constraintFailed(
    @NotNull JitGroupId groupId,
    @NotNull PolicyAnalysis.ConstraintFailedException e
  ) {
    //
    // A failed constraint indicates a configuration issue, so
    // log all the details.
    //
    for (var detail : e.exceptions()) {
      this.logger.buildError(EventIds.GROUP_CONSTRAINT_FAILED)
        .addLabel(LABEL_EVENT_TYPE, "audit")
        .addLabel(LABEL_GROUP_ID, groupId)
        .setMessage(Exceptions.fullMessage(detail))
        .write();
    }
  }

  void joinProposed(
    @NotNull JitGroupContext.JoinOperation joinOp,
    @NotNull ProposalHandler.ProposalToken proposal
  ) {
    this.logger.buildInfo(EventIds.API_JOIN_GROUP)
      .addLabel(LABEL_EVENT_TYPE, "audit")
      .addLabel(LABEL_GROUP_ID, joinOp.group())
      .addLabel(LABEL_PREFIX_PROPOSAL_RECIPIENTS, proposal.audience()
        .stream()
        .map(IamPrincipalId::toString)
        .collect(Collectors.joining(",")))
      .addLabels(joinOp.input()
        .stream()
        .collect(Collectors.toMap(i -> LABEL_PREFIX_JOIN_INPUT + i.name(), i -> i.get())))
      .setMessage(
        "User '%s' asked to join group '%s', proposed to %s for approval",
        joinOp.user(),
        joinOp.group(),
        proposal.audience()
          .stream()
          .map(IamPrincipalId::toString)
          .collect(Collectors.joining(", ")))
      .write();
  }

  void joinExecuted(
    @NotNull JitGroupContext.JoinOperation joinOp,
    @NotNull Principal principal
  ) {
    this.logger.buildInfo(EventIds.API_JOIN_GROUP)
      .addLabel(LABEL_EVENT_TYPE, "audit")
      .addLabel(LABEL_GROUP_ID,  joinOp.group())
      .addLabel(LABEL_GROUP_EXPIRY, principal.expiry()
        .atOffset(ZoneOffset.UTC)
        .truncatedTo(ChronoUnit.SECONDS))
      .addLabels(joinOp.input()
        .stream()
        .collect(Collectors.toMap(i -> LABEL_PREFIX_JOIN_INPUT + i.name(), i -> i.get())))
      .setMessage(
        "User '%s' joined group '%s' with expiry %s, approval was not required",
        joinOp.user(),
        joinOp.group(),
        principal.expiry().atZone(ZoneOffset.UTC).toString())
      .write();
  }


  void joinExecuted(
    @NotNull JitGroupContext.ApprovalOperation approveOp,
    @NotNull Principal principal
  ) {
    this.logger.buildInfo(EventIds.API_APPROVE_JOIN)
      .addLabel(LABEL_EVENT_TYPE, "audit")
      .addLabel(LABEL_GROUP_ID, approveOp.group())
      .addLabel(LABEL_GROUP_EXPIRY, principal.expiry()
        .atOffset(ZoneOffset.UTC)
        .truncatedTo(ChronoUnit.SECONDS))
      .addLabels(approveOp.input()
        .stream()
        .collect(Collectors.toMap(i -> LABEL_PREFIX_PROPOSAL_INPUT + i.name(), i -> i.get())))
      .addLabels(approveOp.joiningUserInput()
        .stream()
        .collect(Collectors.toMap(i -> LABEL_PREFIX_JOIN_INPUT + i.name(), i -> i.get())))
      .setMessage(
        "User '%s' approved proposal of '%s' to join group '%s' with expiry %s",
        approveOp.user(),
        approveOp.joiningUser(),
        approveOp.group(),
        principal.expiry().atZone(ZoneOffset.UTC).toString())
      .write();
  }
}
