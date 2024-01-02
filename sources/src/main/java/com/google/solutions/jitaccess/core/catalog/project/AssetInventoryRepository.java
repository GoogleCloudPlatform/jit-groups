package com.google.solutions.jitaccess.core.catalog.project;

import com.google.api.services.admin.directory.model.Group;
import com.google.api.services.cloudasset.v1.model.Binding;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.Entitlement;
import com.google.solutions.jitaccess.core.clients.AssetInventoryClient;
import com.google.solutions.jitaccess.core.clients.DirectoryGroupsClient;
import jakarta.inject.Singleton;

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
 * Entitlements as used by this class are role bindings that:
 * are annotated with a special IAM condition (making the binding
 * "eligible").
 */
@Singleton
public class AssetInventoryRepository implements ProjectRoleRepository { //TODO: test
  public static final String GROUP_PREFIX = "group:";
  public static final String USER_PREFIX = "user:";

  private final Options options;
  private final Executor executor;
  private final DirectoryGroupsClient groupsClient;
  private final AssetInventoryClient assetInventoryClient; //TODO: use 1-min cache

  public AssetInventoryRepository(
    Executor executor,
    DirectoryGroupsClient groupsClient,
    AssetInventoryClient assetInventoryClient,
    Options options
  ) {
    Preconditions.checkNotNull(executor, "executor");
    Preconditions.checkNotNull(groupsClient, "groupsClient");
    Preconditions.checkNotNull(assetInventoryClient, "assetInventoryClient");
    Preconditions.checkNotNull(options, "options");

    this.executor = executor;
    this.groupsClient = groupsClient;
    this.assetInventoryClient = assetInventoryClient;
    this.options = options;
  }

  <T> T awaitAndRethrow(CompletableFuture<T> future) throws AccessException, IOException {
    try {
      return future.get();
    }
    catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof AccessException) {
        throw (AccessException)e.getCause().fillInStackTrace();
      }

      if (e.getCause() instanceof IOException) {
        throw (IOException)e.getCause().fillInStackTrace();
      }

      throw new IOException("Awaiting executor tasks failed", e);
    }
  }

  Collection<Binding> findAllBindings(
    ProjectId projectId,
    UserId user
  ) throws AccessException, IOException {
    //
    // Lookup in parallel:
    // - the effective set of IAM policies applying to this project. This
    //   includes the IAM policy of the project itself, plus any policies
    //   applied to its ancestry (folders, organization).
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

    awaitAndRethrow(CompletableFuture.allOf(listMembershipsFuture, effectivePoliciesFuture));

    var principalSetForUser = new PrincipalSet(user, awaitAndRethrow(listMembershipsFuture));
    return awaitAndRethrow(effectivePoliciesFuture)
      .stream()

      // All bindings, across all resources in the ancestry.
      .flatMap(policy -> policy.getPolicy().getBindings().stream())

      // Only bindings that apply to the user.
      .filter(binding -> principalSetForUser.isMember(binding))
      .collect(Collectors.toList());
  }

  //---------------------------------------------------------------------------
  // ProjectRoleRepository.
  //---------------------------------------------------------------------------

  @Override
  public SortedSet<ProjectId> findProjectsWithEntitlements(
    UserId user
  ) {
    //
    // Not supported.
    //
    throw new IllegalStateException(
      "Feature is not supported. Use search to determine available projects");
  }

  @Override
  public Annotated<SortedSet<Entitlement<ProjectRoleBinding>>> findEntitlements(
    UserId user,
    ProjectId projectId,
    EnumSet<ActivationType> typesToInclude,
    EnumSet<Entitlement.Status> statusesToInclude
  ) throws AccessException, IOException {

    var allBindings = findAllBindings(projectId, user);

    var allAvailable = new TreeSet<Entitlement<ProjectRoleBinding>>();
    if (statusesToInclude.contains(Entitlement.Status.AVAILABLE)) {

      //
      // Find all JIT-eligible role bindings. The bindings are
      // conditional and have a special condition that serves
      // as marker.
      //
      Set<Entitlement<ProjectRoleBinding>> jitEligible;
      if (typesToInclude.contains(ActivationType.JIT)) {
        jitEligible = allBindings.stream()
          .filter(binding -> JitConstraints.isJitAccessConstraint(binding.getCondition()))
          .map(binding -> new ProjectRoleBinding(new RoleBinding(projectId, binding.getRole())))
          .map(roleBinding -> new Entitlement<ProjectRoleBinding>(
            roleBinding,
            roleBinding.roleBinding().role(),
            ActivationType.JIT,
            Entitlement.Status.AVAILABLE))
          .collect(Collectors.toCollection(TreeSet::new));
      }
      else {
        jitEligible = Set.of();
      }

      //
      // Find all MPA-eligible role bindings. The bindings are
      // conditional and have a special condition that serves
      // as marker.
      //
      Set<Entitlement<ProjectRoleBinding>> mpaEligible;
      if (typesToInclude.contains(ActivationType.MPA)) {
        mpaEligible = allBindings.stream()
          .filter(binding -> JitConstraints.isMultiPartyApprovalConstraint(binding.getCondition()))
          .map(binding -> new ProjectRoleBinding(new RoleBinding(projectId, binding.getRole())))
          .map(roleBinding -> new Entitlement<ProjectRoleBinding>(
            roleBinding,
            roleBinding.roleBinding().role(),
            ActivationType.MPA,
            Entitlement.Status.AVAILABLE))
          .collect(Collectors.toCollection(TreeSet::new));
      }
      else {
        mpaEligible = Set.of();
      }

      //
      // Determine effective set of eligible roles. If a role is both JIT- and
      // MPA-eligible, only retain the JIT-eligible one.
      //
      // Use a list so that JIT-eligible roles go first, followed by MPA-eligible ones.
      //
      allAvailable.addAll(jitEligible);
      allAvailable.addAll(mpaEligible
        .stream()
        .filter(r -> !jitEligible.stream().anyMatch(a -> a.id().equals(r.id())))
        .collect(Collectors.toList()));
    }

    var allActive = new TreeSet<Entitlement<ProjectRoleBinding>>();
    if (statusesToInclude.contains(Entitlement.Status.ACTIVE)) {
      //
      // Find role bindings which have already been activated.
      // These bindings have a time condition that we created, and
      // the condition evaluates to true (indicating it's still
      // valid).
      //
      for (var activeBinding : allBindings.stream()
        .filter(binding -> JitConstraints.isActivated(binding.getCondition())) //TODO: consider expired!
        .map(binding -> new RoleBinding(projectId, binding.getRole()))
        .collect(Collectors.toList())) {
        //
        // Find the corresponding eligible binding to determine
        // whether this is JIT or MPA-eligible.
        //
        var correspondingEligibleBinding = allAvailable
          .stream()
          .filter(ent -> ent.id().roleBinding().equals(activeBinding))
          .findFirst();
        if (correspondingEligibleBinding.isPresent()) {
          allActive.add(new Entitlement<>(
            new ProjectRoleBinding(activeBinding),
            activeBinding.role(),
            correspondingEligibleBinding.get().activationType(),
            Entitlement.Status.ACTIVE));
        }
        else {
          //
          // Active, but no longer eligible.
          //
          allActive.add(new Entitlement<>(
            new ProjectRoleBinding(activeBinding),
            activeBinding.role(),
            ActivationType.NONE,
            Entitlement.Status.ACTIVE));
        }
      }
    }

    //
    // Replace roles that have been activated already.
    //
    var availableAndActive = allAvailable
      .stream()
      .filter(r -> !allActive.stream().anyMatch(a -> a.id().equals(r.id())))
      .collect(Collectors.toCollection(TreeSet::new));
    availableAndActive.addAll(allActive);

    return new Annotated<>(availableAndActive, Set.of());
  }

  @Override
  public Set<UserId> findEntitlementHolders(
    ProjectRoleBinding roleBinding
  ) throws AccessException, IOException {

    var policies = this.assetInventoryClient.getEffectiveIamPolicies(
      this.options.scope,
      roleBinding.projectId());

    var principals = policies
      .stream()

      // All bindings, across all resources in the ancestry.
      .flatMap(policy -> policy.getPolicy().getBindings().stream())

      // Only consider requested role.
      .filter(binding -> binding.getRole().equals(roleBinding.roleBinding().role()))

      .flatMap(binding -> binding.getMembers().stream())
      .collect(Collectors.toSet());

    var allUserMembers = principals.stream()
      .filter(p -> p.startsWith(USER_PREFIX))
      .map(p -> p.substring(USER_PREFIX.length()))
      .distinct()
      .map(email -> new UserId(email))
      .collect(Collectors.toSet());

    //
    // Resolve groups.
    //
    var listMembersFutures = principals.stream()
      .filter(p -> p.startsWith(GROUP_PREFIX))
      .map(p -> p.substring(GROUP_PREFIX.length()))
      .distinct()
      .map(groupEmail -> ThrowingCompletableFuture.submit(
        () -> this.groupsClient.listDirectGroupMembers(groupEmail), // TODO: could fail for external group, or if deleted, ignore then
        this.executor))
      .collect(Collectors.toList());

    awaitAndRethrow(CompletableFuture.allOf((CompletableFuture<?>) listMembersFutures));

    var allMembers = new HashSet<>(allUserMembers);

    for (var listMembersFuture : listMembersFutures) {
      var members = awaitAndRethrow(listMembersFuture)
        .stream()
        .map(m -> new UserId(m.getEmail()))
        .collect(Collectors.toList());
      allMembers.addAll(members);
    }

    return allMembers;
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  class PrincipalSet {
    private final Set<String> principalIdentifiers;

    public PrincipalSet(
      UserId user,
      Collection<Group> groups
    ) {
      this.principalIdentifiers = groups
        .stream()
        .map(g -> String.format("group:%s", g.getEmail()))
        .collect(Collectors.toSet());
      this.principalIdentifiers.add(String.format("user:%s", user.email));
    }

    public boolean isMember(Binding binding) {
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
