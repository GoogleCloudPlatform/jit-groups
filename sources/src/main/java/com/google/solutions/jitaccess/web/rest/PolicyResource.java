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
import com.google.solutions.jitaccess.auth.IamRoleResolver;
import com.google.solutions.jitaccess.catalog.policy.*;
import com.google.solutions.jitaccess.common.Cast;
import com.google.solutions.jitaccess.common.MoreStrings;
import com.google.solutions.jitaccess.web.LogRequest;
import com.google.solutions.jitaccess.web.RequireIapPrincipal;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

@Dependent
@Path("/api")
@RequireIapPrincipal
@LogRequest
public class PolicyResource {
  @Inject
  IamRoleResolver roleResolver;

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
    Preconditions.checkArgument(
      !MoreStrings.isNullOrBlank(source),
      "Source must not be empty");

    PolicyDocument document;

    //
    // 1. Parse and validate document structure.
    //
    try {
      //
      // NB. It's possible that the user is validating that doesn't
      //     explicitly specify a name (because it's implied).
      //
      document = PolicyDocument.parse(
        new PolicyDocumentSource(
          source,
          new Policy.Metadata(
            "user-provided",
            Instant.now(),
            null,
            "anonymous"))); // Accept policies without name.
    }
    catch (PolicyDocument.SyntaxException e) {
      return LintingResultInfo.create(e);
    }

    //
    // 2. Validate role bindings.
    //
    var roleIssues = document.policy()
      .systems()
      .stream()
      .flatMap(s -> s.groups().stream())
      .flatMap(grp -> grp
        .privileges()
        .stream()
        .flatMap(p -> Cast.tryCast(p, IamRoleBinding.class).stream())
        .flatMap(b -> {
          try {
            return this.roleResolver.lintRoleBinding(b.resource(), b.role(), b.condition()).stream();
          }
          catch (IOException ignored) {
            return Stream.of(new IamRoleResolver.LintingIssue("Linting role binding failed"));
          }
        })
        .map(iss -> IssueInfo.fromLintingIssue(grp.name(), iss)))
      .toList();

    if (!roleIssues.isEmpty()) {
      return new LintingResultInfo(false, roleIssues);
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
          .map(IssueInfo::fromPolicyIssue)
          .toList());
    }
  }

  public record IssueInfo(
    boolean error,
    @Nullable String scope,
    @NotNull String code,
    @NotNull String details
  ) {
    static IssueInfo fromPolicyIssue(@NotNull PolicyIssue issue) {
      return new IssueInfo(
        issue.severe(),
        issue.scope(),
        issue.code().toString(),
        issue.details());
    }

    static IssueInfo fromLintingIssue(
      @Nullable String scope,
      @NotNull IamRoleResolver.LintingIssue issue
    ) {
      return new IssueInfo(
        true,
        scope,
        PolicyIssue.Code.PRIVILEGE_INVALID_ROLE.toString(),
        issue.details());
    }
  }
}
