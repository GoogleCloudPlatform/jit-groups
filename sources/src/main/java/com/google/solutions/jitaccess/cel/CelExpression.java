package com.google.solutions.jitaccess.cel;

import dev.cel.common.CelException;
import dev.cel.runtime.CelEvaluationException;

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
