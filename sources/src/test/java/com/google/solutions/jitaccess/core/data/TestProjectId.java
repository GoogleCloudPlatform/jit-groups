package com.google.solutions.jitaccess.core.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestProjectId {
  private final String SAMPLE_PROJECT_FULLRESOURCENAME =
    "//cloudresourcemanager.googleapis.com/projects/project-1";

  @Test
  public void toStringReturnsId() {
    assertEquals("project-1", new ProjectId("project-1").toString());
  }

  // -------------------------------------------------------------------------
  // Full resource name conversion.
  // -------------------------------------------------------------------------

  @Test
  public void getFullResourceNameReturnsFullyQualifiedName() {
    assertEquals(
      "//cloudresourcemanager.googleapis.com/projects/project-1",
      new ProjectId("project-1").getFullResourceName());
  }

  @Test
  public void fromFullResourceNameReturnsProjectId() {
    assertEquals(
      new ProjectId("project-1"),
      ProjectId.fromFullResourceName("//cloudresourcemanager.googleapis.com/projects/project-1"));
  }

  @Test
  public void whenResourceIsProject_TheIsSupportedResourceReturnsTrue() {
    assertTrue(ProjectId.isProjectFullResourceName(SAMPLE_PROJECT_FULLRESOURCENAME));
  }

  @Test
  public void whenResourceIsNotAProject_TheIsSupportedResourceReturnsTrue() {
    assertFalse(ProjectId.isProjectFullResourceName(SAMPLE_PROJECT_FULLRESOURCENAME + "/foo/bar"));
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void whenObjectAreEquivalent_ThenEqualsReturnsTrue() {
    ProjectId id1 = new ProjectId("project-1");
    ProjectId id2 = new ProjectId("project-1");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectAreSame_ThenEqualsReturnsTrue() {
    ProjectId id1 = new ProjectId("project-1");

    assertTrue(id1.equals(id1));
  }

  @Test
  public void whenObjectAreMotEquivalent_ThenEqualsReturnsFalse() {
    ProjectId id1 = new ProjectId("project-1");
    ProjectId id2 = new ProjectId("project-2");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectIsNull_ThenEqualsReturnsFalse() {
    ProjectId id1 = new ProjectId("project-1");

    assertFalse(id1.equals(null));
  }

  @Test
  public void whenObjectIsDifferentType_ThenEqualsReturnsFalse() {
    ProjectId id1 = new ProjectId("project-1");

    assertFalse(id1.equals(""));
  }
}
