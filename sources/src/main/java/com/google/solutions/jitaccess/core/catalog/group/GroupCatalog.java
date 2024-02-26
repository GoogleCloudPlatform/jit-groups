package com.google.solutions.jitaccess.core.catalog.group;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.AccessControlList;
import com.google.solutions.jitaccess.core.auth.GroupExpander;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.catalog.policy.Policy;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Catalog for groups.
 *
 * The catalog is scoped to a Google Cloud organization (or technically,
 * to a Cloud Identity/Workspace account).
 */
public class GroupCatalog implements EntitlementCatalog<GroupMembership, OrganizationId> {
  /**
   * Pseudo-organization ID indicating the "current" organization.
   * Using the real ID would be unnecessary as this catalog only ever
   * operates on a single organization.
   */
  public static final OrganizationId CURRENT_ORGANIZATION = new OrganizationId("-");

  private final @NotNull List<Policy> policies;
  private final @NotNull User.Resolver userResolver;
  private final @NotNull GroupMapper mapper;
  private final @NotNull GroupExpander groupExpander;
  private final @NotNull Executor executor;

  public GroupCatalog(
    @NotNull List<Policy> policies,
    @NotNull User.Resolver userResolver,
    @NotNull GroupMapper mapper,
    @NotNull GroupExpander groupExpander,
    @NotNull Executor executor
  ) {
    this.policies = policies;
    this.userResolver = userResolver;
    this.mapper = mapper;
    this.groupExpander = groupExpander;
    this.executor = executor;
  }

  private Policy.Entitlement lookupEntitlementPolicy(
    GroupMembership entitlement
  ) throws AccessDeniedException {
    var policyEntitlement = this.policies
      .stream()
      .flatMap(p -> p.entitlements().stream())
      .filter(e -> this.mapper.map(e).equals(entitlement))
      .findFirst();
    if (!policyEntitlement.isPresent()) {
      throw new AccessDeniedException("Entitlement not found");
    }

    return policyEntitlement.get();
  }

  private void verifyRequestSatisfiesPolicyConstraints(
    @NotNull ActivationRequest<GroupMembership> request,
    @NotNull Policy.Entitlement policy
  ) {
    //TODO: validateRequest
  }

  private @NotNull AccessControlList verifyUserAllowedByPolicyAcl(
    @NotNull UserEmail requestingUser,
    @NotNull Policy.Entitlement policy,
    @NotNull GroupMembership entitlementId
  ) throws AccessException, IOException {
    //
    // Lookup the user and their principals.
    //
    var userFuture = ThrowingCompletableFuture.submit(
      () -> this.userResolver.lookup(requestingUser),
      this.executor);

    //
    // Expand ACL groups.
    //
    var aclFuture = ThrowingCompletableFuture.submit(
      () -> this.groupExpander.expand(policy.acl()),
      this.executor);

    var user = ThrowingCompletableFuture.awaitAndRethrow(userFuture);
    var acl = ThrowingCompletableFuture.awaitAndRethrow(aclFuture);
    if (!acl.isAllowed(user)) {
      throw new AccessDeniedException(
        String.format(
          "The user %s is not authorized to activate entitlement '%s'",
          requestingUser,
          entitlementId.id()));
    }

    //
    // All good, return effective ACL.
    //
    return acl;
  }

  //---------------------------------------------------------------------------
  // EntitlementCatalog.
  //---------------------------------------------------------------------------

  @Override
  public SortedSet<OrganizationId> listScopes(
    @NotNull UserEmail user
  ) {
    return new TreeSet<>(List.of(CURRENT_ORGANIZATION));
  }

  @Override
  public void verifyUserCanRequest(
    @NotNull ActivationRequest<GroupMembership> request
  ) throws AccessException, IOException {
    Preconditions.checkArgument(
      request.entitlements().size() == 1,
      "Requests must contain exactly one entitlement");

    //
    // Lookup policy and check request.
    //
    var entitlementId = request.entitlements().stream().findFirst().get();
    var policy = lookupEntitlementPolicy(entitlementId);

    verifyRequestSatisfiesPolicyConstraints(request, policy);

    //
    // Check if the requesting user is allowed to activate this
    // entitlement.
    //
    verifyUserAllowedByPolicyAcl(
      request.requestingUser(),
      policy,
      entitlementId);
  }

  @Override
  public void verifyUserCanApprove(
    @NotNull UserEmail approvingUser,
    @NotNull MpaActivationRequest<GroupMembership> request
  ) throws AccessException, IOException {

    Preconditions.checkArgument(
      request.entitlements().size() == 1,
      "Requests must contain exactly one entitlement");

    //
    // Lookup policy and check request.
    //
    var entitlementId = request.entitlements().stream().findFirst().get();
    var policy = lookupEntitlementPolicy(entitlementId);

    verifyRequestSatisfiesPolicyConstraints(request, policy);

    //
    // Check if the approving user (!) is allowed to activate this
    // entitlement.
    //
    // NB. The base class already checked that the requesting user
    // is allowed.
    //
    verifyUserAllowedByPolicyAcl(
      approvingUser,
      policy,
      entitlementId);
  }

  @Override
  public SortedSet<UserEmail> listReviewers(
    @NotNull UserEmail requestingUser,
    @NotNull GroupMembership entitlementId
  ) throws AccessException, IOException {

    var policy = lookupEntitlementPolicy(entitlementId);
    if (policy.approvalRequirement().activationType() != ActivationType.MPA) {
      //
      // It's not worth performing any further authorization,
      // just return a generic error.
      //
      throw new AccessDeniedException(
        String.format(
          "The user %s is not authorized to activate entitlement '%s'",
          requestingUser,
          entitlementId.id()));
    }

    //
    // Check if the user is allowed to use this entitlement at all,
    // and isn't just trying to do enumeration.
    //
    // NB. It doesn't matter whether the user has already
    // activated the entitlement.
    //

    var effectiveAcl = verifyUserAllowedByPolicyAcl(requestingUser, policy, entitlementId);

    //
    // This is an MPA-entitlement and the user is in the ACL.
    //
    // Return all users in the (expanded) ACL. These users
    // all hold the same entitlement can act as reviewers,
    // except for the requesting user themselves.
    //

    return effectiveAcl
      .allowedPrincipals()
      .stream()
      .filter(p -> p instanceof UserEmail)
      .map(p -> (UserEmail)p)
      .filter(u -> !u.equals(requestingUser)) // Exclude requesting user
      .collect(Collectors.toCollection(TreeSet::new));
  }


  @Override
  public EntitlementSet<GroupMembership> listEntitlements(
    @NotNull UserEmail userEmail,
    @NotNull OrganizationId scope
  ) throws AccessException, IOException {
    Preconditions.checkArgument(CURRENT_ORGANIZATION.equals(scope));

    //
    // Lookup the user and their principals.
    //
    var user = this.userResolver.lookup(userEmail);

    //
    // Find entitlements that list this user (or one of its groups)
    // as being eligible.
    //
    var availableEntitlements = new TreeSet<Entitlement<GroupMembership>>();
    for (var policyEntitlement : this.policies
      .stream()
      .flatMap(p -> p.entitlements().stream())
      .filter(e -> e.acl().isAllowed(user))
      .toList()) {

      //
      // Map entitlement to group and check if the user is already
      // a member of that group. If so, they must have activated
      // the entitlement already.
      //
      var backingGroup = this.mapper.map(policyEntitlement);

      var existingMembershipOfBackingGroup = user
        .activeEntitlement()
        .stream()
        .filter(m -> m.id().group().equals(backingGroup))
        .findFirst();

      if (existingMembershipOfBackingGroup.isPresent()) {
        availableEntitlements.add(new Entitlement<>(
          new GroupMembership(backingGroup),
          policyEntitlement.name(), //TODO: include policy name
          policyEntitlement.approvalRequirement().activationType(),
          Entitlement.Status.ACTIVE,
          existingMembershipOfBackingGroup.get().validity()));
      }
      else {
        availableEntitlements.add(new Entitlement<>(
          new GroupMembership(backingGroup),
          policyEntitlement.name(), //TODO: include policy name
          policyEntitlement.approvalRequirement().activationType(),
          Entitlement.Status.AVAILABLE));
      }
    }

    //
    // Find entitlements that are active, but aren't covered
    // by any policy. This can happen if a policy has been modified.
    //
    for (var activeAndOrphanedEntitlement : user
      .activeEntitlement()
      .stream()
      .filter(a -> availableEntitlements.stream().noneMatch(e -> a.id().equals(e.id())))
      .toList()) {

      availableEntitlements.add(new Entitlement<>(
        activeAndOrphanedEntitlement.id(),
        String.format("(%s)", activeAndOrphanedEntitlement.id()), // We don't know the name.
        ActivationType.NONE,
        Entitlement.Status.ACTIVE));
    }

    return new EntitlementSet<>(availableEntitlements, new TreeSet<>(), Set.of());
  }
}
