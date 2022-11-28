package com.google.solutions.jitaccess.core.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestProjectRole {
  private final String SAMPLE_PROJECT_FULLRESOURCENAME =
    "//cloudresourcemanager.googleapis.com/projects/project-1";

  // -------------------------------------------------------------------------
  // Constructor.
  // -------------------------------------------------------------------------

  @Test
  public void whenResourceIsNotAProject_ThenConstructorThrowsException() throws  Exception {
    assertThrows(
      IllegalArgumentException.class,
      () -> new ProjectRole(
        new RoleBinding("//cloudresourcemanager.googleapis.com/folders/folder-1", "role/sample"),
        ProjectRole.Status.ELIGIBLE_FOR_JIT));

  }

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toStringReturnsId() {
    var role = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);
    assertEquals(
      "//cloudresourcemanager.googleapis.com/projects/project-1:role/sample (ELIGIBLE_FOR_JIT)",
      role.toString());
  }

  // -------------------------------------------------------------------------
  // getProjectId.
  // -------------------------------------------------------------------------

  @Test
  public void getProjectIdReturnsUnqualifiedProjectId() {
    var role = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);
    assertEquals(new ProjectId("project-1"), role.getProjectId());
  }

  // -------------------------------------------------------------------------
  // equals.
  // -------------------------------------------------------------------------

  @Test
  public void whenValueIsEquivalent_ThenEqualsReturnsTrue() {
    var role1 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);
    var role2 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);

    assertTrue(role1.equals(role2));
    assertTrue(role1.equals((Object) role2));
    assertEquals(role1.hashCode(), role2.hashCode());
    assertEquals(role1.toString(), role2.toString());
  }

  @Test
  public void whenObjectsAreSame_ThenEqualsReturnsTrue() {
    var role1 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);
    var role2 = role1;

    assertTrue(role1.equals(role2));
    assertTrue(role1.equals((Object) role2));
    assertEquals(role1.hashCode(), role2.hashCode());
  }

  @Test
  public void whenRolesDiffer_ThenEqualsReturnsFalse() {
    var role1 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/one"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);
    var role2 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/two"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);

    assertFalse(role1.equals(role2));
    assertFalse(role1.equals((Object) role2));
  }

  @Test
  public void whenStatusesDiffer_ThenEqualsReturnsFalse() {
    var role1 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);
    var role2 = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ACTIVATED);

    assertFalse(role1.equals(role2));
    assertFalse(role1.equals((Object) role2));
  }

  @Test
  public void equalsNullIsFalse() {
    var role = new ProjectRole(
      new RoleBinding(SAMPLE_PROJECT_FULLRESOURCENAME, "role/sample"),
      ProjectRole.Status.ELIGIBLE_FOR_JIT);

    assertFalse(role.equals(null));
  }
}