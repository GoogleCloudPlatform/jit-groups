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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.api.services.cloudasset.v1.model.Binding;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Member;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.cel.TemporaryIamCondition;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilegeSet;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.clients.AssetInventoryClient;
import com.google.solutions.jitaccess.core.clients.DirectoryGroupsClient;
import dev.cel.common.CelException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Repository that uses the Asset Inventory API (without its
 * Policy Analyzer subset) to find entitlements.
 *
 * Entitlements as used by this class are role bindings that
 * are annotated with a special IAM condition (making the binding
 * "eligible").
 */
public class AssetInventoryRepository extends ProjectRoleRepository {
  public static final String GROUP_PREFIX = "group:";
  public static final String USER_PREFIX = "user:";

  private final @NotNull Options options;
  private final @NotNull Executor executor;
  private final @NotNull DirectoryGroupsClient groupsClient;
  private final @NotNull AssetInventoryClient assetInventoryClient;

  public AssetInventoryRepository(
      @NotNull Executor executor,
      @NotNull DirectoryGroupsClient groupsClient,
      @NotNull AssetInventoryClient assetInventoryClient,
      @NotNull Options options) {
    Preconditions.checkNotNull(executor, "executor");
    Preconditions.checkNotNull(groupsClient, "groupsClient");
    Preconditions.checkNotNull(assetInventoryClient, "assetInventoryClient");
    Preconditions.checkNotNull(options, "options");

    this.executor = executor;
    this.groupsClient = groupsClient;
    this.assetInventoryClient = assetInventoryClient;
    this.options = options;
  }

  @NotNull
  List<Binding> findProjectBindings(
      @NotNull UserEmail user,
      ProjectId projectId) throws AccessException, IOException {
    //
    // Lookup in parallel:
    // - the effective set of IAM policies applying to this project. This
    // includes the IAM policy of the project itself, plus any policies
    // applied to its ancestry (folders, organization).
    // - groups that the user is a member of.
    //
    var listMembershipsFuture = ThrowingCompletableFuture.submit(
        () -> this.groupsClient.listDirectGroupMemberships(user),
        this.executor);

    var effectivePoliciesFuture = ThrowingCompletableFuture.submit(
        () -> this.assetInventoryClient.getEffectiveIamPolicies(
            this.options.scope(),
            projectId),
        this.executor);

    var principalSetForUser = new PrincipalSet(
        user,
        ThrowingCompletableFuture.awaitAndRethrow(listMembershipsFuture));
    return ThrowingCompletableFuture.awaitAndRethrow(effectivePoliciesFuture)
        .stream()

        // All bindings, across all resources in the ancestry.
        .flatMap(policy -> policy.getPolicy().getBindings().stream())

        // Only bindings that apply to the user.
        .filter(binding -> principalSetForUser.isMember(binding))
        .collect(Collectors.toList());
  }

  // ---------------------------------------------------------------------------
  // ProjectRoleRepository.
  // ---------------------------------------------------------------------------

  @Override
  public SortedSet<ProjectId> findProjectsWithRequesterPrivileges(
      UserEmail user) {
    //
    // Not supported.
    //
    throw new IllegalStateException(
        "Feature is not supported. Use search to determine available projects");
  }

  @Override
  public @NotNull RequesterPrivilegeSet<ProjectRoleBinding> findRequesterPrivileges(
      @NotNull UserEmail user,
      @NotNull ProjectId projectId,
      @NotNull Set<ActivationType> typesToInclude,
      @NotNull EnumSet<RequesterPrivilege.Status> statusesToInclude) throws AccessException, IOException {

    List<Binding> allBindings = findProjectBindings(user, projectId);

    var allAvailable = new TreeSet<RequesterPrivilege<ProjectRoleBinding>>();
    if (statusesToInclude.contains(RequesterPrivilege.Status.INACTIVE)) {
      allAvailable.addAll(
          allBindings.stream()
              .map(binding -> PrivilegeFactory.createRequesterPrivilege(
                  new ProjectRoleBinding(new RoleBinding(projectId, binding.getRole())),
                  binding.getCondition()))
              .filter(result -> result.isPresent())
              .map(result -> result.get())
              .filter(privilege -> typesToInclude.stream()
                  .anyMatch(type -> type.isParentTypeOf(privilege.activationType())))
              .collect(Collectors.toSet()));
    }

    //
    // Find temporary bindings that reflect activations and sort out which
    // ones are still active and which ones have expired.
    //
    var allActive = new HashSet<ActivatedRequesterPrivilege<ProjectRoleBinding>>();
    var allExpired = new HashSet<ActivatedRequesterPrivilege<ProjectRoleBinding>>();

    for (var binding : allBindings.stream()
        // Only temporary access bindings.
        .filter(binding -> PrivilegeFactory.isActivated(binding.getCondition()))
        .collect(Collectors.toUnmodifiableList())) {
      var condition = new TemporaryIamCondition(binding.getCondition().getExpression());
      boolean isValid;

      try {
        isValid = condition.evaluate();
      } catch (CelException e) {
        isValid = false;
      }

      if (isValid && statusesToInclude.contains(RequesterPrivilege.Status.ACTIVE)) {
        allActive.add(new ActivatedRequesterPrivilege<>(
            new ProjectRoleBinding(new RoleBinding(projectId, binding.getRole())),
            condition.getValidity()));
      }

      if (!isValid && statusesToInclude.contains(RequesterPrivilege.Status.EXPIRED)) {
        allExpired.add(new ActivatedRequesterPrivilege<>(
            new ProjectRoleBinding(new RoleBinding(projectId, binding.getRole())),
            condition.getValidity()));
      }
    }

    return buildRequesterPrivilegeSet(allAvailable, allActive, allExpired, Set.of());
  }

  @Override
  public @NotNull Set<UserEmail> findReviewerPrivelegeHolders(
      @NotNull ProjectRoleBinding roleBinding,
      @NotNull ActivationType activationType) throws AccessException, IOException {

    var policies = this.assetInventoryClient.getEffectiveIamPolicies(
        this.options.scope,
        roleBinding.projectId());

    var principals = policies
        .stream()

        // All bindings, across all resources in the ancestry.
        .flatMap(policy -> policy.getPolicy().getBindings().stream())

        // Only consider requested role.
        .filter(binding -> binding.getRole().equals(roleBinding.roleBinding().role()))

        // Only consider eligible bindings.
        .filter(binding -> PrivilegeFactory.createReviewerPrivilege(
            new ProjectRoleBinding(new RoleBinding(roleBinding.projectId(), binding.getRole())),
            binding.getCondition()).isPresent())
        .filter(binding -> PrivilegeFactory.createReviewerPrivilege(
            new ProjectRoleBinding(new RoleBinding(roleBinding.projectId(), binding.getRole())),
            binding.getCondition()).get().reviewableTypes().stream()
            .anyMatch(type -> type.isParentTypeOf(activationType)))

        .flatMap(binding -> binding.getMembers().stream())
        .collect(Collectors.toSet());

    var allUserMembers = principals.stream()
        .filter(p -> p.startsWith(USER_PREFIX))
        .map(p -> p.substring(USER_PREFIX.length()))
        .distinct()
        .map(email -> new UserEmail(email))
        .collect(Collectors.toSet());

    //
    // Resolve groups.
    //
    List<CompletableFuture<Collection<Member>>> listMembersFutures = principals.stream()
        .filter(p -> p.startsWith(GROUP_PREFIX))
        .map(p -> p.substring(GROUP_PREFIX.length()))
        .distinct()
        .map(groupEmail -> ThrowingCompletableFuture.submit(
            () -> {
              try {
                return this.groupsClient.listDirectGroupMembers(groupEmail);
              } catch (AccessDeniedException e) {
                //
                // Access might be denied if this is an external group,
                // but this is okay.
                //
                return List.<Member>of();
              }
            },
            this.executor))
        .toList();

    var allMembers = new HashSet<>(allUserMembers);

    for (var listMembersFuture : listMembersFutures) {
      var members = ThrowingCompletableFuture.awaitAndRethrow(listMembersFuture)
          .stream()
          .map(m -> new UserEmail(m.getEmail())).toList();
      allMembers.addAll(members);
    }

    return allMembers;
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  static class PrincipalSet {
    private final @NotNull Set<String> principalIdentifiers;

    public PrincipalSet(
        @NotNull UserEmail user,
        @NotNull Collection<Group> groups) {
      this.principalIdentifiers = groups
          .stream()
          .map(g -> String.format("group:%s", g.getEmail()))
          .collect(Collectors.toSet());
      this.principalIdentifiers.add(String.format("user:%s", user.email));
    }

    public boolean isMember(@NotNull Binding binding) {
      return binding.getMembers()
          .stream()
          .anyMatch(member -> this.principalIdentifiers.contains(member));
    }
  }

  /**
   * @param scope Scope to use for queries.
   */
  public record Options(
      String scope) {

    public Options {
      Preconditions.checkNotNull(scope, "scope");
    }
  }
}
