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

package com.google.solutions.jitaccess.core.catalog.policy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.solutions.jitaccess.core.auth.GroupEmail;
import com.google.solutions.jitaccess.core.auth.PrincipalIdentifier;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.auth.AccessControlList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parsed representation of a JSON-formatted policy document.
 * A document can contain one or more policies.
 */
public class PolicyDocument {
  private static Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9\\-_]{1,32}$");

  private final @NotNull List<PolicyNode> nodes;
  private final @NotNull List<PolicyIssue> warnings;

  /**
   * @return list of warnings encountered when parsing the policy
   */
  public @NotNull List<PolicyIssue> warnings() {
    return this.warnings;
  }

  /**
   * @return list of policy nodes defined in this document.
   */
  @NotNull List<PolicyNode> nodes() {
    return this.nodes;
  }

  /**
   * @return list of policies defined in this document.
   */
  public @NotNull List<Policy> policies() {
    //
    // NB. We already validated the document, so we don't need to do extra
    // validation here anymore.
    //
    assert this.warnings.stream().noneMatch(w -> w.error());

    return this.nodes.stream().map(
      node -> new Policy(
        node.id,
        node.name,
        node
          .entitlements
          .stream()
          .map(e -> {
            Policy.ApprovalRequirement approvalRequirement;
            if (e.requirements != null && e.requirements.peerApproval != null) {
              approvalRequirement = new Policy.PeerApprovalRequirement(
                e.requirements.peerApproval.minimumNumberOfPeers,
                e.requirements.peerApproval.maximumNumberOfPeers);
            }
            else {
              approvalRequirement = new Policy.SelfApprovalRequirement();
            }

            return new Policy.Entitlement(
              e.id,
              e.name,
              e.expiry(),
              new AccessControlList(e.eligible.principals()),
              approvalRequirement);
          })
          .toList()))
      .collect(Collectors.toUnmodifiableList());
  }

  private PolicyDocument(
    @Nullable List<PolicyNode> nodes,
    @NotNull List<PolicyIssue> warnings
  ) {
    this.nodes = nodes;
    this.warnings = warnings;
  }

  /**
   * Parse a JSON-formatted policy document.
   */
  public static @NotNull PolicyDocument fromString(String json) throws PolicyException {
    var issueCollector = new IssueCollector();
    try {
      //
      // Parse the JSON.
      //
      List<PolicyNode> nodes;
      if (json.stripLeading().startsWith("[")) {
        nodes = Arrays.asList(new ObjectMapper().readValue(json, PolicyNode[].class));
      }
      else {
        nodes = List.of(new ObjectMapper().readValue(json, PolicyNode.class));
      }

      if (nodes.isEmpty()) {
        issueCollector.add(
          true,
          PolicyIssue.Code.FILE_INVALID_SYNTAX,
          "The document does not contain any policies");
      }
      else if (!nodes.stream().map(p -> p.id()).allMatch(new HashSet<String>()::add)) {
        issueCollector.add(
          true,
          PolicyIssue.Code.POLICY_DUPLICATE_ID,
          "The document contains multiple policies with the same ID");
      }
      else {
        //
        // The policy is syntactically correct. Now check semantics.
        //
        nodes.forEach(n -> n.validate(issueCollector));
      }

      if (issueCollector.getIssues().stream().noneMatch(i -> i.error())) {
        return new PolicyDocument(nodes, issueCollector.getIssues());
      }
      else {
        throw new PolicyException(
          "The document contains invalid policies",
          issueCollector.getIssues());
      }
    }
    catch (JsonProcessingException e) {
      issueCollector.add(true, PolicyIssue.Code.FILE_INVALID_SYNTAX, e.getMessage());

      throw new PolicyException(
        "The document is not well-formed JSON",
        issueCollector.getIssues());
    }
  }

  private static class IssueCollector {
    private final @NotNull List<PolicyIssue> issues = new LinkedList<>();
    private @NotNull String currentContext = "file";

    void setContext(@NotNull String context) {
      this.currentContext = context;
    }

    List<PolicyIssue> getIssues() {
      return issues;
    }

    void add(
      boolean error,
      @NotNull PolicyIssue.Code code,
      @NotNull String format,
      Object... args
    ) {
      var description = new Formatter()
        .format(format, args)
        .toString();

      this.issues.add(new PolicyIssue(
        error,
        code,
        String.format("[%s] %s", this.currentContext, description)));
    }
  }

  //---------------------------------------------------------------------------
  // Serialization.
  //---------------------------------------------------------------------------

  record PolicyNode(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("entitlements") List<EntitlementsNode> entitlements
  ) {
    void validate(IssueCollector issues) {
      if (this.id == null || !ID_PATTERN.matcher(this.id).matches()) {
        issues.add(
          true,
          PolicyIssue.Code.POLICY_INVALID_ID,
          "'%s' is not a valid policy ID", this.id);
      }
      else {
        issues.setContext(this.id);
      }

      if (this.name() == null || this.name().isBlank()) {
        issues.add(
          true,
          PolicyIssue.Code.POLICY_MISSING_NAME,
          "The policy must have a name");
      }

      if (this.entitlements == null || this.entitlements.isEmpty()) {
        issues.add(
          true,
          PolicyIssue.Code.POLICY_MISSING_ENTITLEMENTS,
          "The policy must contain at least one entitlement");
      }
      else {
        this.entitlements.stream().forEach(e ->  e.validate(this, issues));
      }
    }
  }

  record EntitlementsNode(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("expires_after") String expiryString,
    @JsonProperty("eligible") EligibleNode eligible,
    @JsonProperty("requirements") RequirementsNode requirements
  ) {
    void validate(PolicyNode parent, IssueCollector issues) {
      if (this.id == null || !ID_PATTERN.matcher(this.id).matches()) {
        issues.add(
          true,
          PolicyIssue.Code.ENTITLEMENT_INVALID_ID,
          "'%s' is not a valid entitlement ID", this.id);
      }

      issues.setContext(String.format("%s > %s", parent.id, this.id));

      if (this.name() == null || this.name().isBlank()) {
        issues.add(
          true,
          PolicyIssue.Code.ENTITLEMENT_MISSING_NAME,
          "The entitlement must have a name", this.id);
      }

      if (this.expiryString == null || this.expiryString.isBlank()) {
        issues.add(
          true,
          PolicyIssue.Code.ENTITLEMENT_INVALID_EXPIRY,
          "The entitlement must have an expiry");
      }
      else {
        try {
          Duration.parse(this.expiryString);
        }
        catch (DateTimeParseException e) {
          issues.add(
            true,
            PolicyIssue.Code.ENTITLEMENT_INVALID_EXPIRY,
            "The expiry is invalid: %s", e.getMessage());
        }
      }

      if (this.eligible == null ||
          this.eligible.principalStrings == null ||
          this.eligible.principalStrings.isEmpty()) {
        issues.add(
          false,
          PolicyIssue.Code.ENTITLEMENT_MISSING_ELIGIBLE_PRINCIPALS,
          "The entitlement does not contain any eligible principals");
      }
      else {
        this.eligible.validate(issues);
      }

      if (this.requirements != null) {
        this.requirements.validate(issues);
      }
    }

    Duration expiry() {
      return Duration.parse(this.expiryString);
    }
  }

  record EligibleNode(
    @JsonProperty("principals") List<String> principalStrings
  ) {
    void validate(IssueCollector issues) {
      for (var principal : principalStrings) {
        if (!principal.startsWith(UserEmail.TYPE) &&
            !principal.startsWith(GroupEmail.TYPE)) {
          issues.add(
            true,
            PolicyIssue.Code.PRINCIPAL_INVALID,
            "'%s' is not a valid principal identifier, see " +
            "https://cloud.google.com/iam/docs/principal-identifiers for details",
            principal);
        }
      }
    }

    Set<PrincipalIdentifier> principals() {
      var principals = new HashSet<PrincipalIdentifier>();

      for (var s : this.principalStrings) {
        if (s.startsWith(UserEmail.TYPE + ":")) {
          principals.add(new UserEmail(s.substring(UserEmail.TYPE.length() + 1)));
        }
        else if (s.startsWith(GroupEmail.TYPE + ":")) {
          principals.add(new GroupEmail(s.substring(GroupEmail.TYPE.length() + 1)));
        }
      }

      return principals;
    }
  }

  record RequirementsNode(
    @JsonProperty("requirePeerApproval") RequirePeerApprovalNode peerApproval
  ) {
    void validate(IssueCollector issues) {
      if (this.peerApproval != null) {
        this.peerApproval.validate(issues);
      }
    }
  }

  record RequirePeerApprovalNode(
    @JsonProperty("minimum_peers_to_notify") Integer minimumNumberOfPeers,
    @JsonProperty("maximum_peers_to_notify") Integer maximumNumberOfPeers
  ) {
    void validate(IssueCollector issues) {
      if (minimumNumberOfPeers != null && minimumNumberOfPeers < 1) {
        issues.add(
          true,
          PolicyIssue.Code.PEER_APPROVAL_CONSTRAINTS_INVALID,
          "minimum_peers_to_notify must be greater than 0");
      }

      if (maximumNumberOfPeers != null && maximumNumberOfPeers < minimumNumberOfPeers) {
        issues.add(
          true,
          PolicyIssue.Code.PEER_APPROVAL_CONSTRAINTS_INVALID,
          "maximum_peers_to_notify must be greater than minimum_peers_to_notify");
      }
    }
  }
}
