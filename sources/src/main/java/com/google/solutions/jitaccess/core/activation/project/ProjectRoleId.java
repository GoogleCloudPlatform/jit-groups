package com.google.solutions.jitaccess.core.activation.project;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.activation.EntitlementId;
import com.google.solutions.jitaccess.core.entitlements.RoleBinding;

public class ProjectRoleId extends EntitlementId {
  static final String CATALOG = "iam";

  private final RoleBinding roleBinding;

  public ProjectRoleId(RoleBinding roleBinding) {
    Preconditions.checkNotNull(roleBinding, "roleBinding");
    this.roleBinding = roleBinding;
  }

  public RoleBinding roleBinding() {
    return this.roleBinding;
  }

  @Override
  public String catalog() {
    return CATALOG;
  }

  @Override
  public String id() {
    return this.roleBinding.toString();
  }

  public ProjectId projectId() {
    return ProjectId.fromFullResourceName(this.roleBinding.fullResourceName());
  }
}
