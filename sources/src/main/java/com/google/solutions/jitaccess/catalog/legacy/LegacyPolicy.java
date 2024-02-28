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

package com.google.solutions.jitaccess.catalog.legacy;

import com.google.api.services.cloudasset.v1.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.Streams;
import com.google.solutions.jitaccess.apis.IamRole;
import com.google.solutions.jitaccess.apis.ProjectId;
import com.google.solutions.jitaccess.catalog.EventIds;
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.catalog.auth.GroupId;
import com.google.solutions.jitaccess.catalog.auth.PrincipalId;
import com.google.solutions.jitaccess.catalog.auth.UserClassId;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.*;
import com.google.solutions.jitaccess.util.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Policy derived from "JIT Access 1.x-style" eligible role bindings.
 */
public class LegacyPolicy extends EnvironmentPolicy {
  public static final @NotNull String NAME = "classic";
  private static final @NotNull String DESCRIPTION = "JIT Access 1.x roles";

  /**
   * Predefined basic, IAM-, and CRM roles that contain the
   * resourcemanager.projects.getIamPolicy permission.
   *
   * Principals that have these roles can view the IAM policy,
   * and should therefore be allowed to export.
   */
  private static final Set PREDEFINED_ROLES_WITH_GET_POLICY_PERMISSION = Set.of(
    "roles/owner",
    "roles/editor",
    "roles/viewer",
    "roles/browser",
    "roles/iam.organizationRoleAdmin",
    "roles/iam.organizationRoleViewer",
    "roles/iam.roleAdmin",
    "roles/iam.roleViewer",
    "roles/iam.securityAdmin",
    "roles/iam.securityReviewer",
    "roles/resourcemanager.projectIamAdmin",
    "roles/resourcemanager.folderAdmin",
    "roles/resourcemanager.organizationAdmin");


  /**
   * Predefined basic, IAM-, and CRM roles that contain the
   * resourcemanager.projects.setIamPolicy permission.
   *
   * Principals that have these roles can modify the IAM policy,
   * and should therefore be allowed to reconcile.
   */
  private static final Set PREDEFINED_ROLES_WITH_SET_POLICY_PERMISSION = Set.of(
    "roles/owner",
    "roles/iam.securityAdmin",
    "roles/resourcemanager.projectIamAdmin",
    "roles/resourcemanager.folderAdmin",
    "roles/resourcemanager.organizationAdmin");

  private static List<PrincipalId> extractPrincipals(@NotNull Binding binding) {
    //
    // NB. We don't need to resolve group members as that happens
    // at request time anyway.
    //
    return binding.getMembers()
      .stream()
      .flatMap(member -> Optional.<PrincipalId>empty()
        .or(() -> UserId.parse(member))
        .or(() -> GroupId.parse(member))
        .stream())
      .toList();
  }

  LegacyPolicy(
    @NotNull Duration activationTimeout,
    @NotNull String justificationPattern,
    @NotNull String justificationHint,
    @NotNull Collection<Binding> rootBindings,
    @NotNull Metadata metadata
  ) {
    super(
      NAME,
      DESCRIPTION,
      new AccessControlList(
        Streams
          .concat(
            //
            // Allow users with getIamPolicy permission to EXPORT.
            //
            rootBindings
              .stream()
              .filter(b -> PREDEFINED_ROLES_WITH_GET_POLICY_PERMISSION.contains(b.getRole()))
              .filter(b -> b.getCondition() == null || Strings.isNullOrEmpty(b.getCondition().getExpression()))
              .flatMap(b -> extractPrincipals(b).stream())
              .distinct()
              .map(p -> (AccessControlList.Entry)new AccessControlList.AllowedEntry(p, PolicyPermission.EXPORT.toMask())),

            //
            // Allow users with setIamPolicy permission to RECONCILE.
            //
            rootBindings
              .stream()
              .filter(b -> PREDEFINED_ROLES_WITH_SET_POLICY_PERMISSION.contains(b.getRole()))
              .filter(b -> b.getCondition() == null || Strings.isNullOrEmpty(b.getCondition().getExpression()))
              .flatMap(b -> extractPrincipals(b).stream())
              .distinct()
              .map(p -> (AccessControlList.Entry)new AccessControlList.AllowedEntry(p, PolicyPermission.RECONCILE.toMask())),

            //
            // Allow all users to VIEW.
            //
            Stream.of(new AccessControlList.AllowedEntry(UserClassId.AUTHENTICATED_USERS, PolicyPermission.VIEW.toMask())))
          .toList()
      ),
      Map.of(
        ConstraintClass.JOIN,
        List.of(
          new ExpiryConstraint(Duration.ofMinutes(1), activationTimeout),
          new CelConstraint(
            "justification",
            "You must provide a justification that explains why you need this access",
            List.of(new CelConstraint.StringVariable(
              "justification",
              justificationHint,
              1,
              100)),
            String.format(
              "input.justification.matches('%s')",
              justificationPattern.replace("\\", "\\\\"))
          )
        )
      ),
      metadata);
  }

  @Override
  public @NotNull EnvironmentPolicy add(@NotNull SystemPolicy policy) {
    Preconditions.checkArgument(policy instanceof ProjectPolicy);
    return super.add(policy);
  }

  @NotNull EnvironmentPolicy add(
    @NotNull Project project,
    @NotNull Supplier<Collection<Binding>> bindings,
    @NotNull Logger logger
  ) {
    var projectNumber = Long.parseLong(ProjectId.parse(project.getName()).get().id());
    var projectId = new ProjectId(project.getProjectId());

    return super.add(new ProjectPolicy(
      projectNumber,
      projectId,
      bindings,
      logger));
  }

  /**
   * Maps a project to a System.
   */
  static class ProjectPolicy extends SystemPolicy {
    private final @NotNull ProjectId projectId;
    private final @NotNull Logger logger;
    private final @NotNull AtomicBoolean initialized = new AtomicBoolean(false);
    private final @NotNull Supplier<Collection<Binding>> loadBindings;

    /**
     * Create a shortened name from a project number.
     */
    static @NotNull String createName(long projectNumber) {
      //
      // Project IDs can be up to 30 characters, which consumes too much
      // of the maximum group name length. Base64 would be more compact,
      // but it's case-sensitive. So we use Hex as a compromise.
      //
      return String.format("%x", projectNumber);
    }

    private ProjectPolicy(
      long projectNumber,
      @NotNull ProjectId projectId,
      @NotNull Supplier<Collection<Binding>>  loadBindings,
      @NotNull Logger logger
      ) {
      super(
        createName(projectNumber),
        String.format("Project %s", projectId),
        null,          // No access control, like in 1.x
        Map.of());     // All constraints are global.

      this.projectId = projectId;
      this.loadBindings = loadBindings;
      this.logger = logger;
    }

    private void initializeLazily() {
      synchronized (this.initialized) {
        if (!this.initialized.get()) {
          this.initialized.set(true);

          var roles = new HashMap<String, RolePolicy>();
          for (var binding : this.loadBindings.get()) {
            try {
              var role = RolePolicy.fromBinding(this.projectId, binding);
              if (role.isEmpty()) {
                //
                // Not a JIT- or MPA eligible role, ignore.
                //
              }
              else if (roles.containsKey(role.get().name())) {
                //
                // Role added already. This can happen if the same IAM role
                // is JIt-eligible to some users, and MPA-eligible to others.
                // In this case, we need to merge the two ACLs.
                //
                roles.put(role.get().name(), RolePolicy.merge(roles.get(role.get().name()), role.get()));
              }
              else {
                roles.put(role.get().name(), role.get());
              }
            }
            catch (Exception e) {
              this.logger.warn(
                EventIds.MAP_LEGACY_ROLE_,
                "The role '%s' of project %s cannot be mapped to a JIT group: %s",
                binding.getRole(),
                this.projectId,
                Exceptions.fullMessage(e));
            }
          }

          roles.values().forEach(super::add);
        }
      }
    }

    @Override
    public @NotNull String displayName() {
      return this.projectId.id();
    }

    @Override
    public @NotNull SystemPolicy add(@NotNull JitGroupPolicy group) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull Collection<JitGroupPolicy> groups() {
      initializeLazily();
      return super.groups();
    }

    @Override
    public Optional<JitGroupPolicy> group(@NotNull String name) {
      initializeLazily();
      return super.group(name);
    }
  }

  /**
   * Maps an eligible role binding to a JIT Group.
   */
  static class RolePolicy extends JitGroupPolicy {
    /**
     * Use an extended limit to ensure that we can fix most IAM roles. The
     * extended limit is safe because we know the maximum length
     * of the environment and system name.
     */
    static final int NAME_MAX_LENGTH = 42;
    private static final Pattern CUSTOM_ORG_ROLE_PATTERN = Pattern.compile("^organizations/(\\d+)/roles/(.+)$");
    private static final Pattern CUSTOM_PROJECT_ROLE_PATTERN = Pattern.compile("^projects/(.+)/roles/(.+)$");

    /**
     * Create a shortened name for an IAM role.
     */
    static @NotNull String createName(@NotNull IamRole role) {
      if (role.name().startsWith("roles/")) {
        //
        // Predefined role, strip prefix.
        //
        return role.name().substring(6).replace('.', '-').toLowerCase();
      }

      var matchOrgRole = CUSTOM_ORG_ROLE_PATTERN.matcher(role.name());
      if (matchOrgRole.matches()) {
        //
        // Custom organization role, replace prefix. The result
        // is still unique within the scope of a project, which
        // is sufficient.
        //
        return "o-" + matchOrgRole.group(2).replace('.', '-').toLowerCase();
      }

      var matchProjectRole = CUSTOM_PROJECT_ROLE_PATTERN.matcher(role.name());
      if (matchProjectRole.matches()) {
        //
        // Custom project role, replace prefix. The result
        // is still unique within the scope of a project, which
        // is sufficient.
        //
        return "p-" + matchProjectRole.group(2).replace('.', '-').toLowerCase();
      }

      throw new IllegalArgumentException("Unrecognized role: " + role);
    }

    private static RolePolicy fromBinding(
      @NotNull ProjectRole role,
      @NotNull List<PrincipalId> allowedPrincipals,
      @NotNull EnumSet<PolicyPermission> permissions
    ) {
      var acl = new AccessControlList(
        allowedPrincipals
          .stream()
          .<AccessControlList.Entry>map(p -> new AccessControlList.AllowedEntry(
            p,
            PolicyPermission.toMask(permissions)))
          .toList());

      if (!Strings.isNullOrEmpty(role.resourceCondition())) {
        throw new UnsupportedOperationException(
          "The role has a resource condition");
      }

      var iamBinding = new IamRoleBinding(
        role.projectId(),
        new IamRole(role.role()));

      return new RolePolicy(
        createName(iamBinding.role()),
        acl,
        Map.of(), // All constraints are global.
        iamBinding);
    }

    static Optional<RolePolicy> fromBinding(
      @NotNull ProjectId projectId,
      @NotNull Binding binding
    ) {
      var mpaRole = ProjectRole.fromMpaEligibleRoleBinding(projectId, binding);
      if (mpaRole.isPresent()) {
        //
        // Allow joining and (peer-) approving other requests, but don't
        // allow self-approval.
        //
        return mpaRole.map(r -> fromBinding(
          r,
          extractPrincipals(binding),
          EnumSet.of(PolicyPermission.JOIN, PolicyPermission.APPROVE_OTHERS)));
      }

      var jitRole = ProjectRole.fromJitEligibleRoleBinding(projectId, binding);
      if (jitRole.isPresent()) {
        //
        // Allow joining and  self-approval.
        //
        return jitRole.map(r -> fromBinding(
          r,
          extractPrincipals(binding),
          EnumSet.of(PolicyPermission.JOIN, PolicyPermission.APPROVE_SELF)));
      }

      //
      // Not an eligible role binding, ignore.
      //
      return Optional.empty();
    }


    private RolePolicy(
      @NotNull String name,
      @NotNull String description,
      @NotNull AccessControlList acl,
      @NotNull Map<ConstraintClass, Collection<Constraint>> constraints,
      @NotNull List<Privilege> privileges
    ) {
      super(
        name,
        description,
        acl,
        constraints,
        privileges,
        NAME_MAX_LENGTH);
    }

    private RolePolicy(
      @NotNull String name,
      @NotNull AccessControlList acl,
      @NotNull Map<ConstraintClass, Collection<Constraint>> constraints,
      @NotNull IamRoleBinding binding
    ) {
      this(
        name,
        String.format(
          "Grants %s on project %s",
          binding.role().name(),
          binding.resource().id()),
        acl,
        constraints,
        List.of(binding));
    }

    static RolePolicy merge(
      @NotNull RolePolicy lhs,
      @NotNull RolePolicy rhs
    ) {
      Preconditions.checkArgument(lhs.name().equals(rhs.name()));

      return new RolePolicy(
        lhs.name(),
        lhs.description(),
        new AccessControlList(
          Stream.concat(
            lhs.accessControlList().get().entries().stream(),
            rhs.accessControlList().get().entries().stream())
            .toList()),
        Map.of(
          ConstraintClass.JOIN,
          Stream.concat(
            lhs.constraints(ConstraintClass.JOIN).stream(),
            rhs.constraints(ConstraintClass.JOIN).stream())
            .toList()),
        Stream.concat(
          lhs.privileges().stream(),
          rhs.privileges().stream())
          .distinct()
          .toList());
    }
  }
}
