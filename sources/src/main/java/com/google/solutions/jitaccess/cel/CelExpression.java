package com.google.solutions.jitaccess.cel;

import dev.cel.common.CelException;
import dev.cel.runtime.CelEvaluationException;

/**
 * A CEL expression.
 */
public interface CelExpression<TResult> {
  /**
   * Evaulate the expression.
   * @return result
   */
  TResult evaluate() throws CelException;
}
