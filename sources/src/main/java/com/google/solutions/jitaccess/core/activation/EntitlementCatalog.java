package com.google.solutions.jitaccess.core.activation;

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.UserId;

import java.io.IOException;

/**
 * A catalog of entitlement that can be browsed by the user.
 */
public interface EntitlementCatalog<TEntitlementId extends EntitlementId> {
  /**
   * Verify if a user is allowed to make the given request.
   */
  void canRequest(
    ActivationRequest<TEntitlementId> request
  ) throws AccessException, IOException;

  /**
   * Verify if a user is allowed to approve a given request.
   */
  void canApprove(
    UserId approvingUser,
    MpaActivationRequest<TEntitlementId> request
  ) throws AccessException, IOException;
}
