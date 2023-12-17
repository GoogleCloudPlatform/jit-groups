package com.google.solutions.jitaccess.core.activation;

import com.google.solutions.jitaccess.core.AccessException;

/**
 * A catalog of entitlement that can be browsed by the user.
 */
public interface EntitlementCatalog<TEntitlementId extends EntitlementId> {
  /**
   * Check if a user is allowed to perform an activation request.
   */
  void verifyAccess(
    ActivationRequest<TEntitlementId> request
  ) throws AccessException;
}
