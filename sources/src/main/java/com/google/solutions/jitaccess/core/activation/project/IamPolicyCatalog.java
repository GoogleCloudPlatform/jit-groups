package com.google.solutions.jitaccess.core.activation.project;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.activation.ActivationRequest;
import com.google.solutions.jitaccess.core.activation.ActivationType;
import com.google.solutions.jitaccess.core.activation.Entitlement;
import com.google.solutions.jitaccess.core.activation.MpaActivationRequest;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Catalog that uses the Policy Analyzer API to find entitlements
 * based on IAM Allow-policies.
 *
 * Entitlements as used by this class are role bindings that:
 * are annotated with a special IAM condition (making the binding
 * "eligible").
 */
@ApplicationScoped
public class IamPolicyCatalog extends ProjectRoleCatalog {
  private final PolicyAnalyzer policyAnalyzer;
  private final ResourceManagerClient resourceManagerClient;
  private final Options options;

  public IamPolicyCatalog(
    PolicyAnalyzer policyAnalyzer,
    ResourceManagerClient resourceManagerClient,
    Options options
  ) {
    Preconditions.checkNotNull(policyAnalyzer, "assetInventoryClient");
    Preconditions.checkNotNull(resourceManagerClient, "resourceManagerClient");
    Preconditions.checkNotNull(options, "options");

    this.policyAnalyzer = policyAnalyzer;
    this.resourceManagerClient = resourceManagerClient;
    this.options = options;
  }

  private void validateRequest(ActivationRequest<ProjectRoleId> request) { // TODO: test
    Preconditions.checkNotNull(request, "request");
    Preconditions.checkArgument(
      request.duration().toSeconds() >= this.options.minActivationDuration().toSeconds(),
      String.format(
        "The activation duration must be no shorter than %s",
        this.options.minActivationDuration()));
    Preconditions.checkArgument(
      request.duration().toSeconds() <= this.options.maxActivationDuration().toSeconds(),
      String.format(
        "The activation duration must be no longer than %s",
        this.options.maxActivationDuration));

    if (request instanceof MpaActivationRequest<ProjectRoleId> mpaRequest) {
      Preconditions.checkArgument(
        mpaRequest.reviewers() != null &&
          mpaRequest.reviewers().size() >= this.options.minNumberOfReviewersPerActivationRequest,
        String.format(
          "At least %d reviewers must be specified",
          this.options.minNumberOfReviewersPerActivationRequest ));
      Preconditions.checkArgument(
        mpaRequest.reviewers().size() <= this.options.maxNumberOfReviewersPerActivationRequest,
        String.format(
          "The number of reviewers must not exceed %s",
          this.options.maxNumberOfReviewersPerActivationRequest));
    }
  }

  private void verifyUserCanActivateEntitlements( // TODO: test
    UserId user,
    ProjectId projectId,
    ActivationType activationType,
    Collection<ProjectRoleId> entitlements
  ) throws AccessException, IOException {
    //
    // Verify that the user has eligible role bindings
    // for all entitlements.
    //
    // NB. It doesn't matter whether the user has already
    // activated the role.
    //
    var userEntitlements = this.policyAnalyzer
      .listEligibleProjectRoles(
        user,
        projectId,
        EnumSet.of(Entitlement.Status.AVAILABLE))
      .items()
      .stream()
      .filter(ent -> ent.activationType() == activationType)
      .collect(Collectors.toMap(ent -> ent.id(), ent -> ent));

    for (var requestedEntitlement : entitlements) {
      var grantedEntitlement = userEntitlements.get(requestedEntitlement);
      if (grantedEntitlement == null) {
        throw new AccessDeniedException(
          String.format(
            "The user %s is not allowed to activate %s using %s",
            user,
            requestedEntitlement.id(),
            activationType));
      }
    }
  }

  //---------------------------------------------------------------------------
  // Overrides.
  //---------------------------------------------------------------------------

  @Override
  public SortedSet<ProjectId> listProjects(
    UserId user
  ) throws AccessException, IOException {
    if (Strings.isNullOrEmpty(this.options.availableProjectsQuery)) { // TODO: test
      //
      // Find projects for which the user has any role bindings (eligible
      // or regular bindings). This method is slow, but accurate.
      //
      return this.policyAnalyzer.findProjectsWithRoleBindings(user);
    }
    else {
      //
      // List all projects that the application's service account
      // can enumerate. This method is fast, but almost certainly
      // returns some projects that the user doesn't have any
      // entitlements for. Depending on the nature of the projects,
      // this might be acceptable or considered information disclosure.
      //
      return this.resourceManagerClient.searchProjectIds(
        this.options.availableProjectsQuery);
    }
  }

  @Override
  public Annotated<SortedSet<Entitlement<ProjectRoleId>>> listEntitlements( //TODO: test, order
    UserId user,
    ProjectId projectId
  ) throws AccessException, IOException {
    return this.policyAnalyzer.listEligibleProjectRoles(
      user,
      projectId,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));
  }

  @Override
  public SortedSet<UserId> listReviewers( //TODO: test, order
    UserId requestingUser,
    ProjectRoleId entitlement
  ) throws AccessException, IOException {
    return this.policyAnalyzer
      .listEligibleUsersForProjectRole(
        entitlement.roleBinding(),
        ActivationType.MPA)
      .stream()
      .filter(u -> !u.equals(requestingUser)) // Exclude requesting user
      .collect(Collectors.toCollection(TreeSet::new));
  }

  @Override
  public void canRequest(
    ActivationRequest<ProjectRoleId> request
  ) throws AccessException, IOException { // TODO: test

    validateRequest(request);

    //
    // Check if the requesting user is allowed to activate this
    // entitlement.
    //
    verifyUserCanActivateEntitlements(
      request.requestingUser(),
      ProjectActivationRequest.projectId(request),
      request.type(),
      request.entitlements());
  }

  @Override
  public void canApprove(
    UserId approvingUser,
    MpaActivationRequest<ProjectRoleId> request
  ) throws AccessException, IOException { // TODO: test

    validateRequest(request);

    //
    // Check if the approving user (!) is allowed to activate this
    // entitlement.
    //
    // NB. The base class already checked that the requesting user
    // is allowed.
    //
    verifyUserCanActivateEntitlements(
      approvingUser,
      ProjectActivationRequest.projectId(request),
      request.type(),
      request.entitlements());
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  /**
   * If a query is provided, the class performs a Resource Manager project
   * search instead of Policy Analyzer query to list projects. This is faster,
   * but results in non-personalized results.
   *
   * @param scope organization/ID, folder/ID, or project/ID.
   * @param availableProjectsQuery optional, search query, for example:
   *      - parent:folders/{folder_id}
   * @param maxActivationDuration maximum duration for an activation
   */
  public record Options(
    String availableProjectsQuery,
    Duration maxActivationDuration,
    int minNumberOfReviewersPerActivationRequest,
    int maxNumberOfReviewersPerActivationRequest
  ) {
    static final int MIN_ACTIVATION_TIMEOUT_MINUTES = 5;

    public Options {
      Preconditions.checkNotNull(maxActivationDuration, "maxActivationDuration");

      Preconditions.checkArgument(!maxActivationDuration.isNegative());
      Preconditions.checkArgument(
        maxActivationDuration.toMinutes() >= MIN_ACTIVATION_TIMEOUT_MINUTES,
        "Activation timeout must be at least 5 minutes");
      Preconditions.checkArgument(
        minNumberOfReviewersPerActivationRequest > 0,
        "The minimum number of reviewers cannot be 0");
      Preconditions.checkArgument(
        minNumberOfReviewersPerActivationRequest <= maxNumberOfReviewersPerActivationRequest,
        "The minimum number of reviewers must not exceed the maximum");
    }

    public Duration minActivationDuration() {
      return Duration.ofMinutes(MIN_ACTIVATION_TIMEOUT_MINUTES);
    }
  }
}
