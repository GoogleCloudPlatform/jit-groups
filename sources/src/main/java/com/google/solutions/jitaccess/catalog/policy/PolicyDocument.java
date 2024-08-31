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

package com.google.solutions.jitaccess.catalog.policy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.PropertyBindingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.Strings;
import com.google.solutions.jitaccess.apis.*;
import com.google.solutions.jitaccess.catalog.auth.GroupId;
import com.google.solutions.jitaccess.catalog.auth.PrincipalId;
import com.google.solutions.jitaccess.catalog.auth.UserClassId;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.util.Coalesce;
import com.google.solutions.jitaccess.util.Exceptions;
import com.google.solutions.jitaccess.util.MoreStrings;
import com.google.solutions.jitaccess.util.NullaryOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * YAML representation of an environment policy.
 */
public class PolicyDocument {
  public static final int CURRENT_VERSION = 1;

  private final @NotNull List<Issue> warnings;
  private final @NotNull EnvironmentPolicy policy;

  private PolicyDocument(@NotNull EnvironmentPolicy policy, @NotNull List<Issue> warnings) {
    this.warnings = warnings;
    this.policy = policy;
  }

  public PolicyDocument(@NotNull EnvironmentPolicy policy) {
    this(policy, List.of());
  }

  /**
   * List of warnings encountered when parsing the policy
   */
  public @NotNull List<Issue> warnings() {
    return this.warnings;
  }

  /**
   * Decoded policy.
   */
  public EnvironmentPolicy policy() {
    return this.policy;
  }

  /**
   * Convert policy to a YAML string.
   */
  @Override
  public String toString() {
    try {
      return new YAMLMapper()
        .writer()
        .without(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        .writeValueAsString(DocumentElement.toYaml(this.policy));
    } catch (JsonProcessingException e) {
      return "(invalid)";
    }
  }

  //---------------------------------------------------------------------------
  // Parsing.
  //---------------------------------------------------------------------------

  private static @NotNull PolicyDocument parse(
    @NotNull Callable<DocumentElement> parser,
    @NotNull Policy.Metadata metadata
  ) throws SyntaxException {
    var issues = new IssueCollection();

    Optional<EnvironmentPolicy> policy;
    try {
      //
      // Parse YAML and validate it.
      //
      policy = parser
        .call()
        .toPolicy(issues, metadata);

    }
    catch (PropertyBindingException e) {
      //
      // Use friendly error and void mentioning class names.
      //
      issues.error(
        Issue.Code.FILE_UNKNOWN_PROPERTY,
        String.format("Unrecognized field '%s' at %s (Valid fields are %s)",
          e.getPropertyName(),
          e.getLocation().offsetDescription(),
          e.getKnownPropertyIds()));
      throw new SyntaxException("The policy document is malformed", issues.issues);
    }
    catch (JsonMappingException e) {
      //
      // Use friendly error and void mentioning class names.
      //
      issues.error(
        Issue.Code.FILE_UNKNOWN_PROPERTY,
        String.format("Unrecognized field at %s", e.getLocation().offsetDescription()));
      throw new SyntaxException("The policy document is malformed", issues.issues);
    }
    catch (JsonProcessingException e) {
      //
      // The exception messages tend to be very convoluted, so only
      // use the innermost exception.
      //
      var cause = Exceptions.rootCause(e);

      issues.error(Issue.Code.FILE_INVALID_SYNTAX, cause.getMessage());
      throw new SyntaxException("The policy document is malformed", issues.issues);
    }
    catch (Exception e) {
      issues.error(Issue.Code.FILE_INVALID, e.getMessage());
      throw new SyntaxException("Parsing the policy document failed", issues.issues);
    }

    //
    // If there were any validation errors, we should
    // have received an empty result.
    //
    assert (policy.isPresent() || issues.containsErrors());

    if (policy.isEmpty() || issues.containsErrors()) {
      throw new SyntaxException(
        "The policy document contains errors",
        issues.issues());
    }

    return new PolicyDocument(policy.get(), issues.issues);
  }

  /**
   * Parse a YAML-formatted document.
   *
   * @throws SyntaxException if the document is invalid
   * @return the parsed and validated document, including warnings that may
   *         have been encountered
   */
  public static @NotNull PolicyDocument fromString(
    @NotNull String yaml,
    @NotNull Policy.Metadata metadata) throws SyntaxException {
    return parse(
      () -> new YAMLMapper().readValue(yaml, DocumentElement.class),
      metadata);
  }

  /**
   * Parse a YAML-formatted document.
   *
   * @throws SyntaxException if the document is invalid
   * @return the parsed and validated document, including warnings that may
   *         have been encountered
   */
  static @NotNull PolicyDocument fromString(@NotNull String yaml) throws SyntaxException {
    return fromString(
      yaml,
      new Policy.Metadata(
        "memory",
        Instant.now()));
  }

  /**
   * Parse a YAML-formatted document.
   *
   * @throws SyntaxException if the document is invalid
   * @return the parsed and validated document, including warnings that may
   *         have been encountered
   */
  public static @NotNull PolicyDocument fromFile(@NotNull File file) throws SyntaxException, IOException {
    if (!file.exists()) {
      throw new FileNotFoundException(
        String.format("The file '%s' does not exist", file.getAbsolutePath()));
    }

    return parse(
      () -> new YAMLMapper().readValue(file, DocumentElement.class),
      new Policy.Metadata(
        file.getName(),
        Instant.ofEpochMilli(file.lastModified())));
  }

  /**
   * Warning or error affecting a policy.
   *
   * @param error indicates if this is a fatal error
   * @param scope scope in which the issue was encountered
   * @param code unique code for the issue
   * @param details textual description
   */
  public record Issue(
    boolean error,
    @Nullable String scope,
    @NotNull Code code,
    @NotNull String details) {

    public enum Code {
      FILE_INVALID,
      FILE_INVALID_SYNTAX,
      FILE_INVALID_VERSION,
      FILE_UNKNOWN_PROPERTY,
      ENVIRONMENT_MISSING,
      ENVIRONMENT_INVALID,
      SYSTEM_INVALID,
      GROUP_INVALID,
      ACL_INVALID_PRINCIPAL,
      ACL_INVALID_PERMISSION,
      CONSTRAINT_INVALID_VARIABLE_DECLARATION,
      CONSTRAINT_INVALID_TYPE,
      CONSTRAINT_INVALID_EXPIRY,
      CONSTRAINT_INVALID_EXPRESSION,
      PRIVILEGE_INVALID_RESOURCE_ID,
      PRIVILEGE_DUPLICATE_RESOURCE_ID,
      PRIVILEGE_INVALID_ROLE,
    }

    @Override
    public String toString() {
      return String.format(
        "%s %s: %s",
        this.error ? "ERROR" : "WARNING",
        this.code,
        this.details);
    }
  }

  /**
   * Accumulator for issues encountered during parsing.
   */
  static class IssueCollection {
    private final @NotNull List<Issue> issues = new LinkedList<>();
    private @NotNull String currentScope = "file";

    void setScope(@NotNull String context) {
      this.currentScope = context;
    }

    List<Issue> issues() {
      return this.issues;
    }

    boolean containsErrors() {
      return this.issues.stream().anyMatch(i -> i.error());
    }

    private void error(
      @NotNull PolicyDocument.Issue.Code code,
      @NotNull String format,
      Object... args
    ) {
      var description = new Formatter()
        .format(format, args)
        .toString();

      this.issues.add(new Issue(
        true,
        this.currentScope,
        code,
        description));
    }

    private void warning(
      @NotNull PolicyDocument.Issue.Code code,
      @NotNull String format,
      Object... args
    ) {
      var description = new Formatter()
        .format(format, args)
        .toString();

      this.issues.add(new Issue(
        false,
        this.currentScope,
        code,
        description));
    }
  }

  public static class SyntaxException extends Exception {
    private final @NotNull List<PolicyDocument.Issue> issues;

    public @NotNull List<PolicyDocument.Issue> issues() {
      return this.issues;
    }

    SyntaxException(
      @NotNull String message,
      @NotNull List<PolicyDocument.Issue> issues
    ) {
      super(message);
      this.issues = issues;
    }

    @Override
    public String getMessage() {
      var buffer = new StringBuilder();
      buffer.append(super.getMessage());
      buffer.append(' ');

      int numeral = 1;
      for (var issue : this.issues) {
        buffer.append("[");
        buffer.append(numeral++);
        buffer.append("] ");
        buffer.append(issue);
      }

      return buffer.toString();
    }
  }

  //---------------------------------------------------------------------------
  // Serialization records.
  //---------------------------------------------------------------------------

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record DocumentElement(
    @JsonProperty("schemaVersion") Integer schemaVersion,
    @JsonProperty("environment") EnvironmentElement environment
  ) {
    private static DocumentElement toYaml(
      @NotNull EnvironmentPolicy policy
    ) {
      return new DocumentElement(
        CURRENT_VERSION,
        EnvironmentElement.toYaml(policy));
    }

    private @NotNull Optional<EnvironmentPolicy> toPolicy(
      @NotNull IssueCollection issues,
      @NotNull Policy.Metadata metadata
    ) {
      boolean schemaVersionValid;
      if (this.schemaVersion == null) {
        issues.error(
          Issue.Code.FILE_INVALID_VERSION,
          "The file must specify a schema version");
        schemaVersionValid = false;
      }
      else if (this.schemaVersion != CURRENT_VERSION) {
        issues.error(
          Issue.Code.FILE_INVALID_VERSION,
          "The schema version is not supported");
        schemaVersionValid = false;
      }
      else {
        schemaVersionValid = true;
      }

      if (this.environment == null) {
        issues.error(
          Issue.Code.ENVIRONMENT_MISSING,
          "The file must contain an environment");
      }

      return NullaryOptional
        .ifTrue(schemaVersionValid && this.environment != null)
        .flatMap(() -> this.environment.toPolicy(issues, metadata));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record EnvironmentElement(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("access") List<AccessControlEntryElement> acl,
    @JsonProperty("constraints") ConstraintsElement constraints,
    @JsonProperty("systems") List<SystemElement> systems
  ) {

    static EnvironmentElement toYaml(
      @NotNull EnvironmentPolicy policy
    ) {
      return new EnvironmentElement(
        policy.name(),
        policy.description(),
        policy.accessControlList()
          .map(acl -> acl
            .entries()
            .stream()
            .map(AccessControlEntryElement::toYaml)
            .toList())
          .orElse(null),
        ConstraintsElement.toYaml(policy.constraints()),
        policy
          .systems()
          .stream()
          .map(SystemElement::toYaml)
          .toList());
    }

    @NotNull Optional<EnvironmentPolicy> toPolicy(
      @NotNull IssueCollection issues,
      @NotNull Policy.Metadata metadata) {
      issues.setScope(Coalesce.nonEmpty(this.name, "Unnamed environment"));

      var systems = Coalesce
        .emptyIfNull(this.systems)
        .stream()
        .map(s -> s.toPolicy(issues))
        .toList();

      var aces = Coalesce
        .emptyIfNull(this.acl)
        .stream()
        .map(e -> e.toPolicy(issues))
        .toList();

      var constraints = (this.constraints != null ? this.constraints : ConstraintsElement.EMPTY)
        .toPolicy(issues);

      return NullaryOptional
        .ifTrue(
          constraints.isPresent() &&
          systems.stream().allMatch(Optional::isPresent) &&
          aces.stream().allMatch(Optional::isPresent))
        .map(() -> {
            try {
              var policy = new EnvironmentPolicy(
                Coalesce.nonEmpty(this.name, metadata.defaultName()),
                Strings.nullToEmpty(this.description),
                this.acl == null
                  ? EnvironmentPolicy.DEFAULT_ACCESS_CONTROL_LIST
                  : new AccessControlList(aces.stream().map(Optional::get).toList()),
                constraints.get(),
                metadata);

              systems
                .stream()
                .map(Optional::get)
                .forEach(policy::add);

              return policy;
            }
            catch (Exception e) {
              issues.error(
                Issue.Code.ENVIRONMENT_INVALID,
                "The environment configuration is invalid: %s",
                e.getMessage());
              return null;
            }
          });
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SystemElement(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("access") List<AccessControlEntryElement> acl,
    @JsonProperty("constraints") ConstraintsElement constraints,
    @JsonProperty("groups") List<GroupElement> groups
  ) {
    static SystemElement toYaml(@NotNull SystemPolicy policy) {
      return new SystemElement(
        policy.name(),
        policy.description(),
        policy.accessControlList()
          .map(acl -> acl
            .entries()
            .stream()
            .map(AccessControlEntryElement::toYaml)
            .toList())
          .orElse(null),
        ConstraintsElement.toYaml(policy.constraints()),
        policy
          .groups()
          .stream()
          .map(GroupElement::toYaml)
          .toList());
    }

    @NotNull Optional<SystemPolicy> toPolicy(@NotNull IssueCollection issues) {
      issues.setScope(Coalesce.nonEmpty(this.name, "Unnamed system"));

      var groups = Coalesce
        .emptyIfNull(this.groups)
        .stream()
        .filter(s -> s != null)
        .map(s -> s.toPolicy(issues))
        .toList();

      var aces = Coalesce
        .emptyIfNull(this.acl)
        .stream()
        .map(e -> e.toPolicy(issues))
        .toList();

      var constraints = (this.constraints != null ? this.constraints : ConstraintsElement.EMPTY)
        .toPolicy(issues);

      return NullaryOptional
        .ifTrue(
          constraints.isPresent() &&
          groups.stream().allMatch(Optional::isPresent)&&
          aces.stream().allMatch(Optional::isPresent))
        .map(() -> {
          try {
            var policy = new SystemPolicy(
              this.name,
              Strings.nullToEmpty(this.description),
              this.acl == null
                ? null
                : new AccessControlList(aces.stream().map(Optional::get).toList()),
              constraints.get());

            groups
              .stream()
              .map(Optional::get)
              .forEach(policy::add);

            return policy;
          }
          catch (Exception e) {
            issues.error(
              Issue.Code.SYSTEM_INVALID,
              "The system configuration is invalid: %s",
              e.getMessage());
            return null;
          }
        });
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record GroupElement(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("access") List<AccessControlEntryElement> acl,
    @JsonProperty("constraints") ConstraintsElement constraints,
    @JsonProperty("privileges") PrivilegesElement privileges
  ) {

    static GroupElement toYaml(@NotNull JitGroupPolicy policy) {
      return new GroupElement(
        policy.name(),
        policy.description(),
        policy.accessControlList()
          .map(acl -> acl
            .entries()
            .stream()
            .map(AccessControlEntryElement::toYaml)
            .toList())
          .orElse(null),
        ConstraintsElement.toYaml(policy.constraints()),
        new PrivilegesElement(
          policy.privileges()
            .stream()
            .filter(p -> p instanceof IamRoleBinding)
            .map(p -> IamRoleBindingElement.toYaml((IamRoleBinding)p))
            .toList()));
    }

    @NotNull Optional<JitGroupPolicy> toPolicy(@NotNull IssueCollection issues) {
      issues.setScope(Coalesce.nonEmpty(this.name, "Unnamed group"));

      var aces = Coalesce
        .emptyIfNull(this.acl)
        .stream()
        .map(e -> e.toPolicy(issues))
        .toList();

      var constraints = (this.constraints != null ? this.constraints : ConstraintsElement.EMPTY)
        .toPolicy(issues);

      var roleBindings = Optional.ofNullable(this.privileges)
        .flatMap(p -> Optional.ofNullable(p.iamRoleBindings()))
        .stream()
        .flatMap(b -> b.stream())
        .map(b -> b.toPolicy(issues))
        .toList();

      return NullaryOptional
        .ifTrue(
          constraints.isPresent() &&
          aces.stream().allMatch(Optional::isPresent) &&
          roleBindings.stream().allMatch(Optional::isPresent))
        .map(() -> {
          try {
            return new JitGroupPolicy(
              this.name,
              this.description,
              new AccessControlList(aces.stream().map(Optional::get).toList()),
              constraints.get(),
              roleBindings
                .stream()
                .map(Optional::get)
                .map(b -> (Privilege)b)
                .toList());
          }
          catch (Exception e) {
            issues.error(
              Issue.Code.GROUP_INVALID,
              "The group configuration is invalid: %s",
              e.getMessage());
            return null;
          }
        });
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AccessControlEntryElement(
    @JsonProperty("principal") String principal,
    @JsonProperty("allow") String allowedPermissions,
    @JsonProperty("deny") String deniedPermissions
  ) {
    static AccessControlEntryElement toYaml(@NotNull AccessControlList.Entry ace) {
      return new AccessControlEntryElement(
        String.format("%s:%s", ace.principal.type(), ace.principal.value()),
        ace instanceof AccessControlList.AllowedEntry
          ? PolicyPermission.toString(PolicyPermission.fromMask(ace.accessRights)) : null,
        ace instanceof AccessControlList.DeniedEntry
          ? PolicyPermission.toString(PolicyPermission.fromMask(ace.accessRights)) : null);
    }

    @NotNull Optional<AccessControlList.Entry> toPolicy(@NotNull IssueCollection issues) {
      //
      // Parse principal ID.
      //
      var principalId = Optional
        .ofNullable(this.principal)
        .map(s -> {
          try {
            return Optional.<PrincipalId>empty()
              .or(() -> UserId.parse(s))
              .or(() -> GroupId.parse(s))
              .or(() -> UserClassId.parse(s))
              .orElse(null);
          }
          catch (IllegalArgumentException e) {
            return null;
          }
        });

      if (principalId.isEmpty()) {
        issues.error(
          Issue.Code.ACL_INVALID_PRINCIPAL,
          "The principal '%s' is invalid",
          this.principal);
      }

      //
      // Parse access mask.
      //
      var allowedMask = Optional
        .ofNullable(this.allowedPermissions)
        .map(p -> {
          try {
            return PolicyPermission.toMask(PolicyPermission.parse(p));
          }
          catch (IllegalArgumentException e) {
            issues.error(
              Issue.Code.ACL_INVALID_PERMISSION,
              "The specified permissions are invalid: %s",
              e.getMessage());

            return null;
          }
        });

      var deniedMask = Optional
        .ofNullable(this.deniedPermissions)
        .map(p -> {
          try {
            return PolicyPermission.toMask(PolicyPermission.parse(p));
          }
          catch (IllegalArgumentException e) {
            issues.error(
              Issue.Code.ACL_INVALID_PERMISSION,
              "The specified permissions are invalid: %s",
              e.getMessage());

            return null;
          }
        });

      if (!allowedMask.isPresent() && !deniedMask.isPresent()) {
        issues.error(
          Issue.Code.ACL_INVALID_PERMISSION,
          "The access control entry for '%s' must allow or deny access",
          principalId.orElse(null));
      }
      else if (allowedMask.isPresent() == deniedMask.isPresent()) {
        issues.error(
          Issue.Code.ACL_INVALID_PERMISSION,
          "The access control entry for '%s' can either allow or deny access, but not both",
          principalId.orElse(null));
      }

      return NullaryOptional
        .ifTrue(principalId.isPresent() && (allowedMask.isPresent() ^ deniedMask.isPresent()))
        .map(() -> deniedMask.isPresent()
          ? new AccessControlList.DeniedEntry(principalId.get(), deniedMask.get())
          : new AccessControlList.AllowedEntry(principalId.get(), allowedMask.get()));

    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ConstraintsElement(
    @JsonProperty("join") List<ConstraintElement> join,
    @JsonProperty("approve") List<ConstraintElement> approve
  ) {
    private static final ConstraintsElement EMPTY = new ConstraintsElement(null, null);

    static @Nullable ConstraintsElement toYaml(@NotNull Map<Policy.ConstraintClass, Collection<Constraint>> constraints) {
      return constraints.isEmpty() ? null : new ConstraintsElement(
        Coalesce.emptyIfNull(constraints.get(Policy.ConstraintClass.JOIN))
          .stream()
          .map(ConstraintElement::toYaml)
          .toList(),
        Coalesce.emptyIfNull(constraints.get(Policy.ConstraintClass.APPROVE))
          .stream()
          .map(ConstraintElement::toYaml)
          .toList());
    }

    @NotNull Optional<Map<Policy.ConstraintClass, Collection<Constraint>>> toPolicy(@NotNull IssueCollection issues) {
      var join = Coalesce.emptyIfNull(this.join)
        .stream()
        .map(e -> e.toPolicy(issues))
        .toList();
      var approve = Coalesce.emptyIfNull(this.approve)
        .stream()
        .map(e -> e.toPolicy(issues))
        .toList();

      //
      // Check for redundant expiry constraints.
      //
      if (join.stream()
        .filter(Optional::isPresent)
        .filter(c -> c.get() instanceof ExpiryConstraint)
        .count() > 1) {
        issues.error(Issue.Code.CONSTRAINT_INVALID_EXPIRY, "More than one expiry constraints found");
        return Optional.empty();
      }

      if (approve.stream()
        .filter(Optional::isPresent)
        .anyMatch(c -> c.get() instanceof ExpiryConstraint)) {
        issues.error(Issue.Code.CONSTRAINT_INVALID_EXPIRY, "Expiry constraints can only be used as join-constraint");
        return Optional.empty();
      }

      return NullaryOptional
        .ifTrue(
          join.stream().allMatch(Optional::isPresent) &&
          approve.stream().allMatch(Optional::isPresent))
        .map(() -> Map.of(
          Policy.ConstraintClass.JOIN, join.stream().map(Optional::get).toList(),
          Policy.ConstraintClass.APPROVE, approve.stream().map(Optional::get).toList()));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ConstraintElement(
    @JsonProperty("type") String type,
    @JsonProperty("name") String name,
    @JsonProperty("displayName") String displayName,
    @JsonProperty("min") String expiryMinDuration,
    @JsonProperty("max") String expiryMaxDuration,
    @JsonProperty("expression") String celExpression,
    @JsonProperty("variables") List<CelVariableElement> celVariables
  ) {
    static ConstraintElement toYaml(@NotNull Constraint constraint) {
      if (constraint instanceof ExpiryConstraint expiryConstraint) {
        return new ConstraintElement(
          "expiry",
          null,
          null,
          expiryConstraint.minDuration().toString(),
          expiryConstraint.maxDuration().toString(),
          null,
          null);
      }
      else if (constraint instanceof CelConstraint celConstraint) {
        return new ConstraintElement(
          "expression",
          celConstraint.name(),
          celConstraint.displayName(),
          null,
          null,
          celConstraint.expression(),
          celConstraint.variables()
            .stream()
            .map(CelVariableElement::toYaml)
            .toList());
      }
      else {
        throw new UnsupportedOperationException("The constraint type is not supported");
      }
    }

    @NotNull Optional<Constraint> toPolicy(@NotNull IssueCollection issues) {
      return Optional
        .ofNullable(switch (Strings.nullToEmpty(this.type).trim().toLowerCase()) {
          case "expiry" -> {
            if (!Strings.isNullOrEmpty(this.celExpression) || this.celVariables != null) {
              issues.error(
                Issue.Code.CONSTRAINT_INVALID_EXPIRY,
                "Expiry constraints must not specify an expression",
                this.type);
            }

            try {
              yield new ExpiryConstraint(
                Duration.parse(this.expiryMinDuration),
                Duration.parse(this.expiryMaxDuration));
            }
            catch (Exception e) {
              issues.error(
                Issue.Code.CONSTRAINT_INVALID_EXPIRY,
                "The format of the duration '%s - %s' is invalid: %s",
                this.expiryMinDuration,
                this.expiryMaxDuration,
                e.getMessage());
              yield null;
            }
          }

          case "expression" -> {
            if (this.expiryMinDuration != null || this.expiryMaxDuration != null) {
              issues.error(
                Issue.Code.CONSTRAINT_INVALID_EXPRESSION,
                "Expression constraints must not specify an expiry",
                this.type);
            }

            var variables = Coalesce.emptyIfNull(this.celVariables)
              .stream()
              .map(v -> v.toPolicy(issues))
              .toList();

            if (variables.stream().allMatch(Optional::isPresent)) {
              try {
                var constraint = new CelConstraint(
                  this.name,
                  this.displayName,
                  variables.stream().map(Optional::get).toList(),
                  this.celExpression);

                for (var issue : constraint.lint()) {
                  issues.error(
                    Issue.Code.CONSTRAINT_INVALID_EXPRESSION,
                    "The constraint '%s' uses an invalid CEL expression: %s",
                    this.name,
                    issue.getMessage());
                }

                yield constraint;
              }
              catch (Exception e) {
                issues.error(
                  Issue.Code.CONSTRAINT_INVALID_EXPRESSION,
                  "The CEL constraint is invalid: %s",
                  e.getMessage());
                yield null;
              }
            }
            else {
              yield null;
            }
          }

          default -> {
            issues.error(
              Issue.Code.CONSTRAINT_INVALID_TYPE,
              "The constraint type '%s' is invalid",
              this.type);
            yield null;
          }
        });
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CelVariableElement(
    @JsonProperty("type") String type,
    @JsonProperty("name") String name,
    @JsonProperty("displayName") String displayName,
    @JsonProperty("min") Integer min,
    @JsonProperty("max") Integer max
  ) {
    static CelVariableElement toYaml(@NotNull CelConstraint.Variable variable) {
      if (variable instanceof CelConstraint.StringVariable stringVariable) {
        return new CelVariableElement(
          "string",
          stringVariable.name(),
          stringVariable.displayName(),
          stringVariable.minLength(),
          stringVariable.maxLength());
      }
      else if (variable instanceof CelConstraint.LongVariable longVariable) {
        return new CelVariableElement(
          "int",
          longVariable.name(),
          longVariable.displayName(),
          (int)(long)longVariable.minInclusive(),
          (int)(long)longVariable.maxInclusive());
      }
      else if (variable instanceof CelConstraint.BooleanVariable booleanVariable) {
        return new CelVariableElement(
          "boolean",
          booleanVariable.name(),
          booleanVariable.displayName(),
          null,
          null);
      }
      else {
        throw new UnsupportedOperationException("The variable type is not supported");
      }
    }

    @NotNull Optional<CelConstraint.Variable> toPolicy(@NotNull IssueCollection issues) {
      try {
        return Optional
          .ofNullable(switch (Strings.nullToEmpty(this.type).trim().toLowerCase()) {
            case "string" -> new CelConstraint.StringVariable(
              this.name,
              this.displayName,
              this.min != null ? this.min : 0,
              this.max != null ? this.max : 256);

            case "int", "integer" -> new CelConstraint.LongVariable(
              this.name,
              this.displayName,
              this.min != null ? (long) this.min : 0,
              this.max != null ? (long) this.max : Integer.MAX_VALUE);

            case "bool", "boolean" -> new CelConstraint.BooleanVariable(
              this.name,
              this.displayName);

            default -> {
              issues.error(
                Issue.Code.CONSTRAINT_INVALID_VARIABLE_DECLARATION,
                "The variable declaration '%s' uses an unknown type: %s",
                this.name,
                this.type);
              yield null;
            }
          });
      }
      catch (Exception e) {
        issues.error(
          Issue.Code.CONSTRAINT_INVALID_VARIABLE_DECLARATION,
          "The variable declaration '%s' is invalid: %s",
          this.name,
          e.getMessage());
        return Optional.empty();
      }
    }
  }


  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PrivilegesElement(
    @JsonProperty("iam") List<IamRoleBindingElement> iamRoleBindings
  ) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record IamRoleBindingElement(
    // union {
    @JsonProperty("project") String project,
    @JsonProperty("resource") String resource,
    // }
    @JsonProperty("role") String role,
    @JsonProperty("description") String description,
    @JsonProperty("condition") String condition
  ) {
    static IamRoleBindingElement toYaml(@NotNull IamRoleBinding binding) {
      return new IamRoleBindingElement(
        null,
        binding.resource().path(),
        binding.role().name(),
        binding.description(),
        binding.condition());
    }

    @NotNull Optional<IamRoleBinding> toPolicy(@NotNull IssueCollection issues) {
      if (!MoreStrings.isNullOrBlank(this.project) && !MoreStrings.isNullOrBlank((this.resource))) {
        issues.error(
          Issue.Code.PRIVILEGE_DUPLICATE_RESOURCE_ID,
          "The binding can be either for a project or resource, but not both");
        return Optional.empty();
      }

      var resourceId = Optional.<ResourceId>empty()
        .or(() -> ProjectId.parse(this.project))
        .or(() -> ProjectId.parse(this.resource))
        .or(() -> FolderId.parse(this.resource))
        .or(() -> OrganizationId.parse(this.resource));

      if (resourceId.isEmpty()) {
        issues.error(
          Issue.Code.PRIVILEGE_INVALID_RESOURCE_ID,
          "The resource ID '%s' does not match the format " +
            "projects/ID, folders/ID, or organizations/ID",
          Coalesce.nonEmpty(this.project, this.resource));
      }

      var role = IamRole.parse(this.role);
      if (role.isEmpty()) {
        issues.error(
          Issue.Code.PRIVILEGE_INVALID_ROLE,
          "The IAM role '%s' is invalid",
          this.role);
      }

      return NullaryOptional
        .ifTrue(resourceId.isPresent() && role.isPresent())
        .map(() -> new IamRoleBinding(
          resourceId.get(),
          role.get(),
          this.description,
          this.condition));
    }
  }
}
