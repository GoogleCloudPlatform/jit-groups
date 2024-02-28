package com.google.solutions.jitaccess.core.catalog.jitrole;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.GroupExpander;
import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.catalog.policy.Policy;
import com.google.solutions.jitaccess.core.catalog.policy.PolicySet;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Catalog for JIT roles.
 *
 * The catalog is scoped to a Google Cloud organization (or technically,
 * to a Cloud Identity/Workspace account).
 */
public class JitRoleCatalog implements Catalog<JitRole, OrganizationId, UserContext> { // TODO: test
  /**
   * Pseudo-organization ID indicating the "current" organization.
   * Using the real ID would be unnecessary as this catalog only ever
   * operates on a single organization.
   */
  public static final OrganizationId CURRENT_ORGANIZATION = new OrganizationId("-");

  private final @NotNull PolicySet policySet;
  private final @NotNull UserContext.Resolver userResolver;
  private final @NotNull GroupExpander groupExpander;
  private final @NotNull Executor executor;

  public JitRoleCatalog(
    @NotNull PolicySet policySet,
    @NotNull UserContext.Resolver userResolver,
    @NotNull GroupExpander groupExpander,
    @NotNull Executor executor
  ) {
    this.policySet = policySet;
    this.userResolver = userResolver;
    this.groupExpander = groupExpander;
    this.executor = executor;
  }

  private Policy.Role lookupEntitlementPolicy(
    JitRole entitlementId
  ) throws AccessDeniedException {
    var policyEntitlement = this.policySet
      .policies()
      .stream()
      .flatMap(p -> p.roles().stream())
      .filter(e -> e.id().equals(entitlementId))
      .findFirst();
    if (!policyEntitlement.isPresent()) {
      throw new AccessDeniedException("Entitlement not found");
    }

    return policyEntitlement.get();
  }

  private static void verifyRequestMeetsConstraints(
    @NotNull ActivationRequest<JitRole> request,
    @NotNull Policy.Constraints constraints
  ) {
    Preconditions.checkArgument(
      request.duration().toSeconds() >= constraints.minActivationDuration().toSeconds(),
      String.format(
        "The activation duration must be no shorter than %d minutes",
        constraints.minActivationDuration().toMinutes()));
    Preconditions.checkArgument(
      request.duration().toSeconds() <= constraints.maxActivationDuration().toSeconds(),
      String.format(
        "The activation duration must be no longer than %d minutes",
        constraints.maxActivationDuration().toMinutes()));

    if (request instanceof MpaActivationRequest<JitRole> mpaRequest) {
      Preconditions.checkArgument(
        mpaRequest.reviewers() != null &&
          mpaRequest.reviewers().size() >= constraints.approvalConstraints().minimumNumberOfPeersToNotify(),
        String.format(
          "At least %d reviewers must be specified",
          constraints.approvalConstraints().minimumNumberOfPeersToNotify()));
      Preconditions.checkArgument(
        mpaRequest.reviewers().size() <= constraints.approvalConstraints().maximumNumberOfPeersToNotify(),
        String.format(
          "The number of reviewers must not exceed %s",
          constraints.approvalConstraints().maximumNumberOfPeersToNotify()));
    }
  }

  //---------------------------------------------------------------------------
  // Catalog.
  //---------------------------------------------------------------------------

  @Override
  public @NotNull UserContext createContext(
    @NotNull UserId user
  ) throws AccessException, IOException {
    //
    // Lookup the user and their principals.
    //
    return this.userResolver.lookup(user);
  }

  @Override
  public SortedSet<OrganizationId> listScopes(
    @NotNull UserContext userContext
  ) {
    return new TreeSet<>(List.of(CURRENT_ORGANIZATION));
  }

  @Override
  public void verifyUserCanRequest(
    @NotNull UserContext userContext,
    @NotNull ActivationRequest<JitRole> request
  ) throws AccessException, IOException {
    assert userContext.user().equals(request.requestingUser());
    Preconditions.checkArgument(
      request.entitlements().size() == 1,
      "Requests must contain exactly one entitlement");

    //
    // Lookup policy and check request.
    //
    var entitlementId = request.entitlements().stream().findFirst().get();
    var policy = lookupEntitlementPolicy(entitlementId);

    verifyRequestMeetsConstraints(request, policy.constraints());

    //
    // If this is a JIT request, the user is trying to
    // request and approve at once. So we have to check for
    // both rights.
    //
    // If this is an MPA request, the user only needs to be
    // allowed to request.
    //
    final var requiredAccess = request instanceof JitActivationRequest<JitRole>
      ? Policy.RoleAccessRights.REQUEST_WITH_SELF_APPROVAL
      : Policy.RoleAccessRights.REQUEST;

    if (!policy.acl().isAllowed(userContext, requiredAccess)) {
      throw new AccessDeniedException(
        String.format(
          "The user %s is not authorized to request entitlement '%s'",
          userContext.user(),
          entitlementId.id()));
    }
  }

  @Override
  public void verifyUserCanApprove(
    @NotNull UserContext userContext,
    @NotNull MpaActivationRequest<JitRole> request
  ) throws AccessException, IOException {

    Preconditions.checkArgument(
      request.entitlements().size() == 1,
      "Requests must contain exactly one entitlement");

    //
    // Lookup policy and check request.
    //
    var entitlementId = request.entitlements().stream().findFirst().get();
    var policy = lookupEntitlementPolicy(entitlementId);

    verifyRequestMeetsConstraints(request, policy.constraints());

    //
    // Check if the approving user (!) is allowed to approve this
    // request.
    //

    if (!policy.acl().isAllowed(userContext, Policy.RoleAccessRights.APPROVE_OTHERS)) {
      throw new AccessDeniedException(
        String.format(
          "The user %s is not authorized to approve requests for entitlement '%s'",
          userContext.user(),
          entitlementId.id()));
    }
  }

  @Override
  public SortedSet<UserId> listReviewers(
    @NotNull UserContext userContext,
    @NotNull JitRole entitlementId
  ) throws AccessException, IOException {

    //
    // Check if the user is allowed to activate this entitlement,
    // and isn't just trying to do enumeration.
    //
    // NB. It doesn't matter whether the user has already
    // activated the entitlement.
    //
    var policy = lookupEntitlementPolicy(entitlementId);
    if (!policy.acl().isAllowed(userContext, Policy.RoleAccessRights.REQUEST)) {
      throw new AccessDeniedException(
        String.format(
          "The user %s is not authorized to activate entitlement '%s'",
          userContext.user(),
          entitlementId.id()));
    }

    //
    // Find principals that can approve requests for this policy.
    //
    var principalsThatCanApprove = policy
      .acl()
      .allowedPrincipals(Policy.RoleAccessRights.APPROVE_OTHERS);

    //
    // The list might contain groups. Expand these.
    //
    var expandedPrincipalsThatCanApprove = this.groupExpander.expand(principalsThatCanApprove);

    //
    // Remove any remaining groups (indirect groups) and exclude
    // the user themselves.
    //
    return expandedPrincipalsThatCanApprove
      .stream()
      .filter(p -> p instanceof UserId)
      .map(p -> (UserId)p)
      .filter(u -> !u.equals(userContext.user())) // Exclude requesting user
      .collect(Collectors.toCollection(TreeSet::new));
  }

  @Override
  public EntitlementSet<JitRole> listEntitlements(
    @NotNull UserContext userContext,
    @NotNull OrganizationId scope
  ) throws IOException {
    Preconditions.checkArgument(CURRENT_ORGANIZATION.equals(scope));

    //
    // Find entitlements that list this user (or one of its groups)
    // has REQUEST access to.
    //
    final int requiredAccess = Policy.RoleAccessRights.REQUEST;

    var availableEntitlements = new TreeSet<Entitlement<JitRole>>();
    var currentActivations = new HashMap<JitRole, Activation>();

    for (var policyEntitlement : this.policySet
      .policies()
      .stream()
      .flatMap(p -> p.roles().stream())
      .filter(e -> e.acl().isAllowed(userContext, requiredAccess))
      .toList()) {

      //
      // If the user can also self-approve, then we can use the
      // JIT activation type. Otherwise, it's MPA.
      //
      var activationType = policyEntitlement.acl().isAllowed(
        userContext,
        Policy.RoleAccessRights.APPROVE_SELF)
        ? ActivationType.JIT
        : ActivationType.MPA;

      availableEntitlements.add(new Entitlement<>(
        policyEntitlement.id(),
        policyEntitlement.name(), //TODO: include policy name
        activationType));

      //
      // Check if the user is already a member of that group.
      // If so, they must have activated the entitlement already.
      //
      var existingMembershipOfBackingGroup = userContext
        .activeRoles()
        .stream()
        .filter(m -> m.role().equals(policyEntitlement.id()))
        .findFirst();

      if (existingMembershipOfBackingGroup.isPresent()) {
        currentActivations.put(
          policyEntitlement.id(),
          new Activation(existingMembershipOfBackingGroup.get().validity()));
      }
    }

    //
    // Find entitlements that are active, but aren't covered
    // by any policy. This can happen if a policy has been modified.
    //
    for (var activeAndOrphanedEntitlement : userContext
      .activeRoles()
      .stream()
      .filter(a -> availableEntitlements.stream().noneMatch(e -> a.role().equals(e.id())))
      .toList()) {

      currentActivations.put(
        activeAndOrphanedEntitlement.role(),
        new Activation(activeAndOrphanedEntitlement.validity()));
    }

    return new EntitlementSet<>(availableEntitlements, currentActivations, Map.of(), Set.of());
  }
}
