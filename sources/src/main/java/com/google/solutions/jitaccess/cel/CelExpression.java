package com.google.solutions.jitaccess.cel;

import dev.cel.common.CelException;

/**
 * A CEL expression.
 */
public interface CelExpression<TResult> {
  /**
   * Evaluate the expression.
   * @return result
   */
  TResult evaluate() throws CelException;
}
