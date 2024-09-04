package com.google.solutions.jitaccess.catalog.auth;

import com.google.solutions.jitaccess.apis.ProjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestServiceAccountId {
  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsEmailInLowerCase() {
    assertEquals(
      "serviceAccount:test@project-1.iam.gserviceaccount.com",
      new ServiceAccountId("test", new ProjectId("project-1")).toString());
    assertEquals(
      "serviceAccount:test@project-1.iam.gserviceaccount.com",
      new ServiceAccountId("Test", new ProjectId("project-1")).toString());
  }

  // -------------------------------------------------------------------------
  // projectId.
  // -------------------------------------------------------------------------

  @Test
  public void projectId() {
    assertEquals(
      new ProjectId("project-1"),
      new ServiceAccountId("test", new ProjectId("project-1")).projectId);
  }

  // -------------------------------------------------------------------------
  // email.
  // -------------------------------------------------------------------------

  @Test
  public void email() {
    assertEquals(
      "test@project-1.iam.gserviceaccount.com",
      new ServiceAccountId("test", new ProjectId("project-1")).email());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void equals_whenObjectAreEquivalent() {
    var id1 = new ServiceAccountId("test", new ProjectId("project-1"));
    var id2 = new ServiceAccountId("test", new ProjectId("project-1"));

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectAreEquivalentButDifferInCasing() {
    var id1 = new ServiceAccountId("test", new ProjectId("project-1"));
    var id2 = new ServiceAccountId("TEST", new ProjectId("project-1"));

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectAreSame() {
    var id1 = new ServiceAccountId("test", new ProjectId("project-1"));

    assertTrue(id1.equals(id1));
    assertEquals(0, id1.compareTo(id1));
  }

  @Test
  public void equals_whenIdsDiffer() {
    var id1 = new ServiceAccountId("one", new ProjectId("project-1"));
    var id2 = new ServiceAccountId("two", new ProjectId("project-1"));

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
    assertNotEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenProjectIdsDiffer() {
    var id1 = new ServiceAccountId("test", new ProjectId("project-1"));
    var id2 = new ServiceAccountId("test", new ProjectId("project-2"));

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
    assertNotEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectIsNull() {
    var id1 = new ServiceAccountId("test", new ProjectId("project-1"));

    assertFalse(id1.equals(null));
  }

  @Test
  public void equals_whenObjectIsDifferentType() {
    var id1 = new ServiceAccountId("test", new ProjectId("project-1"));

    assertFalse(id1.equals(""));
  }

  // -------------------------------------------------------------------------
  // PrincipalId.
  // -------------------------------------------------------------------------

  @Test
  public void value() {
    assertEquals(
      "test@project-1.iam.gserviceaccount.com",
      new ServiceAccountId("test", new ProjectId("project-1")).value());
  }

  @Test
  public void iamPrincipalId() {
    assertInstanceOf(IamPrincipalId.class, new ServiceAccountId("test", new ProjectId("project-1")));
  }

  // -------------------------------------------------------------------------
  // parse.
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    "user",
    "user:",
    "invalid"
  })
  public void parse_whenInvalid(String s) {
    assertFalse(ServiceAccountId.parse(null).isPresent());
    assertFalse(ServiceAccountId.parse(s).isPresent());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "serviceaccount:test@project-1.iam.gserviceaccount.com",
    "  serviceaccount:test@project-1.iam.gserviceaccount.com ",
    "ServiceAccount:test@project-1.IAM.GSERVICEACCOUNT.COM",
    "  test@project-1.IAM.GSERVICEACCOUNT.COM "
  })
  public void parse(String id) {
    assertEquals(
      new ServiceAccountId("test", new ProjectId("project-1")),
      ServiceAccountId.parse(id).get());
  }
}
