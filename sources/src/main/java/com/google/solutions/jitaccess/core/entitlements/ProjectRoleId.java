package com.google.solutions.jitaccess.core.entitlements;

import com.google.common.base.Preconditions;

public class ProjectRoleId extends EntitlementId {
  static final String CATALOG = "iam";

  private final RoleBinding roleBinding;

  public ProjectRoleId(RoleBinding roleBinding) {
    Preconditions.checkNotNull(roleBinding, "roleBinding");
    this.roleBinding = roleBinding;
  }

  @Override
  public String catalog() {
    return CATALOG;
  }

  @Override
  public String id() {
    return this.roleBinding.toString();
  }
}
