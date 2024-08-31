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

import com.google.solutions.jitaccess.catalog.policy.Policy;
import com.google.solutions.jitaccess.catalog.policy.PolicyDocument;
import com.google.solutions.jitaccess.web.LogRequest;
import com.google.solutions.jitaccess.web.RequireIapPrincipal;
import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

@Dependent
@Path("/api")
@RequireIapPrincipal
@LogRequest
public class PolicyResource {
  /**
   * Validate policy document
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Path("policy/lint")
  public @NotNull LintingResultInfo lint(
    @FormParam("source") @Nullable String source
  ) {
    // TODO: check source != null

    try {
      PolicyDocument.fromString(
        source,
        new Policy.Metadata("user-provided", Instant.now()));
    }
    catch (PolicyDocument.SyntaxException e) {
      return LintingResultInfo.create(e);
    }

    return LintingResultInfo.SUCCESS;
  }

  //---------------------------------------------------------------------------
  // Payload records.
  //---------------------------------------------------------------------------

  public record LintingResultInfo(
    boolean successful,
    @NotNull List<IssueInfo> issues
    ) {
    static final LintingResultInfo SUCCESS = new LintingResultInfo(true, List.of());

    static LintingResultInfo create(@NotNull PolicyDocument.SyntaxException e) {
      return new LintingResultInfo(
        false,
        e.issues()
          .stream()
          .map(IssueInfo::fromIssue)
          .toList());
    }
  }

  public record IssueInfo(
    boolean error,
    @Nullable String scope,
    @NotNull String code,
    @NotNull String details
  ) {
    static IssueInfo fromIssue(@NotNull PolicyDocument.Issue issue) {
      return new IssueInfo(
        issue.error(),
        issue.scope(),
        issue.code().toString(),
        issue.details());
    }
  }
}
