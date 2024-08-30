//
// Copyright 2024 Google LLC
//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.google.solutions.jitaccess.util;

import com.google.solutions.jitaccess.apis.clients.AccessException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Utility methods for using CompletableFutures.
 */
public abstract class CompletableFutures {
  /**
   * Returns a new CompletableFuture, similar to
   * CompletableFuture.supplyAsync, but accepts a Callable.
   *
   * Any checked exceptions thrown by the callable are wrapped
   * so that future.get() throws an ExecutionException with the
   * checked exception as cause.
   */
  public static @NotNull <T> CompletableFuture<T> supplyAsync(
    @NotNull Callable<T> callable,
    @NotNull Executor executor
    ) {
    var future = new CompletableFuture<T>();
    executor.execute(() -> {
      try {
        future.complete(callable.call());
      }
      catch (Exception e) {
        future.completeExceptionally(e);
      }
    });

    return future;
  }

  /**
   * Invokes a function for each supplied argument and returns a
   * CompletableFuture.
   *
   * If one of the function throws an exception, the future
   * will throw an AggregateException.
   */
  public static <T, R> CompletableFuture<Collection<R>> applyAsync(
    @NotNull ThrowingFunction<T, R> function,
    @NotNull Iterable<T> arguments,
    @NotNull Executor executor
  ) {
    var futures = new LinkedList<CompletableFuture<R>>();
    for (var argument : arguments) {
      futures.add(supplyAsync(() -> function.apply(argument), executor));
    }

    return supplyAsync(
      () -> {
        var results = new LinkedList<R>();
        var exceptions = new LinkedList<Exception>();
        for (var future : futures) {
          try {
            results.add(future.get());
          }
          catch (Exception e) {
            exceptions.add(Exceptions.unwrap(e));
          }
        }

        if (exceptions.isEmpty()) {
          return results;
        }
        else {
          throw new AggregateException(exceptions);
        }
      },
      executor);
  }

  /**
   * Await a future and rethrow exceptions, unwrapping known exceptions.
   */
  public static <T> T getOrRethrow( // TODO: remove
    @NotNull CompletableFuture<T> future
  ) throws AccessException, IOException {
    try {
      return future.get();
    }
    catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof AccessException) {
        throw (AccessException)e.getCause().fillInStackTrace();
      }

      if (e.getCause() instanceof IOException) {
        throw (IOException)e.getCause().fillInStackTrace();
      }

      throw new IOException("Awaiting executor tasks failed", e);
    }
  }

  /**
   * Function that can throw a checked exception.
   */
  @FunctionalInterface
  public interface ThrowingFunction<T, R> {
    R apply(T t) throws Exception;
  }

}
