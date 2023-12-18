package com.google.solutions.jitaccess.core.activation;

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.UserId;

import java.util.Collection;

/**
 * A catalog of entitlement that can be browsed by the user.
 */
public interface EntitlementCatalog<TEntitlementId extends EntitlementId> {
  /**
   * Verify that a user is allowed to request the given set of entitlements.
   */
  void canRequest(
    UserId requestingUser,
    Collection<TEntitlementId> entitlements
  ) throws AccessException;

  /**
   * Verify that a user is allowed to approve requests for the given
   * set of entitlements.
   */
  void canApprove(
    UserId approvingUser,
    Collection<TEntitlementId> entitlements
  ) throws AccessException;
}
