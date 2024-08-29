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

import com.google.common.util.concurrent.UncheckedExecutionException;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Utility methods for using CompletableFutures.
 */
public abstract class CompletableFutures {
  /**
   * Return a Returns a new CompletableFuture, similar to
   * CompletableFuture.supplyAsync, but accepts a Callable.
   *
   * If the Callable throws an exception, it's wrapped in a
   * UncheckedExecutionException.
   */
  public static @NotNull <T> CompletableFuture<T> supplyAsync(
    @NotNull Callable<T> callable
  ) {
    return CompletableFuture.supplyAsync(
      () -> {
        try {
          return callable.call();
        }
        catch (RuntimeException e) {
          throw (RuntimeException)e.fillInStackTrace();
        }
        catch (Exception e) {
          throw new UncheckedExecutionException(e);
        }
      });
  }

  /**
   * Return a Returns a new CompletableFuture, similar to
   * CompletableFuture.supplyAsync, but accepts a Callable.
   *
   * If the Callable throws an exception, it's wrapped in a
   * UncheckedExecutionException.
   */
  public static @NotNull <T> CompletableFuture<T> supplyAsync(
    @NotNull Callable<T> callable,
    @NotNull Executor executor
    ) {
    return CompletableFuture.supplyAsync(
      () -> {
        try {
          return callable.call();
        }
        catch (RuntimeException e) {
          throw (RuntimeException)e.fillInStackTrace();
        }
        catch (Exception e) {
          throw new UncheckedExecutionException(e);
        }
      },
      executor);
  }
}
