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
import com.google.solutions.jitaccess.core.auth.GroupId;
import com.google.solutions.jitaccess.core.auth.PrincipalId;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.auth.AccessControlList;
import com.google.solutions.jitaccess.core.catalog.jitrole.JitRole;
import com.google.solutions.jitaccess.core.util.Coalesce;
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
   * @return Create a set of policies based, applying defaults
   * as necessary.
   */
  public @NotNull PolicySet toPolicySet(
    @NotNull ConstraintsNode defaultConstraints // TODO: pass CEL resolver; option: infer defaults from default policy
  ) throws PolicyException {
    var issueCollector = new IssueCollector();
    defaultConstraints.validate(issueCollector, false);
    if (issueCollector.containsError()) {
      throw new PolicyException(
        "The default constraints are incomplete or invalid",
        issueCollector.issues());
    }

    //
    // NB. We already validated the document, so we don't need to do extra
    // validation here anymore.
    //
    assert this.warnings.stream().noneMatch(w -> w.error());

    return new PolicySet(this.nodes.stream().map(
      node -> new Policy(
        node.id,
        node.name,
        node
          .roles
          .stream()
          .map(e -> {
            var effectiveConstraints = e.constraints != null
              ? e.constraints.mergeDefaults(defaultConstraints)
              : defaultConstraints;

            return new Policy.Role(
              new JitRole(node.id, e.id),
              e.name,
              new AccessControlList(e.accessControlEntries
                .stream()
                .map(ace -> ace.toAccessControlListEntry())
                .toList()),
              effectiveConstraints.toConstraints());
          })
          .toList()))
      .collect(Collectors.toUnmodifiableList()));
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
  public static @NotNull PolicyDocument fromString(
    @NotNull String json) throws PolicyException {
    var issueCollector = new IssueCollector();
    try {
      //
      // Check if the document contains an array or policies or a single policy,
      // and parse accordingly.
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

      if (!issueCollector.containsError()) {
        return new PolicyDocument(nodes, issueCollector.issues());
      }
      else {
        throw new PolicyException(
          "The document contains invalid policies",
          issueCollector.issues());
      }
    }
    catch (JsonProcessingException e) {
      issueCollector.add(true, PolicyIssue.Code.FILE_INVALID_SYNTAX, e.getMessage());

      throw new PolicyException(
        "The document is not well-formed JSON",
        issueCollector.issues());
    }
  }

  private static class IssueCollector {
    private final @NotNull List<PolicyIssue> issues = new LinkedList<>();
    private @NotNull String currentContext = "file";

    void setContext(@NotNull String context) {
      this.currentContext = context;
    }

    List<PolicyIssue> issues() {
      return this.issues;
    }

    boolean containsError() {
      return this.issues.stream().anyMatch(i -> i.error());
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
    @JsonProperty("roles") List<RoleNode> roles
  ) {
    private void validate(IssueCollector issues) {
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

      if (this.roles == null || this.roles.isEmpty()) {
        issues.add(
          true,
          PolicyIssue.Code.POLICY_MISSING_ROLES,
          "The policy must contain at least one role");
      }
      else {
        this.roles.stream().forEach(e ->  e.validate(this, issues));
      }
    }
  }

  record RoleNode(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("access") List<AccessControlListEntryNode> accessControlEntries, //TODO: rename to "principals"
    @JsonProperty("constraints") ConstraintsNode constraints
  ) {
    private void validate(PolicyNode parent, IssueCollector issues) {
      if (this.id == null || !ID_PATTERN.matcher(this.id).matches()) {
        issues.add(
          true,
          PolicyIssue.Code.ROLE_INVALID_ID,
          "'%s' is not a valid role ID", this.id);
      }

      issues.setContext(String.format("%s > %s", parent.id, this.id));

      if (this.name() == null || this.name().isBlank()) {
        issues.add(
          true,
          PolicyIssue.Code.ROLE_MISSING_NAME,
          "The role must have a name", this.id);
      }

      if (this.accessControlEntries == null ||
          this.accessControlEntries.isEmpty()) {
        issues.add(
          true,
          PolicyIssue.Code.ROLE_MISSING_ACCESS,
          "The role does not contain any access configuration");
      }
      else {
        this.accessControlEntries.forEach(e -> e.validate(issues));
      }

      if (this.constraints != null) {
        this.constraints.validate(issues, true);
      }
    }
  }

  record AccessControlListEntryNode(
    @JsonProperty("principal") String principalString,
    @JsonProperty("action") String action,
    @JsonProperty("effect") String effect
  ) {
    private static final String ALLOW = "ALLOW";
    private static final String DENY = "DENY";

    private void validate(IssueCollector issues) {
      try {
        principal();
      }
      catch (IllegalArgumentException e) {
        issues.add(
          true,
          PolicyIssue.Code.ACCESS_INVALID_PRINCIPAL,
          "'%s' is not a valid principal identifier, see " +
            "https://cloud.google.com/iam/docs/principal-identifiers for details",
          this.principalString);
      }

      if (this.effect != null &&
        !this.effect.isBlank() &&
        !Set.of(ALLOW, DENY).contains(this.effect.toUpperCase())) {
        issues.add(
          true,
          PolicyIssue.Code.ACCESS_INVALID_EFFECT,
          String.format("The effect must be %s or %s", ALLOW, DENY),
          this.principalString);

      }

      try {
        Policy.RoleAccessRights.parse(this.action);
      }
      catch (IllegalArgumentException e) {
        issues.add(
          true,
          PolicyIssue.Code.ACCESS_INVALID_ACTION,
          String.format("The action '%s' is invalid", this.action),
          this.principalString);
      }
    }

    private PrincipalId principal() {
      if (this.principalString.startsWith(UserId.TYPE + ":")) {
        return new UserId(this.principalString.substring(UserId.TYPE.length() + 1).trim());
      }
      else if (this.principalString.startsWith(GroupId.TYPE + ":")) {
        return new GroupId(this.principalString.substring(GroupId.TYPE.length() + 1).trim());
      }
      else {
        //
        // This should not happen if we've passed validation.
        //
        throw new IllegalArgumentException("Unrecognized principal type");
      }
    }

    private AccessControlList.Entry toAccessControlListEntry() {
      if (this.effect == null || this.effect.isBlank() || this.effect.equalsIgnoreCase(ALLOW)) {
        return new AccessControlList.AllowedEntry(
          this.principal(),
          Policy.RoleAccessRights.parse(this.action));
      }
      else {
        return new AccessControlList.DeniedEntry(
          this.principal(),
          Policy.RoleAccessRights.parse(this.action));
      }
    }
  }

  record ConstraintsNode(
    @JsonProperty("activation_duration") ActivationDurationNode activationDuration,
    @JsonProperty("approval") ApprovalConstraintsNode approvalConstraints
  ) {
    private void validate(IssueCollector issues, boolean allowEmptyValues) {
      if (!allowEmptyValues) {
        if (this.activationDuration == null) {
          issues.add(
            true,
            PolicyIssue.Code.CONSTRAINT_DURATION_CONSTRAINTS_MISSING,
            "`activation_duration` is missing");
        }

        if (this.approvalConstraints == null) {
          issues.add(
            true,
            PolicyIssue.Code.CONSTRAINT_APPROVAL_CONSTRAINTS_MISSING,
            "`approval` is missing");
        }
      }

      if (this.activationDuration != null) {
        this.activationDuration.validate(issues, allowEmptyValues);
      }

      if (this.approvalConstraints != null) {
        this.approvalConstraints.validate(issues, allowEmptyValues);
      }
    }

    private Policy.Constraints toConstraints() {
      var approvalConstraints = this.approvalConstraints != null
        ? new Policy.ApprovalConstraints(
          this.approvalConstraints.minimumNumberOfPeers,
          this.approvalConstraints.maximumNumberOfPeers)
        : new Policy.ApprovalConstraints(null, null);

      return this.activationDuration != null
        ? new Policy.Constraints(
          this.activationDuration().defaultDuration(),
          this.activationDuration().minDuration(),
          this.activationDuration().maxDuration(),
          approvalConstraints)
        : new Policy.Constraints(null, null, null, approvalConstraints);
    }

    private ConstraintsNode mergeDefaults(
      @NotNull ConstraintsNode defaults
    ) {
      return new ConstraintsNode(
        this.activationDuration != null
          ? this.activationDuration.mergeDefaults(defaults.activationDuration)
          : defaults.activationDuration,
        this.approvalConstraints != null
          ? this.approvalConstraints.mergeDefaults(defaults.approvalConstraints)
          : defaults.approvalConstraints);
    }
  }

  record ActivationDurationNode(
    @JsonProperty("default") String defaultString,
    @JsonProperty("min") String minString,
    @JsonProperty("max") String maxString
  ) {
    private static void validateDuration(
      IssueCollector issues,
      String duration,
      String description,
      boolean allowEmptyValues
    ) {
      if (duration == null || duration.isBlank()) {
        if (!allowEmptyValues) {
          issues.add(
            true,
            PolicyIssue.Code.CONSTRAINT_DURATION_CONSTRAINT_EMPTY,
            "`%s` is missing or empty", description);
        }
      }
      else {
        try {
          Duration.parse(duration);
        }
        catch (DateTimeParseException e) {
          issues.add(
            true,
            PolicyIssue.Code.CONSTRAINT_DURATION_CONSTRAINT_INVALID,
            "`%s` contains an invalid value: %s", description, e.getMessage());
        }
      }
    }

    private void validate(IssueCollector issues, boolean allowEmptyValues) {
      validateDuration(issues, this.defaultString, "activation_duration.default", allowEmptyValues);
      validateDuration(issues, this.minString, "activation_duration.minimum", allowEmptyValues);
      validateDuration(issues, this.maxString, "activation_duration.maximum", allowEmptyValues);
    }

    @Nullable Duration defaultDuration() {
      return defaultString != null
        ? Duration.parse(this.defaultString)
        : null;
    }

    @Nullable Duration minDuration() {
      return minString != null
        ? Duration.parse(this.minString)
        : null;
    }

    @Nullable Duration maxDuration() {
      return maxString != null
        ? Duration.parse(this.maxString)
        : null;
    }

    private ActivationDurationNode mergeDefaults(
      @NotNull ActivationDurationNode defaults
    ) {
      return new ActivationDurationNode(
        Coalesce.nonEmpty(this.defaultString, defaults.defaultString),
        Coalesce.nonEmpty(this.minString, defaults.minString),
        Coalesce.nonEmpty(this.maxString, defaults.maxString));
    }
  }

  record ApprovalConstraintsNode(
    @JsonProperty("minimum_peers_to_notify") Integer minimumNumberOfPeers,
    @JsonProperty("maximum_peers_to_notify") Integer maximumNumberOfPeers
  ) {
    private void validate(IssueCollector issues, boolean allowEmptyValues) {
      if (!allowEmptyValues) {
        if (this.minimumNumberOfPeers == null) {
          issues.add(
            true,
            PolicyIssue.Code.CONSTRAINT_APPROVAL_LIMITS_MISSING,
            "`minimum_peers_to_notify` is missing or empty");
        }

        if (this.maximumNumberOfPeers == null) {
          issues.add(
            true,
            PolicyIssue.Code.CONSTRAINT_APPROVAL_LIMITS_MISSING,
            "`maximum_peers_to_notify` is missing or empty");
        }
      }

      if (this.minimumNumberOfPeers != null && this.minimumNumberOfPeers < 1) {
        issues.add(
          true,
          PolicyIssue.Code.CONSTRAINT_APPROVAL_LIMITS_INVALID,
          "`minimum_peers_to_notify` must be greater than 0");
      }

      if (this.maximumNumberOfPeers != null && this.maximumNumberOfPeers < 1) {
        issues.add(
          true,
          PolicyIssue.Code.CONSTRAINT_APPROVAL_LIMITS_INVALID,
          "`maximum_peers_to_notify` must be greater than 0");
      }

      if (this.minimumNumberOfPeers != null &&
        this.maximumNumberOfPeers != null &&
        this.maximumNumberOfPeers < this.minimumNumberOfPeers) {
        issues.add(
          true,
          PolicyIssue.Code.CONSTRAINT_APPROVAL_LIMITS_INVALID,
          "`maximum_peers_to_notify` must be greater than `minimum_peers_to_notify`");
      }
    }

    private ApprovalConstraintsNode mergeDefaults(
      @NotNull ApprovalConstraintsNode defaults
    ) {
      return new ApprovalConstraintsNode(
        Coalesce.objects(this.minimumNumberOfPeers, defaults.minimumNumberOfPeers),
        Coalesce.objects(this.maximumNumberOfPeers, defaults.maximumNumberOfPeers));
    }
  }
}
