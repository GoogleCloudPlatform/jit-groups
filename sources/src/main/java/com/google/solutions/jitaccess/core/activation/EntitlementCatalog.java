package com.google.solutions.jitaccess.core.activation;

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.UserId;

public abstract class EntitlementCatalog<TEntitlementId extends EntitlementId> {
  /**
   * Check if a user is allowed to perform an activation request.
   */
  public abstract void verifyAccess(
    ActivationRequest<TEntitlementId> request
  ) throws AccessException;
}
