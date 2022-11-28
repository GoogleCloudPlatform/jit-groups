package com.google.solutions.jitaccess.core.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestRoleBinding {

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toStringReturnsResourceAndRole() {
    var roleBinding = new RoleBinding("//project", "role/sample");
    assertEquals("//project:role/sample", roleBinding.toString());
  }

  // -------------------------------------------------------------------------
  // equals.
  // -------------------------------------------------------------------------

  @Test
  public void whenValueIsEquivalent_ThenEqualsReturnsTrue() {
    var ref1 = new RoleBinding(
      "//full-name",
      "roles/test");
    var ref2 = new RoleBinding(
      "//full-name",
      "roles/test");

    assertTrue(ref1.equals(ref2));
    assertTrue(ref1.equals((Object) ref2));
    assertEquals(ref1.hashCode(), ref2.hashCode());
    assertEquals(ref1.toString(), ref2.toString());
  }

  @Test
  public void whenObjectsAreSame_ThenEqualsReturnsTrue() {
    var ref1 = new RoleBinding(
      "//full-name",
      "roles/test");
    var ref2 = ref1;

    assertTrue(ref1.equals(ref2));
    assertTrue(ref1.equals((Object) ref2));
    assertEquals(ref1.hashCode(), ref2.hashCode());
  }

  @Test
  public void whenRolesDiffer_ThenEqualsReturnsFalse() {
    var ref1 = new RoleBinding(
      "//full-name",
      "roles/test");
    var ref2 = new RoleBinding(
      "//full-name",
      "roles/other");

    assertFalse(ref1.equals(ref2));
    assertFalse(ref1.equals((Object) ref2));
  }

  @Test
  public void whenResourcesDiffer_ThenEqualsReturnsFalse() {
    var ref1 = new RoleBinding(
      "//one",
      "roles/test");
    var ref2 = new RoleBinding(
      "//two",
      "roles/test");

    assertFalse(ref1.equals(ref2));
    assertFalse(ref1.equals((Object) ref2));
  }

  @Test
  public void equalsNullIsFalse() {
    var ref1 = new RoleBinding(
      "//full-name",
      "roles/test");

    assertFalse(ref1.equals(null));
  }
}
