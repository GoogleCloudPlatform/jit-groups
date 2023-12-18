package com.google.solutions.jitaccess.core.activation.project;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AnnotatedResult;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.activation.ActivationRequest;
import com.google.solutions.jitaccess.core.activation.ActivationType;
import com.google.solutions.jitaccess.core.activation.Entitlement;
import com.google.solutions.jitaccess.core.activation.MpaActivationRequest;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Policy Analyzer-based catalog for project-level role bindings that
 * have been granted in a discretionary way by a project owner or admin.
 */
@ApplicationScoped
public class DiscretionaryProjectRoleCatalog extends ProjectRoleCatalog {
  private final PolicyAnalyzer policyAnalyzer;
  private final ResourceManagerClient resourceManagerClient;
  private final Options options;

  public DiscretionaryProjectRoleCatalog(
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


  private void verifyAccess(
    UserId user,
    Collection<ProjectRoleId> entitlements,
    EnumSet<ActivationType> typesToInclude
  ) throws AccessException {
    throw new RuntimeException("NIY");
  }

  //---------------------------------------------------------------------------
  // Overrides.
  //---------------------------------------------------------------------------

  @Override
  public Collection<ProjectId> listProjects(
    UserId user
  ) throws AccessException, IOException {
    if (Strings.isNullOrEmpty(this.options.availableProjectsQuery)) { //TODO: test
      //
      // Analyze policies to find projects that the user has
      // any entitlements for. This method is slow, but accurate.
      //
      return this.policyAnalyzer.listAvailableProjects(user);
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
  public AnnotatedResult<Entitlement<ProjectRoleId>> listEntitlements( //TODO: test
    UserId user,
    ProjectId projectId
  ) throws AccessException, IOException {
    return this.policyAnalyzer.listEligibleProjectRoles(
      user,
      projectId,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE),
      EnumSet.of(ActivationType.JIT, ActivationType.MPA));
  }

  @Override
  public Set<UserId> listReviewers( //TODO: test
    UserId requestingUser,
    ProjectRoleId entitlement
  ) throws AccessException, IOException {
    return this.policyAnalyzer.listEligibleUsersForProjectRole(
      requestingUser,
      entitlement.roleBinding());
  }

  @Override
  public void canRequest(
    ActivationRequest<ProjectRoleId> request
  ) throws AccessException { // TODO: test

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

    throw new RuntimeException("NIY");
  }

  @Override
  public void canApprove(
    UserId approvingUser,
    MpaActivationRequest<ProjectRoleId> request
  ) throws AccessException {
    throw new RuntimeException("NIY");

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
    String scope,
    String availableProjectsQuery,
    Duration maxActivationDuration,
    int minNumberOfReviewersPerActivationRequest,
    int maxNumberOfReviewersPerActivationRequest
  ) {
    static final int MIN_ACTIVATION_TIMEOUT_MINUTES = 5;

    public Options {
      Preconditions.checkNotNull(scope, "scope");
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
