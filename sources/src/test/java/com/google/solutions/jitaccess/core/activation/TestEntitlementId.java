package com.google.solutions.jitaccess.core.activation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

public class TestEntitlementId {
  // -------------------------------------------------------------------------
  // hashCode.
  // -------------------------------------------------------------------------

  @Test
  public void whenIdIsEqual_ThenHashCodeIsEqual() {
    assertEquals(
      new SampleEntitlementId("cat", "1").hashCode(),
      new SampleEntitlementId("dog", "1").hashCode()
    );
  }

  // -------------------------------------------------------------------------
  // equals.
  // -------------------------------------------------------------------------

  @Test
  public void whenObjectAreEquivalent_ThenEqualsReturnsTrue() {
    SampleEntitlementId id1 = new SampleEntitlementId("cat", "jit-1");
    SampleEntitlementId id2 = new SampleEntitlementId("cat", "jit-1");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectAreSame_ThenEqualsReturnsTrue() {
    SampleEntitlementId id1 = new SampleEntitlementId("cat", "jit-1");

    assertTrue(id1.equals(id1));
  }

  @Test
  public void whenObjectAreNotEquivalent_ThenEqualsReturnsFalse() {
    SampleEntitlementId id1 = new SampleEntitlementId("cat", "jit-1");
    SampleEntitlementId id2 = new SampleEntitlementId("cat", "jit-2");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectIsNull_ThenEqualsReturnsFalse() {
    SampleEntitlementId id1 = new SampleEntitlementId("cat", "jit-1");

    assertFalse(id1.equals(null));
  }

  @Test
  public void whenObjectIsDifferentType_ThenEqualsReturnsFalse() {
    SampleEntitlementId id1 = new SampleEntitlementId("cat", "jit-1");

    assertFalse(id1.equals(""));
  }

  // -------------------------------------------------------------------------
  // compareTo.
  // -------------------------------------------------------------------------

  @Test
  public void compareToOrdersByCatalogThenId() {
    var ids = List.of(
      new SampleEntitlementId("b", "2"),
      new SampleEntitlementId("b", "1"),
      new SampleEntitlementId("a", "2"),
      new SampleEntitlementId("b", "3"),
      new SampleEntitlementId("a", "1"));

    var sorted = new TreeSet<SampleEntitlementId>();
    sorted.addAll(ids);

    Assertions.assertIterableEquals(
      List.of(
        new SampleEntitlementId("a", "1"),
        new SampleEntitlementId("a", "2"),
        new SampleEntitlementId("b", "1"),
        new SampleEntitlementId("b", "2"),
        new SampleEntitlementId("b", "3")),
      sorted);
  }
}
