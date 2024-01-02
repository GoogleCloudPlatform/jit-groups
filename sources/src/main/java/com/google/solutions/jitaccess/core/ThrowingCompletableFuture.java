package com.google.solutions.jitaccess.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Completable future for a supplier that can throw a checked exception.
 */
public class ThrowingCompletableFuture {
  /**
   * Function that can throw a checked exception.
   */
  @FunctionalInterface
  public interface ThrowingSupplier<T> {
    T supply() throws Exception;
  }

  public static <T> CompletableFuture<T> submit(
    ThrowingSupplier<T> supplier,
    Executor executor
  ) {
    var future = new CompletableFuture<T>();
    executor.execute(() -> {
      try {
        future.complete(supplier.supply());
      }
      catch (Exception e) {
        future.completeExceptionally(e);
      }
    });

    return future;
  }
}
