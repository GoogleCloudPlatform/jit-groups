package com.google.solutions.jitaccess.core.data;

import com.google.common.base.Preconditions;

import java.util.Objects;

public class ProjectId {
  private static final String PROJECT_RESOURCE_NAME_PREFIX = "//cloudresourcemanager.googleapis.com/projects/";

  public final String id;

  public ProjectId(String id) {
    Preconditions.checkNotNull(id, "id");
    assert !id.startsWith("//");

    this.id = id;
  }

  @Override
  public String toString() {
    return this.id;
  }


  // -------------------------------------------------------------------------
  // Full resource name conversion.
  // -------------------------------------------------------------------------

  public String getFullResourceName() {
    return PROJECT_RESOURCE_NAME_PREFIX + this.id;
  }

  public static ProjectId fromFullResourceName(String fullResourceName) {
    return new ProjectId(fullResourceName.substring(PROJECT_RESOURCE_NAME_PREFIX.length()));
  }

  /**
   * Check if a full resource name identifies a project and can be used for
   * a ProjectRole.
   */
  public static boolean isProjectFullResourceName(String fullResourceName) {
    return fullResourceName.startsWith(PROJECT_RESOURCE_NAME_PREFIX)
      && fullResourceName.indexOf('/', PROJECT_RESOURCE_NAME_PREFIX.length()) == -1;
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ProjectId projectId = (ProjectId) o;
    return this.id.equals(projectId.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.id);
  }
}
