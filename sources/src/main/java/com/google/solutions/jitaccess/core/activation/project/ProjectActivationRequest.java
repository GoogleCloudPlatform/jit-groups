package com.google.solutions.jitaccess.core.activation.project;

import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.activation.ActivationRequest;

import java.util.stream.Collectors;

class ProjectActivationRequest {
  private ProjectActivationRequest() {
  }

  /**
   * @return common project ID for all requested entitlements.
   */
  static ProjectId projectId(ActivationRequest<ProjectRoleId> request) { // TODO: test
    var projects = request.entitlements().stream()
      .map(e -> e.roleBinding().fullResourceName())
      .collect(Collectors.toSet());

    if (projects.size() != 1) {
      throw new IllegalArgumentException("Entitlements must be part of the same project");
    }

    return ProjectId.fromFullResourceName(projects.stream().findFirst().get());
  }
}
