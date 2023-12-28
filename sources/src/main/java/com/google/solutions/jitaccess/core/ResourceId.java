package com.google.solutions.jitaccess.core;

public interface ResourceId {
  /**
   * Type of resource, for example project, folder, organization.
   */
  String type();

  /**
   * Unique ID of the resource, without prefix.
   */
  String id();

  /**
   * Path, in notation type/id.
   *
   * For example, projects/test-123 folders/234, organizations/345.
   */
   String path();
}
