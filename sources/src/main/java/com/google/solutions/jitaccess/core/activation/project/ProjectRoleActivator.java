
package com.google.solutions.jitaccess.core.activation.project;

import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.AlreadyExistsException;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.activation.*;
import com.google.solutions.jitaccess.core.clients.IamTemporaryAccessConditions;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.core.entitlements.JitConstraints;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Activator for project roles.
 */
@ApplicationScoped
public class ProjectRoleActivator extends EntitlementActivator<ProjectRoleId> {
  private final ResourceManagerClient resourceManagerClient;

  public ProjectRoleActivator(
    EntitlementCatalog<ProjectRoleId> catalog,
    ResourceManagerClient resourceManagerClient,
    JustificationPolicy policy
  ) {
    super(catalog, policy);

    Preconditions.checkNotNull(resourceManagerClient, "resourceManagerClient");

    this.resourceManagerClient = resourceManagerClient;
  }

  private void provisionTemporaryBinding(
    String bindingDescription,
    ProjectId projectId,
    UserId user,
    Set<String> roles,
    Instant startTime,
    Duration duration
  ) throws AccessException, AlreadyExistsException, IOException {

    //
    // Add time-bound IAM binding.
    //
    // Replace existing bindings for same user and role to avoid
    // accumulating junk, and to prevent hitting the binding limit.
    //

    for (var role : roles) {
      var binding = new Binding()
        .setMembers(List.of("user:" + user))
        .setRole(role)
        .setCondition(new com.google.api.services.cloudresourcemanager.v3.model.Expr()
          .setTitle(JitConstraints.ACTIVATION_CONDITION_TITLE)
          .setDescription(bindingDescription)
          .setExpression(IamTemporaryAccessConditions.createExpression(startTime, duration)));

      //TODO(later): Add bindings in a single request.

      this.resourceManagerClient.addProjectIamBinding(
        projectId,
        binding,
        EnumSet.of(ResourceManagerClient.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS),
        bindingDescription);
    }
  }

  // -------------------------------------------------------------------------
  // Overrides.
  // -------------------------------------------------------------------------

  @Override
  protected void provisionAccess(//TODO:test
    JitActivationRequest<ProjectRoleId> request
  ) throws AccessException, AlreadyExistsException, IOException {

    Preconditions.checkNotNull(request, "request");

    var bindingDescription = String.format(
      "Self-approved, justification: %s",
      request.justification());

    provisionTemporaryBinding(
      bindingDescription,
      ProjectActivationRequest.projectId(request),
      request.requestingUser(),
      request.entitlements()
        .stream()
        .map(e -> e.roleBinding().role())
        .collect(Collectors.toSet()),
      request.startTime(),
      request.duration());
  }

  @Override
  protected void provisionAccess(//TODO:test
    UserId approvingUser,
    MpaActivationRequest<ProjectRoleId> request
  ) throws AccessException, AlreadyExistsException, IOException {

    Preconditions.checkNotNull(request, "request");

    var bindingDescription = String.format(
      "Approved by %s, justification: %s",
      approvingUser.email,
      request.justification());

    //
    // NB. The start/end time for the binding is derived from the approval token. If multiple
    // reviewers try to approve the same token, the resulting condition (and binding) will
    // be the same. This is important so that we can use the FAIL_IF_BINDING_EXISTS flag.
    //

    provisionTemporaryBinding(
      bindingDescription,
      ProjectActivationRequest.projectId(request),
      request.requestingUser(),
      request.entitlements()
        .stream()
        .map(e -> e.roleBinding().role())
        .collect(Collectors.toSet()),
      request.startTime(),
      request.duration());
  }
}
