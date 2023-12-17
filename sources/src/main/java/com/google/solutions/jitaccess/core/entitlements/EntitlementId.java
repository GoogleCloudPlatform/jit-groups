package com.google.solutions.jitaccess.core.entitlements;

import com.google.common.base.Preconditions;

import java.util.Comparator;

/**
 * Unique identifier of an entitlement.
 *
 * @param catalog the catalog the entitlement belongs to
 * @param id the ID within the catalog
 */
public record EntitlementId(
  String catalog,
  String id
) implements Comparable<EntitlementId> {
  public EntitlementId {
    Preconditions.checkNotNull(catalog, "catalog");
    Preconditions.checkNotNull(id, "id");
  }

  @Override
  public String toString() {
    return String.format("%s:%s", this.catalog, this.id);
  }

  @Override
  public int compareTo(EntitlementId o) {
    return Comparator
      .comparing((EntitlementId e) -> e.catalog)
      .thenComparing(e -> e.id)
      .compare(this, o);
  }
}
