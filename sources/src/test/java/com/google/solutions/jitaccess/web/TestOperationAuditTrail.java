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

package com.google.solutions.jitaccess.web;

import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.auth.EndUserId;
import com.google.solutions.jitaccess.auth.JitGroupId;
import com.google.solutions.jitaccess.auth.Principal;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.policy.PolicyAnalysis;
import com.google.solutions.jitaccess.web.proposal.ProposalHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class TestOperationAuditTrail {
  private static final JitGroupId SAMPLE_JITGROUP = new JitGroupId("env-1", "system-1", "group-1");
  private static final EndUserId SAMPLER_USER_1 = new EndUserId("user-1@example.com");
  private static final EndUserId SAMPLER_USER_2 = new EndUserId("user-2@example.com");
  private static final EndUserId SAMPLER_USER_3 = new EndUserId("user-3@example.com");
  public static final Instant INSTANT_2030 = Instant.ofEpochMilli(1893456000000L);

  private static class LogEntry implements Logger.LogEntry {
    private final @NotNull Map<String, String> labels = new HashMap<>();
    private @Nullable String message;

    @Override
    public @NotNull Logger.LogEntry addLabel(@NotNull String label, @Nullable Object value) {
      this.labels.put(label, value.toString());
      return this;
    }

    @Override
    public @NotNull Logger.LogEntry addLabels(@NotNull Map<String, String> labels) {
      this.labels.putAll(labels);
      return this;
    }

    @Override
    public @NotNull Logger.LogEntry setMessage(@NotNull String message) {
      this.message = message;
      return this;
    }

    @Override
    public @NotNull Logger.LogEntry setMessage(@NotNull String format, Object... args) {
      this.message = new Formatter()
        .format(format, args)
        .toString();
      return this;
    }

    @Override
    public void write() {
    }
  }

  //---------------------------------------------------------------------------
  // constraintFailed.
  //---------------------------------------------------------------------------

  @Test
  public void constraintFailed_whenMultipleExceptions() {
    var entry = new LogEntry();
    var logger = Mockito.mock(Logger.class);
    when(logger.buildError(EventIds.GROUP_CONSTRAINT_FAILED))
      .thenReturn(entry);

    var trail = new OperationAuditTrail(logger);
    trail.constraintFailed(
      SAMPLE_JITGROUP,
      new PolicyAnalysis.ConstraintFailedException(List.of(
        new RuntimeException("one"),
        new RuntimeException("two"))));

    verify(logger, times(2)).buildError(EventIds.GROUP_CONSTRAINT_FAILED);
  }

  @Test
  public void constraintFailed_whenSingleExceptions() {
    var entry = new LogEntry();
    var logger = Mockito.mock(Logger.class);
    when(logger.buildError(EventIds.GROUP_CONSTRAINT_FAILED))
      .thenReturn(entry);

    var trail = new OperationAuditTrail(logger);
    trail.constraintFailed(
      SAMPLE_JITGROUP,
      new PolicyAnalysis.ConstraintFailedException(List.of(new RuntimeException("mock"))));

    assertEquals("audit", entry.labels.get(OperationAuditTrail.LABEL_EVENT_TYPE));
    assertEquals(SAMPLE_JITGROUP.toString(), entry.labels.get(OperationAuditTrail.LABEL_GROUP_ID));
    assertEquals("mock", entry.message);
  }

  //---------------------------------------------------------------------------
  // joinProposed.
  //---------------------------------------------------------------------------

  @Test
  public void joinProposed_whenInputEmpty() {
    var entry = new LogEntry();
    var logger = Mockito.mock(Logger.class);
    when(logger.buildInfo(EventIds.API_JOIN_GROUP))
      .thenReturn(entry);

    var joinOp = Mockito.mock(JitGroupContext.JoinOperation.class);
    when(joinOp.user()).thenReturn(SAMPLER_USER_1);
    when(joinOp.group()).thenReturn(SAMPLE_JITGROUP);
    when(joinOp.input()).thenReturn(List.of());

    var trail = new OperationAuditTrail(logger);
    trail.joinProposed(
      joinOp,
      new ProposalHandler.ProposalToken(
        "token",
        Set.of(SAMPLER_USER_2, SAMPLER_USER_3),
        Instant.now()));

    assertEquals(
      "audit",
      entry.labels.get(OperationAuditTrail.LABEL_EVENT_TYPE));
    assertEquals(
      SAMPLE_JITGROUP.toString(),
      entry.labels.get(OperationAuditTrail.LABEL_GROUP_ID));
    assertEquals(
      "user:user-2@example.com,user:user-3@example.com",
      entry.labels.get(OperationAuditTrail.LABEL_PROPOSAL_RECIPIENTS));
    assertEquals(
      "User 'user:user-1@example.com' asked to join group 'jit-group:env-1.system-1.group-1', " +
        "proposed to 'user:user-2@example.com', 'user:user-3@example.com' for approval",
      entry.message);
  }

  //---------------------------------------------------------------------------
  // joinExecuted.
  //---------------------------------------------------------------------------

  @Test
  public void joinExecuted_whenSelfApproved() {
    var entry = new LogEntry();
    var logger = Mockito.mock(Logger.class);
    when(logger.buildInfo(EventIds.API_JOIN_GROUP))
      .thenReturn(entry);

    var joinOp = Mockito.mock(JitGroupContext.JoinOperation.class);
    when(joinOp.user()).thenReturn(SAMPLER_USER_1);
    when(joinOp.group()).thenReturn(SAMPLE_JITGROUP);
    when(joinOp.input()).thenReturn(List.of());

    var trail = new OperationAuditTrail(logger);
    trail.joinExecuted(
      joinOp,
      new Principal(SAMPLE_JITGROUP, INSTANT_2030));

    assertEquals(
      "audit",
      entry.labels.get(OperationAuditTrail.LABEL_EVENT_TYPE));
    assertEquals(
      SAMPLE_JITGROUP.toString(),
      entry.labels.get(OperationAuditTrail.LABEL_GROUP_ID));
    assertEquals(
      "2030-01-01T00:00Z",
      entry.labels.get(OperationAuditTrail.LABEL_GROUP_EXPIRY));
    assertEquals(
      "User 'user:user-1@example.com' joined group 'jit-group:env-1.system-1.group-1' " +
        "with expiry 2030-01-01T00:00Z, approval was not required",
      entry.message);
  }

  //---------------------------------------------------------------------------
  // joinExecuted.
  //---------------------------------------------------------------------------

  @Test
  public void joinExecuted_whenApproved() {
    var entry = new LogEntry();
    var logger = Mockito.mock(Logger.class);
    when(logger.buildInfo(EventIds.API_APPROVE_JOIN))
      .thenReturn(entry);

    var joinOp = Mockito.mock(JitGroupContext.ApprovalOperation.class);
    when(joinOp.user()).thenReturn(SAMPLER_USER_2);
    when(joinOp.joiningUser()).thenReturn(SAMPLER_USER_1);
    when(joinOp.group()).thenReturn(SAMPLE_JITGROUP);
    when(joinOp.input()).thenReturn(List.of());

    var trail = new OperationAuditTrail(logger);
    trail.joinExecuted(
      joinOp,
      new Principal(SAMPLE_JITGROUP, INSTANT_2030));

    assertEquals(
      "audit",
      entry.labels.get(OperationAuditTrail.LABEL_EVENT_TYPE));
    assertEquals(
      SAMPLE_JITGROUP.toString(),
      entry.labels.get(OperationAuditTrail.LABEL_GROUP_ID));
    assertEquals(
      "2030-01-01T00:00Z",
      entry.labels.get(OperationAuditTrail.LABEL_GROUP_EXPIRY));
    assertEquals(
      "User 'user:user-2@example.com' approved 'user:user-1@example.com' to join group " +
        "'jit-group:env-1.system-1.group-1' with expiry 2030-01-01T00:00Z",
      entry.message);
  }
}
