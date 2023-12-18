package com.google.solutions.jitaccess.core.activation;

public enum ActivationType {
  /** Entitlement can be activated using self-approval */
  JIT,

  /** Entitlement can be activated using multi-party approval.  */
  MPA,

  /** Entitlement can no longer be activated. */
  NONE
}
